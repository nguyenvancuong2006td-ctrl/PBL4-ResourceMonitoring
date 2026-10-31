package pbl4.agent.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kết nối WebSocket từ Agent lên Server (endpoint /ws/screen)
 * để đẩy khung hình màn hình.
 *
 * Class này chỉ lo phần truyền tải:
 *   - mở / đóng kết nối
 *   - gửi văn bản (JSON điều khiển) và nhị phân (khung hình JPEG)
 *   - giữ kết nối bằng Ping định kỳ
 *
 * Việc tạo / phân tích JSON do ScreenSharingService đảm nhiệm.
 *
 * Lưu ý: WebSocket của JDK không cho phép gửi chồng nhau, nên mọi
 * lệnh gửi đều được tuần tự hóa và chờ hoàn tất. Nhờ vậy khi mạng
 * chậm, luồng đọc khung hình tự bị chậm lại và các khung hình cũ
 * bị bỏ ở phía script thu màn hình.
 */
public class ScreenStreamClient implements AutoCloseable {

    public static final String ENDPOINT_PATH = "/ws/screen";

    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final long PING_INTERVAL_SECONDS = 20;

    /**
     * Nhận sự kiện từ kết nối.
     */
    public interface Listener {

        /** Nhận một tin nhắn văn bản đầy đủ từ Server. */
        void onText(String text);

        /** Kết nối đã đóng (do Server hoặc do lỗi). */
        void onClosed(int statusCode, String reason);
    }

    private final URI uri;
    private final Listener listener;

    private final HttpClient httpClient;

    private final Object sendLock = new Object();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ScheduledExecutorService pingExecutor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {

                Thread thread = new Thread(runnable, "screen-stream-ping");
                thread.setDaemon(true);

                return thread;
            });

    private volatile WebSocket webSocket;

    public ScreenStreamClient(String serverUrl, Listener listener) {

        if (listener == null) {
            throw new IllegalArgumentException(
                    "Listener không được null.");
        }

        this.uri = URI.create(buildWebSocketUrl(serverUrl));
        this.listener = listener;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * Chuyển HTTP Server URL thành WebSocket URL.
     *
     *   http://host:8080  ->  ws://host:8080/ws/screen
     *   https://host      ->  wss://host/ws/screen
     */
    static String buildWebSocketUrl(String serverUrl) {

        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Server URL không được để trống.");
        }

        String url = serverUrl.trim();

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        if (url.startsWith("https://")) {
            return "wss://" + url.substring("https://".length())
                    + ENDPOINT_PATH;
        }

        if (url.startsWith("http://")) {
            return "ws://" + url.substring("http://".length())
                    + ENDPOINT_PATH;
        }

        throw new IllegalArgumentException(
                "Server URL phải bắt đầu bằng http:// hoặc https://.");
    }

    /**
     * Mở kết nối WebSocket.
     */
    public void connect() throws Exception {

        if (webSocket != null) {
            throw new IllegalStateException(
                    "Kết nối đã được mở trước đó.");
        }

        System.out.println("[SCREEN STREAM] Đang kết nối: " + uri);

        webSocket = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .buildAsync(uri, new Handler())
                .get(CONNECT_TIMEOUT_SECONDS + 2, TimeUnit.SECONDS);

        pingExecutor.scheduleWithFixedDelay(
                this::sendPingSafely,
                PING_INTERVAL_SECONDS,
                PING_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        System.out.println("[SCREEN STREAM] Đã kết nối.");
    }

    /**
     * Gửi tin nhắn văn bản (JSON).
     */
    public void sendText(String text) throws Exception {

        WebSocket target = requireOpen();

        synchronized (sendLock) {

            target.sendText(text, true)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Gửi một khung hình nhị phân.
     */
    public void sendBinary(byte[] data) throws Exception {

        WebSocket target = requireOpen();

        synchronized (sendLock) {

            target.sendBinary(ByteBuffer.wrap(data), true)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private WebSocket requireOpen() {

        WebSocket current = webSocket;

        if (current == null
                || closed.get()
                || current.isOutputClosed()
                || current.isInputClosed()) {

            throw new IllegalStateException(
                    "WebSocket chưa mở hoặc đã đóng.");
        }

        return current;
    }

    public boolean isOpen() {

        WebSocket current = webSocket;

        return current != null
                && !closed.get()
                && !current.isOutputClosed()
                && !current.isInputClosed();
    }

    private void sendPingSafely() {

        try {
            WebSocket current = requireOpen();

            synchronized (sendLock) {

                current.sendPing(ByteBuffer.allocate(0))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }

        } catch (Exception e) {

            if (!closed.get()) {
                System.err.println(
                        "[SCREEN STREAM] Gửi Ping thất bại: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Đóng kết nối. Có thể gọi nhiều lần.
     */
    @Override
    public void close() {

        if (!closed.compareAndSet(false, true)) {
            return;
        }

        pingExecutor.shutdownNow();

        WebSocket current = webSocket;

        if (current == null) {
            return;
        }

        try {
            synchronized (sendLock) {

                current.sendClose(WebSocket.NORMAL_CLOSURE, "Agent đóng kết nối")
                        .get(3, TimeUnit.SECONDS);
            }

        } catch (Exception e) {

            // Không đóng gọn được thì ngắt cứng
            current.abort();
        }

        System.out.println("[SCREEN STREAM] Đã đóng kết nối.");
    }

    /**
     * Xử lý sự kiện WebSocket của JDK.
     *
     * Mỗi onXxx phải gọi request(1) để nhận tin nhắn tiếp theo.
     */
    private final class Handler implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket ws,
                CharSequence data,
                boolean last) {

            textBuffer.append(data);

            if (last) {

                String message = textBuffer.toString();

                textBuffer.setLength(0);

                try {
                    listener.onText(message);

                } catch (RuntimeException e) {

                    System.err.println(
                            "[SCREEN STREAM] Lỗi xử lý tin nhắn: "
                                    + e.getMessage());
                }
            }

            ws.request(1);

            return null;
        }

        @Override
        public CompletionStage<?> onBinary(
                WebSocket ws,
                ByteBuffer data,
                boolean last) {

            // Server không gửi dữ liệu nhị phân cho Agent
            ws.request(1);

            return null;
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket ws,
                int statusCode,
                String reason) {

            if (closed.compareAndSet(false, true)) {

                pingExecutor.shutdownNow();

                try {
                    listener.onClosed(statusCode, reason);

                } catch (RuntimeException e) {

                    System.err.println(
                            "[SCREEN STREAM] Lỗi xử lý đóng kết nối: "
                                    + e.getMessage());
                }
            }

            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {

            System.err.println(
                    "[SCREEN STREAM] Lỗi WebSocket: " + error);

            if (closed.compareAndSet(false, true)) {

                pingExecutor.shutdownNow();

                try {
                    listener.onClosed(1006, String.valueOf(error.getMessage()));

                } catch (RuntimeException e) {

                    System.err.println(
                            "[SCREEN STREAM] Lỗi xử lý đóng kết nối: "
                                    + e.getMessage());
                }
            }
        }
    }
}