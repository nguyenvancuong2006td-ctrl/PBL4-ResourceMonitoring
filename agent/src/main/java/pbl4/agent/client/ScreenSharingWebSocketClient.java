package pbl4.agent.client;

import pbl4.agent.config.AgentConfig;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ScreenSharingWebSocketClient {

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    private static final Set<String> ALLOWED_SIGNAL_TYPES =
            Set.of(
                    "OFFER",
                    "ANSWER",
                    "ICE_CANDIDATE"
            );

    private final AgentConfig config;

    private final WebSocketStompClient stompClient;

    private final Consumer<Map<String, Object>> signalListener;

    private volatile StompSession session;

    private volatile String currentSessionCode;

    public ScreenSharingWebSocketClient(
            AgentConfig config,
            Consumer<Map<String, Object>> signalListener) {

        if (config == null) {
            throw new IllegalArgumentException(
                    "AgentConfig không được null."
            );
        }

        if (signalListener == null) {
            throw new IllegalArgumentException(
                    "Signal listener không được null."
            );
        }

        this.config = config;
        this.signalListener = signalListener;

        this.stompClient = new WebSocketStompClient(
                new StandardWebSocketClient()
        );

        // Converter JSON tương thích Spring Framework 7
        this.stompClient.setMessageConverter(
                new JacksonJsonMessageConverter()
        );
    }

    /**
     * Chuyển HTTP Server URL thành WebSocket URL.
     */
    private String buildWebSocketUrl() {

        String serverUrl = config.getServerUrl();

        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException(
                    "Server URL không được để trống."
            );
        }

        serverUrl = serverUrl.trim();

        while (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(
                    0,
                    serverUrl.length() - 1
            );
        }

        if (serverUrl.startsWith("https://")) {

            return serverUrl.replaceFirst(
                    "^https://",
                    "wss://"
            ) + "/ws";
        }

        if (serverUrl.startsWith("http://")) {

            return serverUrl.replaceFirst(
                    "^http://",
                    "ws://"
            ) + "/ws";
        }

        throw new IllegalArgumentException(
                "Server URL phải bắt đầu bằng http:// hoặc https://."
        );
    }

    /**
     * Kiểm tra cấu hình xác thực Agent.
     */
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

    /**
     * Kết nối WebSocket và đăng ký nhận tín hiệu.
     */
    public synchronized void connect(String sessionCode)
            throws Exception {

        if (sessionCode == null || sessionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Session code không được để trống."
            );
        }

        if (session != null && session.isConnected()) {

            if (sessionCode.equals(currentSessionCode)) {
                System.out.println(
                        "[SCREEN SHARING] Đã kết nối phiên này."
                );
                return;
            }

            throw new IllegalStateException(
                    "Agent đang kết nối với một phiên chia sẻ khác."
            );
        }

        validateAgentConfig();

        String url = buildWebSocketUrl();

        URI uri = URI.create(url);

        // STOMP headers dùng để xác thực Agent
        StompHeaders connectHeaders = new StompHeaders();

        connectHeaders.add(
                "X-Agent-Id",
                config.getAgentId()
        );

        connectHeaders.add(
                "X-Agent-Credential",
                config.getCredential()
        );

        System.out.println(
                "[SCREEN SHARING] Đang kết nối WebSocket: " + url
        );

        currentSessionCode = sessionCode;

        CompletableFuture<StompSession> future =
                stompClient.connectAsync(
                        uri,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {

                            @Override
                            public void afterConnected(
                                    StompSession connectedSession,
                                    StompHeaders connectedHeaders) {

                                session = connectedSession;

                                System.out.println(
                                        "[SCREEN SHARING] "
                                                + "Kết nối WebSocket thành công."
                                );

                                subscribeToSession(
                                        connectedSession,
                                        sessionCode
                                );
                            }

                            @Override
                            public void handleTransportError(
                                    StompSession stompSession,
                                    Throwable exception) {

                                System.err.println(
                                        "[SCREEN SHARING] "
                                                + "Lỗi WebSocket: "
                                                + exception.getMessage()
                                );

                                if (session == stompSession) {
                                    session = null;
                                    currentSessionCode = null;
                                }
                            }
                        }
                );

        try {

            future.get(
                    CONNECT_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            System.out.println(
                    "[SCREEN SHARING] Đã kết nối phiên: "
                            + sessionCode
            );

        } catch (TimeoutException e) {

            future.cancel(true);

            closeConnection();

            throw new IllegalStateException(
                    "Hết thời gian chờ kết nối WebSocket Server.",
                    e
            );

        } catch (Exception e) {

            closeConnection();

            throw new IllegalStateException(
                    "Không thể kết nối WebSocket Server: "
                            + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Đăng ký nhận tín hiệu từ phiên chia sẻ.
     */
    private void subscribeToSession(
            StompSession connectedSession,
            String sessionCode) {

        String destination =
                "/topic/screen-sharing/" + sessionCode;

        connectedSession.subscribe(
                destination,
                new StompFrameHandler() {

                    @Override
                    public Type getPayloadType(
                            StompHeaders headers) {

                        return Map.class;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload) {

                        if (!(payload instanceof Map<?, ?> map)) {

                            System.err.println(
                                    "[SCREEN SHARING] "
                                            + "Payload không hợp lệ."
                            );

                            return;
                        }

                        Map<String, Object> signal =
                                (Map<String, Object>) map;

                        try {

                            signalListener.accept(signal);

                        } catch (Exception e) {

                            System.err.println(
                                    "[SCREEN SHARING] "
                                            + "Lỗi xử lý tín hiệu: "
                                            + e.getMessage()
                            );
                        }
                    }
                }
        );

        System.out.println(
                "[SCREEN SHARING] Đã đăng ký topic: "
                        + destination
        );
    }

    /**
     * Gửi tín hiệu WebRTC lên Server.
     */
    public void sendSignal(
            String type,
            String data) {

        StompSession currentSession = session;

        if (currentSession == null
                || !currentSession.isConnected()) {

            throw new IllegalStateException(
                    "WebSocket chưa được kết nối."
            );
        }

        if (type == null || type.isBlank()) {

            throw new IllegalArgumentException(
                    "Signal type không được để trống."
            );
        }

        if (data == null || data.isBlank()) {

            throw new IllegalArgumentException(
                    "Signal data không được để trống."
            );
        }

        String signalType = type.trim().toUpperCase();

        if (!ALLOWED_SIGNAL_TYPES.contains(signalType)) {

            throw new IllegalArgumentException(
                    "Loại tín hiệu không được hỗ trợ: "
                            + signalType
            );
        }

        String sessionCode = currentSessionCode;

        if (sessionCode == null || sessionCode.isBlank()) {

            throw new IllegalStateException(
                    "Chưa có session code."
            );
        }

        String destination =
                "/app/screen-sharing/"
                        + sessionCode
                        + "/signal";

        Map<String, String> signal = Map.of(
                "type", signalType,
                "data", data
        );

        currentSession.send(
                destination,
                signal
        );

        System.out.println(
                "[SCREEN SHARING] Đã gửi tín hiệu: "
                        + signalType
        );
    }

    /**
     * Đóng kết nối hiện tại.
     */
    private synchronized void closeConnection() {

        StompSession currentSession = session;

        session = null;
        currentSessionCode = null;

        if (currentSession != null
                && currentSession.isConnected()) {

            try {
                currentSession.disconnect();

            } catch (Exception e) {

                System.err.println(
                        "[SCREEN SHARING] "
                                + "Lỗi khi đóng kết nối: "
                                + e.getMessage()
                );
            }
        }
    }

    /**
     * Ngắt kết nối WebSocket.
     */
    public synchronized void disconnect() {

        if (session == null) {
            currentSessionCode = null;
            return;
        }

        closeConnection();

        System.out.println(
                "[SCREEN SHARING] Đã ngắt kết nối WebSocket."
        );
    }

    /**
     * Kiểm tra trạng thái kết nối.
     */
    public boolean isConnected() {

        StompSession currentSession = session;

        return currentSession != null
                && currentSession.isConnected();
    }

    /**
     * Lấy mã phiên hiện tại.
     */
    public String getCurrentSessionCode() {
        return currentSessionCode;
    }
}