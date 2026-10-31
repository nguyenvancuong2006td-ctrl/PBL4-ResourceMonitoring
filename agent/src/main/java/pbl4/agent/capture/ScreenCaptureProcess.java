package pbl4.agent.capture;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Chạy tiến trình thu màn hình (script Python) và đọc khung hình từ nó.
 *
 * Giao thức giữa Agent và script:
 *
 *   stdout : chuỗi khung hình JPEG, mỗi khung hình gồm
 *              [4 byte độ dài, big-endian][dữ liệu JPEG]
 *
 *   stderr : mỗi dòng sự kiện có dạng
 *              PBL4_EVENT {"type":"STATUS","state":"STREAMING",...}
 *            các dòng khác (cảnh báo của GStreamer...) chỉ được ghi log.
 *
 *   stdin  : Agent không ghi gì. Script tự thoát khi stdin đóng
 *            (Agent bị tắt đột ngột).
 */
public class ScreenCaptureProcess implements AutoCloseable {

    private static final String EVENT_PREFIX = "PBL4_EVENT ";

    // Một khung hình JPEG hợp lệ không thể lớn hơn mức này
    private static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;

    private static final long STOP_TIMEOUT_MS = 3000;

    private final List<String> command;
    private final Consumer<byte[]> frameListener;
    private final Consumer<String> eventListener;
    private final IntConsumer exitListener;

    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private volatile Process process;

    private volatile Thread stdoutThread;
    private volatile Thread stderrThread;

    /**
     * @param command       lệnh khởi chạy script thu màn hình
     * @param frameListener nhận từng khung hình JPEG
     * @param eventListener nhận phần JSON của mỗi dòng PBL4_EVENT
     * @param exitListener  được gọi khi tiến trình kết thúc (mã thoát);
     *                      KHÔNG được gọi khi chính Agent chủ động dừng
     */
    public ScreenCaptureProcess(
            List<String> command,
            Consumer<byte[]> frameListener,
            Consumer<String> eventListener,
            IntConsumer exitListener) {

        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lệnh thu màn hình không được để trống.");
        }

        if (frameListener == null
                || eventListener == null
                || exitListener == null) {

            throw new IllegalArgumentException(
                    "Listener không được null.");
        }

        this.command = List.copyOf(command);
        this.frameListener = frameListener;
        this.eventListener = eventListener;
        this.exitListener = exitListener;
    }

    /**
     * Khởi chạy tiến trình và bắt đầu đọc dữ liệu.
     */
    public synchronized void start() throws IOException {

        if (process != null) {
            throw new IllegalStateException(
                    "Tiến trình thu màn hình đã được khởi chạy.");
        }

        System.out.println(
                "[SCREEN CAPTURE] Khởi chạy: " + String.join(" ", command));

        Process started = new ProcessBuilder(command).start();

        process = started;

        stdoutThread = startThread(
                "screen-capture-stdout", () -> readFrames(started));

        stderrThread = startThread(
                "screen-capture-stderr", () -> readEvents(started));

        startThread("screen-capture-wait", () -> waitForExit(started));
    }

    private Thread startThread(String name, Runnable task) {

        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    /**
     * Đọc các khung hình [độ dài][JPEG] từ stdout.
     */
    private void readFrames(Process target) {

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        target.getInputStream(),
                        64 * 1024))) {

            while (!stopping.get()) {

                int length = in.readInt();

                if (length <= 0 || length > MAX_FRAME_BYTES) {

                    System.err.println(
                            "[SCREEN CAPTURE] Độ dài khung hình không hợp lệ: "
                                    + length);

                    target.destroy();
                    return;
                }

                byte[] frame = new byte[length];

                in.readFully(frame);

                try {
                    frameListener.accept(frame);

                } catch (RuntimeException e) {

                    System.err.println(
                            "[SCREEN CAPTURE] Lỗi xử lý khung hình: "
                                    + e.getMessage());
                }
            }

        } catch (EOFException e) {
            // Tiến trình đã đóng stdout: kết thúc bình thường

        } catch (IOException e) {

            if (!stopping.get()) {
                System.err.println(
                        "[SCREEN CAPTURE] Lỗi đọc khung hình: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Đọc stderr: dòng PBL4_EVENT được chuyển cho eventListener,
     * các dòng khác chỉ ghi log.
     */
    private void readEvents(Process target) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        target.getErrorStream(),
                        StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.startsWith(EVENT_PREFIX)) {

                    String json = line.substring(EVENT_PREFIX.length());

                    try {
                        eventListener.accept(json);

                    } catch (RuntimeException e) {

                        System.err.println(
                                "[SCREEN CAPTURE] Lỗi xử lý sự kiện: "
                                        + e.getMessage());
                    }

                } else if (!line.isBlank()) {

                    System.err.println("[SCREEN CAPTURE] " + line);
                }
            }

        } catch (IOException e) {

            if (!stopping.get()) {
                System.err.println(
                        "[SCREEN CAPTURE] Lỗi đọc stderr: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Chờ tiến trình kết thúc rồi báo cho exitListener.
     */
    private void waitForExit(Process target) {

        int exitCode;

        try {
            exitCode = target.waitFor();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return;
        }

        if (stopping.get()) {
            return;
        }

        // Đọc nốt dữ liệu còn lại để không bỏ lỡ sự kiện lỗi cuối cùng
        // của script trước khi báo tiến trình đã kết thúc.
        joinQuietly(stdoutThread);
        joinQuietly(stderrThread);

        System.out.println(
                "[SCREEN CAPTURE] Tiến trình đã kết thúc, mã thoát: "
                        + exitCode);

        try {
            exitListener.accept(exitCode);

        } catch (RuntimeException e) {

            System.err.println(
                    "[SCREEN CAPTURE] Lỗi xử lý kết thúc tiến trình: "
                            + e.getMessage());
        }
    }

    private void joinQuietly(Thread thread) {

        if (thread == null) {
            return;
        }

        try {
            thread.join(2000);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() {

        Process current = process;

        return current != null && current.isAlive();
    }

    /**
     * Dừng tiến trình thu màn hình.
     *
     * Gửi SIGTERM trước để script đóng phiên Portal gọn gàng
     * (tắt biểu tượng "đang chia sẻ màn hình"), sau đó mới ép dừng.
     */
    @Override
    public void close() {

        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        Process current = process;

        if (current == null) {
            return;
        }

        current.destroy();

        try {
            if (!current.waitFor(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {

                System.err.println(
                        "[SCREEN CAPTURE] Tiến trình không thoát, ép dừng.");

                current.destroyForcibly();
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            current.destroyForcibly();
        }

        System.out.println("[SCREEN CAPTURE] Đã dừng tiến trình thu màn hình.");
    }
}