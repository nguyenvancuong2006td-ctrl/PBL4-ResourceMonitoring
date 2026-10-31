package pbl4.agent.service;

import pbl4.agent.client.ScreenSharingWebSocketClient;
import pbl4.agent.config.AgentConfig;

import java.time.LocalDateTime;
import java.util.Map;

public class ScreenSharingService {

    private final ScreenSharingWebSocketClient webSocketClient;

    // Trạng thái chia sẻ màn hình
    private volatile boolean sharing = false;

    // Mã phiên chia sẻ hiện tại
    private volatile String currentSessionCode;

    // Thời điểm bắt đầu chia sẻ
    private volatile LocalDateTime startedAt;

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

        this.webSocketClient = new ScreenSharingWebSocketClient(
                config,
                this::handleSignal
        );
    }

    /**
     * Xử lý tín hiệu nhận được từ Server.
     */
    private void handleSignal(Map<String, Object> signal) {

        if (signal == null || signal.isEmpty()) {
            System.err.println(
                    "[SCREEN SHARING] Tín hiệu nhận được không hợp lệ."
            );
            return;
        }

        Object type = signal.get("type");
        Object data = signal.get("data");
        Object senderType = signal.get("senderType");

        if (type == null || data == null) {
            System.err.println(
                    "[SCREEN SHARING] Tín hiệu thiếu type hoặc data."
            );
            return;
        }

        // Không xử lý tín hiệu do chính Agent gửi lên
        if ("AGENT".equals(senderType)) {
            return;
        }

        System.out.println(
                "[SCREEN SHARING] Nhận tín hiệu từ "
                        + senderType
                        + ": "
                        + type
        );

        /*
         * TODO:
         * Tích hợp WebRTC PeerConnection tại đây.
         *
         * OFFER:
         * Nhận SDP Offer và tạo SDP Answer.
         *
         * ANSWER:
         * Xử lý SDP Answer.
         *
         * ICE_CANDIDATE:
         * Thêm ICE Candidate vào PeerConnection.
         *
         * Hiện tại Agent mới tiếp nhận tín hiệu,
         * chưa xử lý WebRTC hoặc truyền video.
         */
    }

    /**
     * Bắt đầu phiên chia sẻ màn hình.
     *
     * Agent sẽ kết nối WebSocket Server và đăng ký
     * topic tương ứng với sessionCode.
     */
    public synchronized String startSharing(String sessionCode) {

        if (sessionCode == null || sessionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Session code không được để trống."
            );
        }

        sessionCode = sessionCode.trim();

        // Nếu đang chia sẻ đúng phiên này
        if (sharing
                && sessionCode.equals(currentSessionCode)
                && webSocketClient.isConnected()) {

            return "Phiên chia sẻ đã được khởi tạo trước đó: "
                    + sessionCode;
        }

        // Không cho phép một Agent chạy hai phiên cùng lúc
        if (sharing || webSocketClient.isConnected()) {

            throw new IllegalStateException(
                    "Agent đang có một phiên chia sẻ khác: "
                            + currentSessionCode
            );
        }

        System.out.println(
                "[SCREEN SHARING] Đang khởi tạo phiên: "
                        + sessionCode
        );

        try {

            // Kết nối WebSocket và đăng ký nhận tín hiệu
            webSocketClient.connect(sessionCode);

            // Chỉ cập nhật trạng thái khi kết nối thành công
            currentSessionCode = sessionCode;
            startedAt = LocalDateTime.now();
            sharing = true;

            System.out.println(
                    "[SCREEN SHARING] Đã kết nối WebSocket."
            );

            System.out.println(
                    "[SCREEN SHARING] Đã khởi tạo phiên: "
                            + sessionCode
            );

            System.out.println(
                    "[SCREEN SHARING] Thời gian bắt đầu: "
                            + startedAt
            );

            return "Agent đã kết nối WebSocket và khởi tạo phiên: "
                    + sessionCode;

        } catch (Exception e) {

            // Dọn dẹp nếu kết nối thất bại
            webSocketClient.disconnect();

            sharing = false;
            currentSessionCode = null;
            startedAt = null;

            System.err.println(
                    "[SCREEN SHARING] Không thể khởi tạo phiên: "
                            + e.getMessage()
            );

            throw new IllegalStateException(
                    "Không thể bắt đầu chia sẻ màn hình: "
                            + e.getMessage(),
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
        if (!sharing) {

            if (webSocketClient.isConnected()) {
                webSocketClient.disconnect();
            }

            currentSessionCode = null;
            startedAt = null;

            return "Không có phiên chia sẻ đang hoạt động.";
        }

        // Kiểm tra mã phiên để tránh dừng nhầm
        if (!sessionCode.equals(currentSessionCode)) {

            throw new IllegalStateException(
                    "Session code không khớp với phiên đang hoạt động."
            );
        }

        String stoppedSessionCode = currentSessionCode;

        try {

            // Ngắt kết nối WebSocket
            webSocketClient.disconnect();

        } finally {

            // Luôn giải phóng trạng thái cục bộ
            sharing = false;
            currentSessionCode = null;
            startedAt = null;
        }

        System.out.println(
                "[SCREEN SHARING] Đã dừng phiên: "
                        + stoppedSessionCode
        );

        return "Đã dừng phiên chia sẻ: "
                + stoppedSessionCode;
    }

    /**
     * Kiểm tra trạng thái chia sẻ.
     */
    public boolean isSharing() {
        return sharing;
    }

    /**
     * Kiểm tra trạng thái kết nối WebSocket.
     */
    public boolean isWebSocketConnected() {
        return webSocketClient.isConnected();
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

    /**
     * Gửi tín hiệu WebRTC lên Server.
     */
    public void sendSignal(String type, String data) {

        if (!sharing) {
            throw new IllegalStateException(
                    "Agent chưa bắt đầu chia sẻ màn hình."
            );
        }

        webSocketClient.sendSignal(type, data);
    }
}