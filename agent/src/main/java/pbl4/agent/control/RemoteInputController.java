package pbl4.agent.control;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Điều khiển chuột / bàn phím của máy Client bằng java.awt.Robot.
 *
 * Dùng cho máy chạy X11 (cùng điều kiện với JavaRobotCapture).
 * Trên Wayland, Robot chỉ tác động được lên cửa sổ XWayland nên Agent
 * không bật điều khiển (xem ScreenSharingService).
 *
 * Quy ước dữ liệu (do trình xem của Admin gửi, Server đã lọc):
 *
 *   x, y   : toạ độ CHUẨN HOÁ 0.0 - 1.0 so với khung hình đang xem
 *            (0,0 = góc trên trái; 1,1 = góc dưới phải). Nhờ vậy không
 *            phụ thuộc việc khung hình đã bị thu nhỏ (max-width).
 *   button : 0 = trái, 1 = giữa, 2 = phải (giống MouseEvent.button của JS)
 *   delta  : số "nấc" cuộn; dương = cuộn xuống
 *   code   : KeyboardEvent.code của JS, ví dụ "KeyA", "Enter", "ShiftLeft"
 *
 * Mọi thao tác Robot chạy trên MỘT luồng riêng (đúng thứ tự, không chặn
 * luồng nhận WebSocket). Sự kiện di chuyển chuột được gộp: chỉ giữ vị trí
 * mới nhất nên mạng chậm cũng không bị "trễ dây chuyền".
 */
public class RemoteInputController implements AutoCloseable {

    /** Bật / tắt toàn bộ tính năng: -Dagent.remote-control.enabled=false */
    public static boolean isEnabledByConfig() {

        String value = System.getProperty("agent.remote-control.enabled");

        if (value == null || value.isBlank()) {
            value = System.getenv("AGENT_REMOTE_CONTROL");
        }

        return value == null
                || value.isBlank()
                || !"false".equalsIgnoreCase(value.trim());
    }

    // JS button -> mặt nạ nút của Robot
    private static final int[] BUTTON_MASKS = {
            InputEvent.BUTTON1_DOWN_MASK,
            InputEvent.BUTTON2_DOWN_MASK,
            InputEvent.BUTTON3_DOWN_MASK
    };

    private static final int MAX_WHEEL_NOTCHES = 20;

    // KeyboardEvent.code -> KeyEvent.VK_*
    private static final Map<String, Integer> KEY_MAP = buildKeyMap();

