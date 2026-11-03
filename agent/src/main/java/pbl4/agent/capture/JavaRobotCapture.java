package pbl4.agent.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Thu màn hình bằng Java thuần (java.awt.Robot), không cần Python.
 *
 * Dùng cho máy Client chạy X11. Trên Wayland, Robot chỉ chụp được
 * cửa sổ XWayland (thường ra màn đen) nên Agent dùng script Python + Portal.
 *
 * Giao diện giống ScreenCaptureProcess để ScreenSharingService dùng chung:
 *   - frameListener : nhận từng khung hình JPEG
 *   - eventListener : nhận JSON sự kiện STATUS / ERROR
 *   - exitListener  : được gọi khi việc thu bị lỗi và dừng ngoài ý muốn;
 *                     KHÔNG được gọi khi chính Agent chủ động dừng
 *
 * Điều kiện chạy:
 *   - Agent chạy trong phiên đồ họa của người dùng (có DISPLAY)
 *   - Java đầy đủ (không phải bản "-headless") và đã tắt chế độ headless
 *     (AgentApplication gọi setHeadless(false))
 *
 * Màn hình đứng yên thì không gửi lại khung hình giống hệt, nhưng vẫn gửi
 * một khung mỗi KEEP_ALIVE_SECONDS giây để Viewer luôn bắt kịp.
 */
public class JavaRobotCapture implements AutoCloseable {

    private static final long STOP_TIMEOUT_MS = 3000;

    // Màn hình không đổi thì vẫn gửi lại một khung sau chừng này giây
    private static final long KEEP_ALIVE_SECONDS = 5;
    private static final long KEEP_ALIVE_NANOS =
            TimeUnit.SECONDS.toNanos(KEEP_ALIVE_SECONDS);

    private final int fps;
    private final int maxWidth;
    private final float quality;

    private final Consumer<byte[]> frameListener;
    private final Consumer<String> eventListener;
    private final IntConsumer exitListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private volatile Thread thread;

    // Chỉ dùng trên luồng thu (được khởi tạo trước khi luồng chạy)
    private Robot robot;
    private Rectangle screenBounds;
    private ImageWriter writer;
    private ImageWriteParam writeParam;

    /**
     * @param fps           số khung hình tối đa mỗi giây (1-60)
     * @param maxWidth      chiều rộng tối đa, 0 = không thu nhỏ
     * @param quality       chất lượng JPEG 1-100
     * @param frameListener nhận từng khung hình JPEG
     * @param eventListener nhận JSON sự kiện STATUS / ERROR
     * @param exitListener  được gọi khi việc thu lỗi và dừng ngoài ý muốn
     */
    public JavaRobotCapture(
            int fps,
            int maxWidth,
            int quality,
            Consumer<byte[]> frameListener,
            Consumer<String> eventListener,
            IntConsumer exitListener) {

        if (fps < 1 || fps > 60) {
            throw new IllegalArgumentException(
                    "FPS phải nằm trong khoảng 1-60.");
        }

        if (maxWidth < 0) {
            throw new IllegalArgumentException(
                    "Chiều rộng tối đa không được âm.");
        }

        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException(
                    "Chất lượng JPEG phải nằm trong khoảng 1-100.");
        }

        if (frameListener == null
                || eventListener == null
                || exitListener == null) {

            throw new IllegalArgumentException(
                    "Listener không được null.");
        }

