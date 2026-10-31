package com.pbl4.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.service.AccountDetailsService;
import com.pbl4.server.service.AgentCredentialService;
import com.pbl4.server.service.ScreenSharingSessionService;
import com.pbl4.server.service.ScreenSharingWebSocketAccessService;
import com.pbl4.server.util.JwtUtil;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Endpoint /ws/screen: nhận khung hình JPEG từ Agent và chuyển cho Admin.
 *
 * Giao thức (mọi kết nối phải gửi AUTH đầu tiên):
 *
 *   Agent  -> {"type":"AUTH","role":"AGENT","agentId":"..","credential":"..","sessionCode":".."}
 *   Viewer -> {"type":"AUTH","role":"VIEWER","token":"<JWT>","sessionCode":".."}
 *   Server -> {"type":"JOINED"}  hoặc  {"type":"ERROR","message":".."} rồi đóng
 *
 *   Sau khi JOINED:
 *     Agent  -> nhị phân: một khung hình JPEG mỗi tin nhắn
 *     Agent  -> văn bản : STATUS / ERROR (chuyển tiếp nguyên văn cho Viewer)
 *     Server -> Viewer  : nhị phân (JPEG) + văn bản (STATUS, ERROR,
 *                         AGENT_CONNECTED, ENDED)
 */
