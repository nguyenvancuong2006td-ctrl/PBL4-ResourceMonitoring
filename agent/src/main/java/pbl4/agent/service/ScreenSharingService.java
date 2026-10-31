package pbl4.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pbl4.agent.capture.ScreenCaptureProcess;
import pbl4.agent.capture.ScreenCaptureSettings;
import pbl4.agent.client.ScreenStreamClient;
import pbl4.agent.config.AgentConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chia sẻ màn hình của máy Client lên Server.
 *
 * Luồng hoạt động:
 *
 *   START_SCREEN_SHARING (sessionCode)
 *     1. Agent mở WebSocket tới Server (/ws/screen)
 *     2. Agent gửi AUTH (agentId + credential + sessionCode)
 *     3. Server trả JOINED
 *     4. Agent chạy script thu màn hình (Python + GStreamer)
 *     5. Mỗi khung hình JPEG từ script được gửi lên Server dạng nhị phân
 *        và Server chuyển tiếp cho trình xem của Admin
 *
 *   STOP_SCREEN_SHARING (sessionCode)
 *     Dừng script thu màn hình và đóng WebSocket.
 *
 * Trạng thái thu màn hình (đang chờ người dùng xác nhận, đang truyền,
 * lỗi...) được script báo về qua các sự kiện STATUS / ERROR và Agent
 * chuyển tiếp lên Server để Admin nhìn thấy.
 */
public class ScreenSharingService {

    private static final long JOIN_TIMEOUT_SECONDS = 10;

    private static final String SCRIPT_RESOURCE =
            "/scripts/" + ScreenCaptureSettings.SCRIPT_FILE_NAME;

    // Chỉ chuyển tiếp các loại sự kiện này từ script lên Server
    private static final Set<String> FORWARDED_EVENT_TYPES =
            Set.of("STATUS", "ERROR");

    private final AgentConfig config;

    private final ScreenCaptureSettings settings;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Phiên chia sẻ đang hoạt động (null nếu không có). Bảo vệ bởi "this".
    private ActiveStream active;

    // Trạng thái chia sẻ màn hình
    private volatile boolean sharing = false;

    // Mã phiên chia sẻ hiện tại
    private volatile String currentSessionCode;

    // Thời điểm bắt đầu chia sẻ
    private volatile LocalDateTime startedAt;

    /**
     * Một phiên chia sẻ đang chạy: WebSocket + tiến trình thu màn hình.
     */
    private static final class ActiveStream {

        final String sessionCode;
        final ScreenStreamClient client;

        volatile ScreenCaptureProcess capture;

        // Đã báo lỗi truyền tải (tránh tạo nhiều luồng dọn dẹp)
        final AtomicBoolean failureHandled = new AtomicBoolean(false);

        // Script đã báo ít nhất một sự kiện ERROR lên Server
        volatile boolean errorReported = false;

        ActiveStream(String sessionCode, ScreenStreamClient client) {
            this.sessionCode = sessionCode;
            this.client = client;
        }
    }

    /**
     * Khởi tạo ScreenSharingService.
     *
     * @param config cấu hình Agent dùng để kết nối Server
     */
    public ScreenSharingService(AgentConfig config) {

        if (config == null) {
            throw new IllegalArgumentException(
                    "AgentConfig không được null."
            );
        }

        this.config = config;
        this.settings = ScreenCaptureSettings.load();
    }

    /**
     * Bắt đầu phiên chia sẻ màn hình.
     *
     * Hàm trả về ngay sau khi kết nối Server thành công và script thu
     * màn hình đã được khởi chạy. Việc người dùng ở máy Client xác nhận
     * chia sẻ (Wayland) diễn ra sau đó và được báo qua sự kiện STATUS.
     */
    public synchronized String startSharing(String sessionCode) {

        if (sessionCode == null || sessionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Session code không được để trống."
            );
        }

        sessionCode = sessionCode.trim();