    private final Robot robot;
    private final Rectangle screenBounds;

    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {

                Thread t = new Thread(r, "remote-input");
                t.setDaemon(true);

                return t;
            });

    // Vị trí chuột mới nhất chưa được áp dụng (gộp các MOUSE_MOVE)
    private final AtomicReference<double[]> pendingMove =
            new AtomicReference<>();

    // Chỉ dùng trên luồng "remote-input"
    private final Set<Integer> pressedKeys = new HashSet<>();
    private final Set<Integer> pressedButtons = new HashSet<>();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RemoteInputController() throws IOException {

        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException(
                    "Java đang chạy ở chế độ headless nên không điều khiển "
                            + "được chuột/bàn phím.");
        }

        try {
            robot = new Robot();

            // Phải trùng với vùng mà JavaRobotCapture thu
            screenBounds = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds();

            // Tự gọi waitForIdle sau mỗi sự kiện sẽ làm chậm đáng kể
            robot.setAutoWaitForIdle(false);
            robot.setAutoDelay(0);

        } catch (Throwable e) {
            throw new IOException(
                    "Không khởi tạo được Java Robot để điều khiển: "
                            + e.getMessage(), e);
        }
    }

    /**
     * Nhận một sự kiện điều khiển. Trả về ngay, việc thực thi chạy nền.
     *
     * @param event MOUSE_MOVE | MOUSE_DOWN | MOUSE_UP | MOUSE_WHEEL
     *              | KEY_DOWN | KEY_UP | RELEASE_ALL
     */
    public void handle(
            String event,
            double x,
            double y,
            int button,
            int delta,
            String code) {

        if (closed.get() || event == null) {
            return;
        }

        if ("MOUSE_MOVE".equals(event)) {

            // Gộp: chỉ nhớ vị trí mới nhất, chỉ đặt một tác vụ khi
            // trước đó chưa có tác vụ nào đang chờ
            boolean hadPending = pendingMove.getAndSet(new double[] {x, y}) != null;

            if (!hadPending) {
                submit(this::applyPendingMove);
            }

            return;
        }

        submit(() -> execute(event, x, y, button, delta, code));
    }

    private void submit(Runnable task) {

        try {
            worker.execute(() -> {

                try {
                    task.run();

                } catch (RuntimeException e) {
                    System.err.println(
                            "[REMOTE INPUT] Lỗi thực thi: " + e.getMessage());
                }
            });

        } catch (RuntimeException e) {
            // Executor đã đóng: bỏ qua
        }
    }

    // ==================================================================
    // THỰC THI (chạy trên luồng remote-input)
    // ==================================================================

    private void applyPendingMove() {

        double[] target = pendingMove.getAndSet(null);

        if (target != null) {
            moveTo(target[0], target[1]);
        }
    }

    private void execute(
            String event,
            double x,
            double y,
            int button,
            int delta,
            String code) {

        switch (event) {

            case "MOUSE_DOWN": {
                int mask = maskOf(button);

                if (mask != 0) {
                    // Đưa chuột tới đúng vị trí rồi mới bấm
                    moveTo(x, y);
                    robot.mousePress(mask);
                    pressedButtons.add(mask);
                }
                break;
            }

            case "MOUSE_UP": {
                int mask = maskOf(button);

                if (mask != 0) {
                    moveTo(x, y);
                    robot.mouseRelease(mask);
                    pressedButtons.remove(mask);
                }
                break;
            }

            case "MOUSE_WHEEL": {
                int notches = Math.max(
                        -MAX_WHEEL_NOTCHES,
                        Math.min(MAX_WHEEL_NOTCHES, delta));

                if (notches != 0) {
                    robot.mouseWheel(notches);
                }
                break;
            }

            case "KEY_DOWN": {
                Integer vk = KEY_MAP.get(code);

                if (vk != null) {
                    robot.keyPress(vk);
                    pressedKeys.add(vk);
                }
                break;
            }

            case "KEY_UP": {
                Integer vk = KEY_MAP.get(code);

                if (vk != null) {
                    robot.keyRelease(vk);
                    pressedKeys.remove(vk);
                }
                break;
            }

            case "RELEASE_ALL":
                releaseAll();
                break;

            default:
                // Sự kiện lạ: bỏ qua
        }
    }

    private void moveTo(double nx, double ny) {

        double cx = Math.max(0.0, Math.min(1.0, nx));
        double cy = Math.max(0.0, Math.min(1.0, ny));

        // width - 1 để (1.0) rơi đúng điểm ảnh cuối cùng của màn hình
        int px = screenBounds.x
                + (int) Math.round(cx * (screenBounds.width - 1));

        int py = screenBounds.y
                + (int) Math.round(cy * (screenBounds.height - 1));

        robot.mouseMove(px, py);
    }

    private static int maskOf(int button) {

        return (button >= 0 && button < BUTTON_MASKS.length)
                ? BUTTON_MASKS[button]
                : 0;
    }

    /**
     * Nhả mọi phím / nút đang giữ, tránh kẹt phím khi Admin mất kết nối
     * hoặc chuyển cửa sổ giữa lúc đang giữ Ctrl/Shift...
     */
    private void releaseAll() {

        for (int vk : new HashSet<>(pressedKeys)) {
            robot.keyRelease(vk);
        }

        pressedKeys.clear();

        for (int mask : new HashSet<>(pressedButtons)) {
            robot.mouseRelease(mask);
        }

        pressedButtons.clear();
    }

    // ==================================================================
    // ĐÓNG
    // ==================================================================

    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // Nhả phím trước khi dừng luồng; chờ ngắn cho tác vụ cuối chạy xong
        try {
            worker.execute(this::releaseAll);
            worker.shutdown();
            worker.awaitTermination(2, TimeUnit.SECONDS);

        } catch (Exception e) {
            worker.shutdownNow();
        }

        System.out.println("[REMOTE INPUT] Đã dừng điều khiển từ xa.");
    }

    // ==================================================================
    // BẢNG ÁNH XẠ PHÍM
    // ==================================================================

    private static Map<String, Integer> buildKeyMap() {

        Map<String, Integer> m = new HashMap<>();

        for (char c = 'A'; c <= 'Z'; c++) {
            m.put("Key" + c, KeyEvent.VK_A + (c - 'A'));
        }

        for (int d = 0; d <= 9; d++) {
            m.put("Digit" + d, KeyEvent.VK_0 + d);
            m.put("Numpad" + d, KeyEvent.VK_NUMPAD0 + d);
        }

        for (int f = 1; f <= 12; f++) {
            m.put("F" + f, KeyEvent.VK_F1 + (f - 1));
        }

        m.put("Enter", KeyEvent.VK_ENTER);
        m.put("NumpadEnter", KeyEvent.VK_ENTER);
        m.put("Escape", KeyEvent.VK_ESCAPE);
        m.put("Backspace", KeyEvent.VK_BACK_SPACE);
        m.put("Tab", KeyEvent.VK_TAB);
        m.put("Space", KeyEvent.VK_SPACE);
        m.put("CapsLock", KeyEvent.VK_CAPS_LOCK);

        m.put("ArrowUp", KeyEvent.VK_UP);
        m.put("ArrowDown", KeyEvent.VK_DOWN);
        m.put("ArrowLeft", KeyEvent.VK_LEFT);
        m.put("ArrowRight", KeyEvent.VK_RIGHT);

        m.put("Home", KeyEvent.VK_HOME);
        m.put("End", KeyEvent.VK_END);
        m.put("PageUp", KeyEvent.VK_PAGE_UP);
        m.put("PageDown", KeyEvent.VK_PAGE_DOWN);
        m.put("Insert", KeyEvent.VK_INSERT);
        m.put("Delete", KeyEvent.VK_DELETE);

        m.put("ShiftLeft", KeyEvent.VK_SHIFT);
        m.put("ShiftRight", KeyEvent.VK_SHIFT);
        m.put("ControlLeft", KeyEvent.VK_CONTROL);
        m.put("ControlRight", KeyEvent.VK_CONTROL);
        m.put("AltLeft", KeyEvent.VK_ALT);
        m.put("AltRight", KeyEvent.VK_ALT_GRAPH);
        m.put("MetaLeft", KeyEvent.VK_META);
        m.put("MetaRight", KeyEvent.VK_META);

        m.put("Minus", KeyEvent.VK_MINUS);
        m.put("Equal", KeyEvent.VK_EQUALS);
        m.put("BracketLeft", KeyEvent.VK_OPEN_BRACKET);
        m.put("BracketRight", KeyEvent.VK_CLOSE_BRACKET);
        m.put("Backslash", KeyEvent.VK_BACK_SLASH);
        m.put("Semicolon", KeyEvent.VK_SEMICOLON);
        m.put("Quote", KeyEvent.VK_QUOTE);
        m.put("Backquote", KeyEvent.VK_BACK_QUOTE);
        m.put("Comma", KeyEvent.VK_COMMA);
        m.put("Period", KeyEvent.VK_PERIOD);
        m.put("Slash", KeyEvent.VK_SLASH);

        m.put("NumpadAdd", KeyEvent.VK_ADD);
        m.put("NumpadSubtract", KeyEvent.VK_SUBTRACT);
        m.put("NumpadMultiply", KeyEvent.VK_MULTIPLY);
        m.put("NumpadDivide", KeyEvent.VK_DIVIDE);
        m.put("NumpadDecimal", KeyEvent.VK_DECIMAL);
        m.put("NumLock", KeyEvent.VK_NUM_LOCK);

        m.put("PrintScreen", KeyEvent.VK_PRINTSCREEN);
        m.put("ScrollLock", KeyEvent.VK_SCROLL_LOCK);
        m.put("Pause", KeyEvent.VK_PAUSE);
        m.put("ContextMenu", KeyEvent.VK_CONTEXT_MENU);

        return Map.copyOf(m);
    }
}