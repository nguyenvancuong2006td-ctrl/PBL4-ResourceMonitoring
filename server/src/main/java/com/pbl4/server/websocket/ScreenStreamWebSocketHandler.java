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

import jakarta.annotation.PreDestroy;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Endpoint /ws/screen: nhận khung hình JPEG từ Agent và chuyển cho Admin.
 *
 * Giao thức (mọi kết nối phải gửi AUTH đầu tiên, trong vòng
 * AUTH_DEADLINE_SECONDS giây):
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
 *
 *   Điều khiển từ xa:
 *     Agent  -> STATUS với state CONTROL_READY / CONTROL_UNSUPPORTED
 *               (Server nhớ để báo cho Viewer vào sau)
 *     Viewer -> {"type":"INPUT","event":"MOUSE_MOVE|MOUSE_DOWN|MOUSE_UP|
 *                MOUSE_WHEEL|KEY_DOWN|KEY_UP|RELEASE_ALL", ...}
 *               Server kiểm tra, làm sạch rồi chuyển cho Agent
 *               (chỉ khi Agent đã báo CONTROL_READY).
 *
 * Khi Viewer cuối cùng rời phòng và không ai vào lại trong
 * NO_VIEWER_GRACE_SECONDS giây, Server tự yêu cầu Agent dừng chia sẻ.
 */
@Component
public class ScreenStreamWebSocketHandler extends AbstractWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 2 * 1024 * 1024;

    // Thời gian chờ trước khi tự dừng phiên không còn Viewer
    private static final long NO_VIEWER_GRACE_SECONDS = 30;

    // Thời gian chờ Admin mở trình xem sau khi Agent vào phòng.
    // Hết hạn mà vẫn chưa có Viewer nào thì Server yêu cầu Agent dừng
    // (tránh Agent thu màn hình vô thời hạn khi không ai xem).
    private static final long INITIAL_VIEWER_GRACE_SECONDS = 60;

    // Viewer có quá nhiều dữ liệu đang chờ gửi thì bỏ khung hình mới
    // (Viewer tự bắt kịp ở khung hình sau) thay vì ngắt kết nối.
    private static final int SLOW_VIEWER_BUFFER_BYTES = 512 * 1024;

    // Hạn chót để một kết nối mới gửi AUTH
    private static final long AUTH_DEADLINE_SECONDS = 10;

    private static final Set<String> FORWARDED_AGENT_EVENTS =
            Set.of("STATUS", "ERROR");

    // Các sự kiện điều khiển mà Viewer được phép gửi
    private static final Set<String> INPUT_EVENTS = Set.of(
            "MOUSE_MOVE", "MOUSE_DOWN", "MOUSE_UP", "MOUSE_WHEEL",
            "KEY_DOWN", "KEY_UP", "RELEASE_ALL");

    // KeyboardEvent.code hợp lệ: chữ và số, tối đa 32 ký tự
    private static final java.util.regex.Pattern KEY_CODE_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9]{1,32}");

    // Agent có quá nhiều dữ liệu chờ gửi thì bỏ MOUSE_MOVE
    // (phím và nút chuột vẫn được gửi để không bị kẹt phím)
    private static final int SLOW_AGENT_BUFFER_BYTES = 64 * 1024;

    private static final int MAX_WHEEL_NOTCHES = 20;

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

    // Bộ hẹn giờ: hạn chót AUTH và tự dừng phiên khi hết Viewer
    private final ScheduledExecutorService timer =
            Executors.newSingleThreadScheduledExecutor(r -> {

                Thread t = new Thread(r, "screen-ws-timer");
                t.setDaemon(true);

                return t;
            });

    private static final class Peer {

        final WebSocketSession raw;
        final ConcurrentWebSocketSessionDecorator sender; // gửi an toàn đa luồng

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

        // Khung hình JPEG mới nhất, gửi ngay cho Viewer vào sau
        // (màn hình đứng yên thì Agent không gửi khung hình mới)
        volatile byte[] lastFrame;

        // Trạng thái điều khiển Agent đã báo gần nhất (JSON STATUS gửi cho
        // Viewer vào sau) và Agent có đang cho phép điều khiển hay không
        volatile String controlState;
        volatile boolean controlReady;

        // Bộ hẹn giờ tự dừng phiên khi không có Viewer. Mỗi phòng chỉ có
        // tối đa một; hẹn lại thì hủy cái cũ, Viewer vào thì hủy.
        volatile ScheduledFuture<?> stopTimer;
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

    @PreDestroy
    public void shutdownTimer() {
        timer.shutdownNow();
    }

    // ==================================================================
    // VÒNG ĐỜI KẾT NỐI
    // ==================================================================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        Peer peer = new Peer(session);
        peers.put(session.getId(), peer);

        // Kết nối không gửi AUTH đúng hạn thì đóng
        timer.schedule(() -> {

            if (!peer.authenticated()) {
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            }

        }, AUTH_DEADLINE_SECONDS, TimeUnit.SECONDS);
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

            // Hết Viewer: nhả mọi phím / nút chuột Agent đang giữ để
            // không bị kẹt phím nếu Admin đóng trình duyệt giữa chừng
            if (room.viewers.isEmpty()) {
                releaseAllOnAgent(room);
            }

            // Hết Viewer: chờ một lúc (Admin có thể F5 trang), vẫn không
            // ai xem thì yêu cầu Agent dừng thu màn hình
            if (room.viewers.isEmpty() && room.agent != null) {

                scheduleStopIfNoViewer(
                        room, peer.sessionCode, NO_VIEWER_GRACE_SECONDS);
            }

        } else if (room.agent == peer) {

            room.agent = null;
            room.lastFrame = null;
            room.controlState = null;
            room.controlReady = false;

            cancelStopTimer(room);

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

        // Đồng bộ với join() để không xóa phòng đúng lúc có người vừa vào
        synchronized (room) {

            if (room.agent == null && room.viewers.isEmpty()) {
                cancelStopTimer(room);
                rooms.remove(peer.sessionCode, room);
            }
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

        // Viewer chỉ được gửi sự kiện điều khiển (INPUT)
        if ("VIEWER".equals(peer.role)) {
            handleViewerMessage(peer, message.getPayload());
            return;
        }

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

            // Nhớ trạng thái điều khiển để báo cho Viewer vào sau
            String state = out.path("state").asText("");

            if (state.startsWith("CONTROL_")) {
                room.controlState = json;
                room.controlReady = "CONTROL_READY".equals(state);
            }

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

        if (room == null) {
            return;
        }

        // Sao chép ra mảng riêng: dùng chung cho mọi Viewer và để lưu
        // làm khung hình cuối (bộ đệm của tin nhắn có thể bị tái sử dụng)
        ByteBuffer payload = message.getPayload();

        byte[] frame = new byte[payload.remaining()];
        payload.duplicate().get(frame);

        room.lastFrame = frame;

        for (Peer viewer : room.viewers) {

            // Viewer chậm: bỏ khung hình này, không ngắt kết nối
            if (viewer.sender.getBufferSize() > SLOW_VIEWER_BUFFER_BYTES) {
                continue;
            }

            try {
                viewer.sender.sendMessage(new BinaryMessage(frame));

            } catch (Exception e) {

                // Viewer đã chết hoặc quá giới hạn: ngắt riêng Viewer đó
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

        Room room;

        while (true) {

            room = rooms.computeIfAbsent(sessionCode, k -> new Room());

            synchronized (room) {

                // Phòng vừa bị dọn bởi afterConnectionClosed: lấy phòng mới
                if (rooms.get(sessionCode) != room) {
                    continue;
                }

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

                    // Có người xem: không tự dừng phiên nữa
                    cancelStopTimer(room);
                }

                peer.role = role;
                peer.sessionCode = sessionCode;

                break;
            }
        }

        ObjectNode joined = objectMapper.createObjectNode();
        joined.put("type", "JOINED");
        joined.put("agentConnected", room.agent != null);

        sendText(peer, joined.toString());

        if ("VIEWER".equals(role)) {

            // Màn hình đứng yên thì không có khung hình mới:
            // gửi ngay khung hình cuối để Viewer không bị đen
            sendLastFrame(peer, room);

            // Báo ngay Agent có cho điều khiển hay không
            String controlState = room.controlState;

            if (controlState != null) {
                sendText(peer, controlState);
            }

        } else if (room.viewers.isEmpty()) {

            // Agent vào phòng mà chưa có ai xem: hẹn giờ tự dừng
            scheduleStopIfNoViewer(
                    room, sessionCode, INITIAL_VIEWER_GRACE_SECONDS);
        }

        System.out.println("[SCREEN WS] " + role
                + " đã vào phiên " + sessionCode);
    }

    // ==================================================================
    // ĐIỀU KHIỂN TỪ XA: Viewer -> Server -> Agent
    // ==================================================================

    /**
     * Nhận tin nhắn của Viewer. Chỉ xử lý INPUT: kiểm tra từng trường rồi
     * dựng JSON MỚI gửi cho Agent (không chuyển nguyên văn dữ liệu của
     * trình duyệt).
     */
    private void handleViewerMessage(Peer viewer, String payload) {

        try {
            JsonNode node = objectMapper.readTree(payload);

            if (!"INPUT".equals(node.path("type").asText(""))) {
                return;
            }

            String event = node.path("event").asText("");

            if (!INPUT_EVENTS.contains(event)) {
                return;
            }

            Room room = rooms.get(viewer.sessionCode);

            if (room == null || !room.controlReady) {
                return;
            }

            Peer agent = room.agent;

            if (agent == null || !agent.raw.isOpen()) {
                return;
            }

            if ("MOUSE_MOVE".equals(event)
                    && agent.sender.getBufferSize()
                            > SLOW_AGENT_BUFFER_BYTES) {
                return;
            }

            ObjectNode out = objectMapper.createObjectNode();
            out.put("type", "INPUT");
            out.put("event", event);

            switch (event) {

                case "MOUSE_MOVE":
                    out.put("x", clamp01(node.path("x").asDouble(0)));
                    out.put("y", clamp01(node.path("y").asDouble(0)));
                    break;

                case "MOUSE_DOWN":
                case "MOUSE_UP":
                    out.put("x", clamp01(node.path("x").asDouble(0)));
                    out.put("y", clamp01(node.path("y").asDouble(0)));
                    out.put("button", Math.max(0,
                            Math.min(2, node.path("button").asInt(0))));
                    break;

                case "MOUSE_WHEEL":
                    out.put("delta", Math.max(-MAX_WHEEL_NOTCHES,
                            Math.min(MAX_WHEEL_NOTCHES,
                                    node.path("delta").asInt(0))));
                    break;

                case "KEY_DOWN":
                case "KEY_UP":
                    String code = node.path("code").asText("");

                    if (!KEY_CODE_PATTERN.matcher(code).matches()) {
                        return;
                    }

                    out.put("code", code);
                    break;

                default:
                    // RELEASE_ALL: không có tham số
            }

            sendText(agent, out.toString());

        } catch (Exception e) {
            // JSON lỗi: bỏ qua
        }
    }

    /**
     * Yêu cầu Agent nhả mọi phím / nút chuột đang giữ.
     */
    private void releaseAllOnAgent(Room room) {

        Peer agent = room.agent;

        if (agent == null || !room.controlReady) {
            return;
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("type", "INPUT");
        out.put("event", "RELEASE_ALL");

        sendText(agent, out.toString());
    }

    private static double clamp01(double value) {

        if (Double.isNaN(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    // ==================================================================
    // HÀM PHỤ
    // ==================================================================

    private void sendLastFrame(Peer viewer, Room room) {

        byte[] frame = room.lastFrame;

        if (frame == null) {
            return;
        }

        try {
            viewer.sender.sendMessage(new BinaryMessage(frame));

        } catch (Exception e) {
            // Kết nối đã hỏng: afterConnectionClosed sẽ dọn dẹp
        }
    }

    /**
     * Đóng các phòng chia sẻ màn hình của những phiên cho trước.
     *
     * Dùng khi Agent / Client bị xóa khỏi hệ thống: báo cho Viewer rồi
     * đóng kết nối của Viewer lẫn Agent. Phòng được gỡ trước khi đóng nên
     * afterConnectionClosed không còn tìm thấy phòng và sẽ không cố cập nhật
     * trạng thái phiên (bản ghi phiên đã bị xóa khỏi database).
     */
    public void closeSessions(Collection<String> sessionCodes) {

        if (sessionCodes == null || sessionCodes.isEmpty()) {
            return;
        }

        for (String code : sessionCodes) {

            Room room = rooms.remove(code);

            if (room == null) {
                continue;
            }

            cancelStopTimer(room);

            Peer agent = room.agent;

            room.agent = null;
            room.lastFrame = null;
            room.controlState = null;
            room.controlReady = false;

            for (Peer viewer : room.viewers) {
                sendText(viewer, event(
                        "ENDED", "Agent đã bị xóa khỏi hệ thống."));
                closeQuietly(viewer, CloseStatus.NORMAL);
            }

            room.viewers.clear();

            if (agent != null) {
                closeQuietly(agent, CloseStatus.NORMAL);
            }
        }
    }

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

    /**
     * Hẹn giờ tự dừng phiên nếu hết hạn mà vẫn không có Viewer.
     *
     * Mỗi phòng chỉ giữ MỘT timer: hẹn lại sẽ hủy timer cũ, nên thời gian
     * chờ luôn tính từ lần Viewer rời gần nhất (timer cũ không thể nổ sớm
     * và dừng phiên khi Viewer mới vừa rời).
     */
    private void scheduleStopIfNoViewer(
            Room room, String code, long delaySeconds) {

        synchronized (room) {

            cancelStopTimer(room);

            room.stopTimer = timer.schedule(
                    () -> stopIfNoViewer(code),
                    delaySeconds,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Hủy timer tự dừng của phòng (nếu có).
     */
    private void cancelStopTimer(Room room) {

        synchronized (room) {

            ScheduledFuture<?> pending = room.stopTimer;

            if (pending != null) {
                pending.cancel(false);
                room.stopTimer = null;
            }
        }
    }

    /**
     * Chạy khi hết thời gian chờ kể từ lúc Viewer cuối cùng rời phòng
     * (hoặc Agent vào phòng mà chưa có Viewer).
     * Nếu vẫn không ai xem thì yêu cầu Agent dừng chia sẻ.
     */
    private void stopIfNoViewer(String code) {

        Room r = rooms.get(code);

        if (r == null) {
            return;
        }

        // Kiểm tra cùng khóa với join() để không đọc giữa lúc có người vào
        synchronized (r) {

            // Có người xem lại, hoặc Agent đã rời: không làm gì
            if (r.agent == null || !r.viewers.isEmpty()) {
                return;
            }
        }

        try {
            sessionService.stopSessionBySystem(code);

        } catch (Exception e) {
            System.err.println("[SCREEN WS] Không tự dừng được phiên "
                    + code + ": " + e.getMessage());
        }
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