        // Nếu đang chia sẻ đúng phiên này
        if (active != null
                && sessionCode.equals(active.sessionCode)
                && active.client.isOpen()) {

            return "Phiên chia sẻ đã được khởi tạo trước đó: "
                    + sessionCode;
        }

        // Không cho phép một Agent chạy hai phiên cùng lúc
        if (active != null) {

            throw new IllegalStateException(
                    "Agent đang có một phiên chia sẻ khác: "
                            + active.sessionCode
            );
        }

        validateAgentConfig();

        System.out.println(
                "[SCREEN SHARING] Đang khởi tạo phiên: " + sessionCode
        );

        final String code = sessionCode;

        CompletableFuture<Void> joined = new CompletableFuture<>();

        ScreenStreamClient client = new ScreenStreamClient(
                config.getServerUrl(),
                new StreamListener(code, joined)
        );

        ActiveStream stream = new ActiveStream(code, client);

        try {

            // 1. Kết nối WebSocket và xác thực
            client.connect();

            client.sendText(buildAuthMessage(code));

            waitForJoin(joined);

            // 2. Khởi chạy script thu màn hình
            Path script = extractScript();

            ScreenCaptureProcess capture = new ScreenCaptureProcess(
                    buildCaptureCommand(script),
                    frame -> forwardFrame(stream, frame),
                    event -> forwardEvent(stream, event),
                    exitCode -> onCaptureExited(stream, exitCode)
            );

            stream.capture = capture;

            capture.start();

            // 3. Chỉ cập nhật trạng thái khi mọi bước thành công
            active = stream;
            currentSessionCode = code;
            startedAt = LocalDateTime.now();
            sharing = true;

            System.out.println(
                    "[SCREEN SHARING] Đã khởi tạo phiên: " + code
            );

            return "Agent đã kết nối Server và bắt đầu thu màn hình, "
                    + "phiên: " + code;

        } catch (Exception e) {

            // Dọn dẹp nếu có bước thất bại
            ScreenCaptureProcess capture = stream.capture;

            if (capture != null) {
                capture.close();
            }

            client.close();

            active = null;
            sharing = false;
            currentSessionCode = null;
            startedAt = null;

            String message = describe(e);

            System.err.println(
                    "[SCREEN SHARING] Không thể khởi tạo phiên: " + message
            );

            throw new IllegalStateException(
                    "Không thể bắt đầu chia sẻ màn hình: " + message,
                    e
            );
        }
    }

    /**
     * Dừng phiên chia sẻ màn hình.
     */
    public synchronized String stopSharing(String sessionCode) {

        if (sessionCode == null || sessionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Session code không được để trống."
            );
        }

        sessionCode = sessionCode.trim();

        // Không có phiên đang hoạt động
        if (active == null) {

            sharing = false;
            currentSessionCode = null;
            startedAt = null;

            return "Không có phiên chia sẻ đang hoạt động.";
        }

        // Kiểm tra mã phiên để tránh dừng nhầm
        if (!sessionCode.equals(active.sessionCode)) {

            throw new IllegalStateException(
                    "Session code không khớp với phiên đang hoạt động."
            );
        }

        String stoppedSessionCode = active.sessionCode;

        releaseActiveStream();

        System.out.println(
                "[SCREEN SHARING] Đã dừng phiên: " + stoppedSessionCode
        );

        return "Đã dừng phiên chia sẻ: " + stoppedSessionCode;
    }

    /**
     * Giải phóng phiên đang hoạt động. Gọi khi đang giữ khóa "this".
     */
    private void releaseActiveStream() {

        ActiveStream stream = active;

        active = null;
        sharing = false;
        currentSessionCode = null;
        startedAt = null;

        if (stream == null) {
            return;
        }

        try {
            ScreenCaptureProcess capture = stream.capture;

            if (capture != null) {
                capture.close();
            }

        } finally {

            stream.client.close();
        }
    }

    /**
     * Dọn dẹp khi phiên bị gián đoạn ngoài ý muốn
     * (mất kết nối Server, script thu màn hình thoát...).
     *
     * Chạy trên một luồng riêng để không tự chờ chính mình.
     */
    private void teardownAsync(ActiveStream stream, String reason) {

        if (!stream.failureHandled.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(() -> {

            synchronized (ScreenSharingService.this) {

                if (active != stream) {
                    return;
                }

                System.err.println(
                        "[SCREEN SHARING] Phiên " + stream.sessionCode
                                + " bị gián đoạn: " + reason
                );

                releaseActiveStream();
            }

        }, "screen-sharing-teardown");

        thread.setDaemon(true);
        thread.start();
    }

    // ==================================================================
    // LUỒNG DỮ LIỆU: script -> Agent -> Server
    // ==================================================================

    /**
     * Gửi một khung hình JPEG lên Server.
     */
    private void forwardFrame(ActiveStream stream, byte[] frame) {

        try {
            stream.client.sendBinary(frame);

        } catch (Exception e) {

            teardownAsync(
                    stream,
                    "không gửi được khung hình: " + describe(e)
            );
        }
    }

    /**
     * Chuyển tiếp sự kiện STATUS / ERROR của script lên Server.
     */
    private void forwardEvent(ActiveStream stream, String json) {

        try {
            JsonNode node = objectMapper.readTree(json);

            String type = node.path("type").asText("");

            if (!FORWARDED_EVENT_TYPES.contains(type)) {
                return;
            }

            if ("ERROR".equals(type)) {
                stream.errorReported = true;
            }

            System.out.println(
                    "[SCREEN SHARING] " + type + ": "
                            + node.path("message").asText("")
            );

            stream.client.sendText(json);

        } catch (Exception e) {

            System.err.println(
                    "[SCREEN SHARING] Không chuyển tiếp được sự kiện: "
                            + describe(e)
            );
        }
    }

    /**
     * Script thu màn hình đã thoát ngoài ý muốn.
     */
    private void onCaptureExited(ActiveStream stream, int exitCode) {

        if (exitCode != 0 && !stream.errorReported) {

            try {
                ObjectNode error = objectMapper.createObjectNode();

                error.put("type", "ERROR");
                error.put(
                        "message",
                        "Tiến trình thu màn hình đã dừng đột ngột (mã "
                                + exitCode + ")."
                );

                stream.client.sendText(error.toString());

            } catch (Exception e) {

                System.err.println(
                        "[SCREEN SHARING] Không báo được lỗi lên Server: "
                                + describe(e)
                );
            }
        }

        teardownAsync(
                stream,
                "tiến trình thu màn hình đã thoát, mã " + exitCode
        );
    }

    // ==================================================================
    // NHẬN TIN NHẮN TỪ SERVER
    // ==================================================================

    private final class StreamListener implements ScreenStreamClient.Listener {

        private final String sessionCode;
        private final CompletableFuture<Void> joined;

        StreamListener(
                String sessionCode,
                CompletableFuture<Void> joined) {

            this.sessionCode = sessionCode;
            this.joined = joined;
        }

        @Override
        public void onText(String text) {

            JsonNode node;

            try {
                node = objectMapper.readTree(text);

            } catch (IOException e) {

                System.err.println(
                        "[SCREEN SHARING] Tin nhắn Server không hợp lệ."
                );
                return;
            }

            String type = node.path("type").asText("");

            if ("JOINED".equals(type)) {

                joined.complete(null);

            } else if ("ERROR".equals(type)) {

                String message = node.path("message")
                        .asText("Server từ chối kết nối.");

                // Trước khi JOINED: lỗi xác thực / phiên không hợp lệ
                if (!joined.completeExceptionally(
                        new IllegalStateException(message))) {

                    System.err.println(
                            "[SCREEN SHARING] Server báo lỗi: " + message);
                }
            }

            // Các loại khác (ví dụ điều khiển chuột/bàn phím sau này)
            // sẽ được xử lý tại đây.
        }

        @Override
        public void onClosed(int statusCode, String reason) {

            System.out.println(
                    "[SCREEN SHARING] Server đã đóng kết nối ("
                            + statusCode + "): " + reason
            );

            // Đóng trước khi JOINED: startSharing đang chờ
            joined.completeExceptionally(
                    new IllegalStateException(
                            reason == null || reason.isBlank()
                                    ? "Server đã đóng kết nối."
                                    : reason
                    )
            );

            // Đóng khi đang chia sẻ: dọn dẹp phiên
            ActiveStream stream;

            synchronized (ScreenSharingService.this) {
                stream = active;
            }

            if (stream != null && sessionCode.equals(stream.sessionCode)) {
                teardownAsync(stream, "Server đóng kết nối");
            }
        }
    }

    // ==================================================================
    // HÀM PHỤ
    // ==================================================================

    private void validateAgentConfig() {

        if (config.getAgentId() == null
                || config.getAgentId().isBlank()) {

            throw new IllegalStateException(
                    "Agent ID không được để trống."
            );
        }

        if (config.getCredential() == null
                || config.getCredential().isBlank()) {

            throw new IllegalStateException(
                    "Agent Credential không được để trống."
            );
        }
    }

    private String buildAuthMessage(String sessionCode) {

        ObjectNode auth = objectMapper.createObjectNode();

        auth.put("type", "AUTH");
        auth.put("role", "AGENT");
        auth.put("agentId", config.getAgentId());
        auth.put("credential", config.getCredential());
        auth.put("sessionCode", sessionCode);

        return auth.toString();
    }

    private void waitForJoin(CompletableFuture<Void> joined)
            throws Exception {

        try {
            joined.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (TimeoutException e) {

            throw new IllegalStateException(
                    "Server không phản hồi yêu cầu tham gia phiên.", e
            );

        } catch (ExecutionException e) {

            Throwable cause = e.getCause() != null ? e.getCause() : e;

            throw new IllegalStateException(describe(cause), cause);
        }
    }

    /**
     * Chép script thu màn hình từ classpath ra thư mục dữ liệu của Agent
     * (Python cần một file thật để chạy).
     */
    private Path extractScript() throws IOException {

        Path target = settings.getScriptPath();

        try (InputStream in =
                     ScreenSharingService.class.getResourceAsStream(
                             SCRIPT_RESOURCE)) {

            if (in == null) {
                throw new IOException(
                        "Không tìm thấy " + SCRIPT_RESOURCE
                                + " trong tài nguyên của Agent."
                );
            }

            byte[] content = in.readAllBytes();

            Files.createDirectories(target.getParent());

            if (!Files.exists(target)
                    || !Arrays.equals(Files.readAllBytes(target), content)) {

                Files.write(target, content);
            }
        }

        return target;
    }

    private List<String> buildCaptureCommand(Path script) {

        List<String> command = new ArrayList<>();

        command.add(settings.getPythonPath());
        command.add(script.toString());

        command.add("--backend");
        command.add(settings.getBackend());

        command.add("--fps");
        command.add(String.valueOf(settings.getFps()));

        command.add("--max-width");
        command.add(String.valueOf(settings.getMaxWidth()));

        command.add("--quality");
        command.add(String.valueOf(settings.getQuality()));

        command.add("--restore-token-file");
        command.add(settings.getRestoreTokenPath().toString());

        return command;
    }

    private String describe(Throwable e) {

        String message = e.getMessage();

        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : message;
    }

    // ==================================================================
    // TRẠNG THÁI
    // ==================================================================

    /**
     * Kiểm tra trạng thái chia sẻ.
     */
    public boolean isSharing() {
        return sharing;
    }

    /**
     * Kiểm tra trạng thái kết nối WebSocket.
     */
    public synchronized boolean isWebSocketConnected() {
        return active != null && active.client.isOpen();
    }

    /**
     * Lấy mã phiên hiện tại.
     */
    public String getCurrentSessionCode() {
        return currentSessionCode;
    }

    /**
     * Lấy thời điểm bắt đầu chia sẻ.
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }
}