@Component
public class ScreenStreamWebSocketHandler extends AbstractWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;

    private static final Set<String> FORWARDED_AGENT_EVENTS =
            Set.of("STATUS", "ERROR");

    private final ObjectMapper objectMapper;
    private final AgentCredentialService agentCredentialService;
    private final JwtUtil jwtUtil;
    private final AccountDetailsService accountDetailsService;
    private final ScreenSharingWebSocketAccessService accessService;
    private final ScreenSharingSessionService sessionService;

    // sessionCode -> phòng
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    // id kết nối WebSocket -> thông tin kết nối
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    private static final class Peer {

        final WebSocketSession raw;
        final WebSocketSession sender; // gửi an toàn đa luồng

        volatile String role;        // AGENT | VIEWER (null = chưa AUTH)
        volatile String sessionCode;

        Peer(WebSocketSession raw) {
            this.raw = raw;
            this.sender = new ConcurrentWebSocketSessionDecorator(
                    raw, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        }

        boolean authenticated() {
            return role != null;
        }
    }

    private static final class Room {

        volatile Peer agent;
        final Set<Peer> viewers = new CopyOnWriteArraySet<>();
    }

    public ScreenStreamWebSocketHandler(
            ObjectMapper objectMapper,
            AgentCredentialService agentCredentialService,
            JwtUtil jwtUtil,
            AccountDetailsService accountDetailsService,
            ScreenSharingWebSocketAccessService accessService,
            ScreenSharingSessionService sessionService) {

        this.objectMapper = objectMapper;
        this.agentCredentialService = agentCredentialService;
        this.jwtUtil = jwtUtil;
        this.accountDetailsService = accountDetailsService;
        this.accessService = accessService;
        this.sessionService = sessionService;
    }

    // ==================================================================
    // VÒNG ĐỜI KẾT NỐI
    // ==================================================================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        peers.put(session.getId(), new Peer(session));
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session, CloseStatus status) {

        Peer peer = peers.remove(session.getId());

        if (peer == null || !peer.authenticated()) {
            return;
        }

        Room room = rooms.get(peer.sessionCode);

        if (room == null) {
            return;
        }

        if ("VIEWER".equals(peer.role)) {

            room.viewers.remove(peer);

        } else if (room.agent == peer) {

            room.agent = null;

            // Agent rời đi: báo cho Viewer rồi đóng
            for (Peer viewer : room.viewers) {
                sendText(viewer, event("ENDED", "Agent đã ngắt kết nối."));
                closeQuietly(viewer, CloseStatus.NORMAL);
            }

            room.viewers.clear();

            // Agent đã rời: ghi nhận phiên kết thúc trong database để
            // Admin có thể tạo phiên mới (xem markAgentDisconnected)
            try {
                sessionService.markAgentDisconnected(peer.sessionCode);

            } catch (Exception e) {

                System.err.println("[SCREEN WS] Không cập nhật được trạng thái "
                        + "phiên " + peer.sessionCode + ": " + e.getMessage());
            }
        }

        if (room.agent == null && room.viewers.isEmpty()) {
            rooms.remove(peer.sessionCode, room);
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession session, Throwable exception) {

        System.err.println("[SCREEN WS] Lỗi truyền tải ("
                + session.getId() + "): " + exception.getMessage());

        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ==================================================================
    // TIN NHẮN
    // ==================================================================

    @Override
    protected void handleTextMessage(
            WebSocketSession session, TextMessage message) {

        Peer peer = peers.get(session.getId());

        if (peer == null) {
            return;
        }

        if (!peer.authenticated()) {
            authenticate(peer, message.getPayload());
            return;
        }

        // Chỉ Agent được gửi sự kiện; Viewer chỉ xem
        if (!"AGENT".equals(peer.role)) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(message.getPayload());

            String type = node.path("type").asText("");

            if (!FORWARDED_AGENT_EVENTS.contains(type)) {
                return;
            }

            Room room = rooms.get(peer.sessionCode);

            if (room == null) {
                return;
            }

            // Chỉ chuyển type + state + message, không chuyển nguyên JSON
            ObjectNode out = objectMapper.createObjectNode();
            out.put("type", type);
            out.put("state", node.path("state").asText(""));
            out.put("message", node.path("message").asText(""));

            String json = out.toString();

            for (Peer viewer : room.viewers) {
                sendText(viewer, json);
            }

        } catch (Exception e) {
            // JSON lỗi: bỏ qua
        }
    }

    @Override
    protected void handleBinaryMessage(
            WebSocketSession session, BinaryMessage message) {

        Peer peer = peers.get(session.getId());

        if (peer == null
                || !peer.authenticated()
                || !"AGENT".equals(peer.role)) {

            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        Room room = rooms.get(peer.sessionCode);

        if (room == null || room.viewers.isEmpty()) {
            return; // không ai xem: bỏ khung hình
        }

        ByteBuffer payload = message.getPayload();

        for (Peer viewer : room.viewers) {

            try {
                // Mỗi Viewer cần một bản sao có position riêng
                viewer.sender.sendMessage(
                        new BinaryMessage(payload.asReadOnlyBuffer()));

            } catch (Exception e) {

                // Viewer quá chậm hoặc đã chết: ngắt riêng Viewer đó
                room.viewers.remove(viewer);
                closeQuietly(viewer, CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    // ==================================================================
    // XÁC THỰC
    // ==================================================================

    private void authenticate(Peer peer, String payload) {

        try {
            JsonNode node = objectMapper.readTree(payload);

            if (!"AUTH".equals(node.path("type").asText(""))) {
                reject(peer, "Phải gửi AUTH trước.");
                return;
            }

            String role = node.path("role").asText("");
            String sessionCode = node.path("sessionCode").asText("").trim();

            if (sessionCode.isEmpty()) {
                reject(peer, "Thiếu sessionCode.");
                return;
            }

            Authentication auth;

            if ("AGENT".equals(role)) {
                auth = authenticateAgent(node);
            } else if ("VIEWER".equals(role)) {
                auth = authenticateViewer(node);
            } else {
                reject(peer, "Vai trò không hợp lệ.");
                return;
            }

            if (auth == null) {
                reject(peer, "Xác thực thất bại.");
                return;
            }

            // Phiên phải tồn tại, còn hiệu lực và thuộc về đúng Agent/Admin
            if (!accessService.hasAccess(auth, sessionCode)) {
                reject(peer, "Không có quyền truy cập phiên này.");
                return;
            }

            join(peer, role, sessionCode);

        } catch (Exception e) {
            reject(peer, "Tin nhắn AUTH không hợp lệ.");
        }
    }

    private Authentication authenticateAgent(JsonNode node) {

        String agentId = node.path("agentId").asText("");
        String credential = node.path("credential").asText("");

        if (agentId.isBlank() || credential.isBlank()) {
            return null;
        }

        if (!agentCredentialService.validateCredential(
                agentId, credential)) {
            return null;
        }

        // Tên "AGENT:<id>" khớp với cách ScreenSharingWebSocketAccessService
        // nhận diện Agent
        return new UsernamePasswordAuthenticationToken(
                "AGENT:" + agentId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
    }

    private Authentication authenticateViewer(JsonNode node) {

        String token = node.path("token").asText("").trim();

        if (token.isEmpty() || !jwtUtil.isTokenValid(token)) {
            return null;
        }

        String username = jwtUtil.extractUsername(token);

        if (username == null || username.isBlank()) {
            return null;
        }

        Account account = accountDetailsService.getAccountByUsername(username);

        if (account.getStatus() != AccountStatus.ACTIVE
                || account.getRole() == null
                || account.getRole().getRoleName() == null
                || !"ADMIN".equals(account.getRole().getRoleName().name())) {

            return null;
        }

        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    // ==================================================================
    // VÀO PHÒNG
    // ==================================================================

    private void join(Peer peer, String role, String sessionCode) {

        Room room = rooms.computeIfAbsent(sessionCode, k -> new Room());

        synchronized (room) {

            if ("AGENT".equals(role)) {

                Peer existing = room.agent;

                if (existing != null && existing.raw.isOpen()) {
                    reject(peer, "Phiên đã có một Agent kết nối.");
                    return;
                }

                room.agent = peer;

                // Viewer đã vào trước thì được báo Agent đã sẵn sàng
                for (Peer viewer : room.viewers) {
                    sendText(viewer, event("AGENT_CONNECTED", ""));
                }

            } else {
                room.viewers.add(peer);
            }

            peer.role = role;
            peer.sessionCode = sessionCode;
        }

        ObjectNode joined = objectMapper.createObjectNode();
        joined.put("type", "JOINED");
        joined.put("agentConnected", room.agent != null);

        sendText(peer, joined.toString());

        System.out.println("[SCREEN WS] " + role
                + " đã vào phiên " + sessionCode);
    }

    // ==================================================================
    // HÀM PHỤ
    // ==================================================================

    private String event(String type, String message) {

        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", type);
        node.put("message", message);

        return node.toString();
    }

    private void reject(Peer peer, String message) {

        sendText(peer, event("ERROR", message));
        closeQuietly(peer, CloseStatus.POLICY_VIOLATION);
    }

    private void sendText(Peer peer, String json) {

        try {
            peer.sender.sendMessage(new TextMessage(json));

        } catch (Exception e) {
            // Kết nối đã hỏng: afterConnectionClosed sẽ dọn dẹp
        }
    }

    private void closeQuietly(Peer peer, CloseStatus status) {
        closeQuietly(peer.raw, status);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {

        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception ignored) {
            // bỏ qua
        }
    }
}