        this.fps = fps;
        this.maxWidth = maxWidth;
        this.quality = quality / 100f;
        this.frameListener = frameListener;
        this.eventListener = eventListener;
        this.exitListener = exitListener;
    }

    /**
     * Khởi tạo Robot, bộ mã hóa JPEG và bắt đầu thu.
     *
     * @throws IOException nếu không thu được màn hình trên máy này
     */
    public synchronized void start() throws IOException {

        if (thread != null) {
            throw new IllegalStateException(
                    "Việc thu màn hình đã được khởi chạy.");
        }

        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException(
                    "Java đang chạy ở chế độ headless nên không thu được "
                            + "màn hình. Hãy chạy Agent trong phiên đồ họa "
                            + "(có DISPLAY) và dùng Java đầy đủ, không phải "
                            + "bản \"-headless\".");
        }

        try {
            robot = new Robot();

            screenBounds = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds();

        } catch (Throwable e) {

            // AWTException, HeadlessException, AWTError (không kết nối được
            // X server), UnsatisfiedLinkError (thiếu thư viện AWT)...
            throw new IOException(
                    "Không khởi tạo được Java Robot: " + describe(e)
                            + ". Kiểm tra Agent đang chạy trong phiên đồ "
                            + "họa X11 và Java không phải bản \"-headless\".",
                    e);
        }

        initEncoder();

        System.out.println(
                "[JAVA CAPTURE] Bắt đầu thu: " + screenBounds.width + "x"
                        + screenBounds.height + ", fps=" + fps
                        + ", max-width=" + maxWidth
                        + ", quality=" + Math.round(quality * 100));

        Thread t = new Thread(this::captureLoop, "java-robot-capture");
        t.setDaemon(true);

        thread = t;

        t.start();
    }

    private void initEncoder() throws IOException {

        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByFormatName("jpeg");

        if (!writers.hasNext()) {
            throw new IOException(
                    "Java không có bộ mã hóa JPEG.");
        }

        writer = writers.next();

        writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality);
    }

    // ==================================================================
    // VÒNG LẶP THU
    // ==================================================================

    private void captureLoop() {

        final long intervalNanos = 1_000_000_000L / fps;

        long nextTick = System.nanoTime();
        long lastSentAt = 0;

        boolean sentAny = false;
        boolean failed = false;

        int[] previousPixels = null;
        Point previousCursor = null;

        try {
            while (!stopping.get()) {

                BufferedImage shot = robot.createScreenCapture(screenBounds);
                Point cursor = readCursor();
                int[] pixels = pixelsOf(shot);

                boolean unchanged = sentAny
                        && pixels != null
                        && previousPixels != null
                        && Arrays.equals(pixels, previousPixels)
                        && Objects.equals(cursor, previousCursor);

                boolean keepAliveDue =
                        System.nanoTime() - lastSentAt >= KEEP_ALIVE_NANOS;

                if (!unchanged || keepAliveDue) {

                    byte[] jpeg = encode(render(shot, cursor));

                    try {
                        frameListener.accept(jpeg);

                    } catch (RuntimeException e) {

                        System.err.println(
                                "[JAVA CAPTURE] Lỗi xử lý khung hình: "
                                        + describe(e));
                    }

                    lastSentAt = System.nanoTime();

                    if (!sentAny) {
                        sentAny = true;

                        emit("STATUS", "STREAMING", "Đang truyền màn hình.");
                    }
                }

                previousPixels = pixels;
                previousCursor = cursor;

                // Giữ nhịp theo FPS; nếu bị chậm thì không cố đuổi kịp
                nextTick += intervalNanos;

                long wait = nextTick - System.nanoTime();

                if (wait > 0) {
                    TimeUnit.NANOSECONDS.sleep(wait);
                } else {
                    nextTick = System.nanoTime();
                }
            }

        } catch (InterruptedException e) {

            // close() ngắt luồng để dừng nhanh
            Thread.currentThread().interrupt();

        } catch (Throwable e) {

            if (!stopping.get()) {

                failed = true;

                System.err.println(
                        "[JAVA CAPTURE] Lỗi thu màn hình: " + describe(e));

                emit("ERROR", null,
                        "Lỗi thu màn hình bằng Java: " + describe(e));
            }

        } finally {

            writer.dispose();
        }

        if (failed) {
            try {
                exitListener.accept(1);

            } catch (RuntimeException e) {

                System.err.println(
                        "[JAVA CAPTURE] Lỗi xử lý kết thúc: " + describe(e));
            }
        }
    }

    /**
     * Vẽ khung hình đầu ra: thu nhỏ nếu cần và vẽ con trỏ chuột
     * (Robot không chụp con trỏ).
     *
     * Vẽ sang ảnh mới để không làm thay đổi ảnh gốc, vì mảng điểm ảnh
     * của ảnh gốc được dùng để so sánh với khung hình kế tiếp.
     */
    private BufferedImage render(BufferedImage shot, Point cursor) {

        int sourceWidth = shot.getWidth();
        int sourceHeight = shot.getHeight();

        double scale = (maxWidth > 0 && sourceWidth > maxWidth)
                ? (double) maxWidth / sourceWidth
                : 1.0;

        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage out =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();

        try {
            if (scale < 1.0) {
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                g.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
            }

            g.drawImage(shot, 0, 0, width, height, null);

            if (cursor != null) {

                int x = (int) Math.round((cursor.x - screenBounds.x) * scale);
                int y = (int) Math.round((cursor.y - screenBounds.y) * scale);

                if (x >= 0 && y >= 0 && x < width && y < height) {
                    drawCursor(g, x, y);
                }
            }

        } finally {
            g.dispose();
        }

        return out;
    }

    private void drawCursor(Graphics2D g, int x, int y) {

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        Polygon arrow = new Polygon(
                new int[] {0, 0, 4, 8, 11, 7, 12},
                new int[] {0, 17, 13, 20, 19, 12, 12},
                7);

        arrow.translate(x, y);

        g.setColor(Color.WHITE);
        g.fillPolygon(arrow);

        g.setColor(Color.BLACK);
        g.drawPolygon(arrow);
    }

    private byte[] encode(BufferedImage image) throws IOException {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);

        // Dùng bộ nhớ thay vì file tạm của ImageIO
        try (MemoryCacheImageOutputStream out =
                     new MemoryCacheImageOutputStream(bytes)) {

            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        }

        return bytes.toByteArray();
    }

    private Point readCursor() {

        try {
            PointerInfo info = MouseInfo.getPointerInfo();

            return info == null ? null : info.getLocation();

        } catch (RuntimeException e) {
            return null;
        }
    }

    private int[] pixelsOf(BufferedImage image) {

        if (image.getRaster().getDataBuffer() instanceof DataBufferInt) {

            return ((DataBufferInt) image.getRaster().getDataBuffer())
                    .getData();
        }

        return null;
    }

    private void emit(String type, String state, String message) {

        try {
            ObjectNode node = objectMapper.createObjectNode();

            node.put("type", type);

            if (state != null) {
                node.put("state", state);
            }

            node.put("message", message);

            eventListener.accept(node.toString());

        } catch (RuntimeException e) {

            System.err.println(
                    "[JAVA CAPTURE] Lỗi gửi sự kiện: " + describe(e));
        }
    }

    private String describe(Throwable e) {

        String message = e.getMessage();

        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }

    public boolean isRunning() {

        Thread current = thread;

        return current != null && current.isAlive();
    }

    /**
     * Dừng việc thu màn hình. Có thể gọi nhiều lần.
     */
    @Override
    public void close() {

        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        Thread current = thread;

        if (current != null) {

            current.interrupt();

            try {
                current.join(STOP_TIMEOUT_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[JAVA CAPTURE] Đã dừng thu màn hình.");
    }
}