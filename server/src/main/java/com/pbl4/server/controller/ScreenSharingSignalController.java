package com.pbl4.server.controller;

import com.pbl4.server.service.ScreenSharingWebSocketAccessService;

import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Controller
public class ScreenSharingSignalController {

    private final SimpMessagingTemplate messagingTemplate;

    private final ScreenSharingWebSocketAccessService accessService;

    // Các loại tín hiệu WebRTC được phép
    private static final Set<String> ALLOWED_SIGNAL_TYPES = Set.of(
            "OFFER",
            "ANSWER",
            "ICE_CANDIDATE"
    );

    public ScreenSharingSignalController(
            SimpMessagingTemplate messagingTemplate,
            ScreenSharingWebSocketAccessService accessService) {

        this.messagingTemplate = messagingTemplate;
        this.accessService = accessService;
    }

    /**
     * Nhận và chuyển tiếp tín hiệu WebRTC.
     *
     * Client gửi:
     * /app/screen-sharing/{sessionCode}/signal
     *
     * Server chuyển tiếp:
     * /topic/screen-sharing/{sessionCode}
     */
    @MessageMapping("/screen-sharing/{sessionCode}/signal")
    public void relaySignal(
            @DestinationVariable String sessionCode,
            @Payload Map<String, Object> signal,
            Principal principal) {

        // =========================================
        // 1. KIỂM TRA XÁC THỰC
        // =========================================

        if (!(principal instanceof Authentication authentication)) {
            throw new MessageDeliveryException(
                    "WebSocket chưa được xác thực."
            );
        }

        // =========================================
        // 2. KIỂM TRA SESSION CODE
        // =========================================

        if (sessionCode == null || sessionCode.isBlank()
                || sessionCode.contains("/")) {

            throw new MessageDeliveryException(
                    "Session code không hợp lệ."
            );
        }

        // =========================================
        // 3. KIỂM TRA QUYỀN TRUY CẬP PHIÊN
        // =========================================

        if (!accessService.hasAccess(authentication, sessionCode)) {

            throw new MessageDeliveryException(
                    "Bạn không có quyền gửi tín hiệu vào phiên này."
            );
        }

        // =========================================
        // 4. KIỂM TRA DỮ LIỆU TÍN HIỆU
        // =========================================

        if (signal == null || signal.isEmpty()) {

            throw new MessageDeliveryException(
                    "Nội dung tín hiệu không được để trống."
            );
        }

        Object typeObject = signal.get("type");

        if (!(typeObject instanceof String type)
                || !ALLOWED_SIGNAL_TYPES.contains(type)) {

            throw new MessageDeliveryException(
                    "Loại tín hiệu WebRTC không hợp lệ."
            );
        }

        Object data = signal.get("data");

        if (data == null) {

            throw new MessageDeliveryException(
                    "Dữ liệu tín hiệu không được để trống."
            );
        }

        // =========================================
        // 5. XÁC ĐỊNH NGƯỜI GỬI
        // =========================================

        // Không lấy senderType từ dữ liệu Client gửi lên.
        // Phải xác định dựa trên Principal đã xác thực.

        String senderName = authentication.getName();

        if (senderName == null || senderName.isBlank()) {

            throw new MessageDeliveryException(
                    "Không xác định được người gửi tín hiệu."
            );
        }

        String senderType;

        if (senderName.startsWith("AGENT:")) {

            senderType = "AGENT";

        } else if (authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()))) {

            senderType = "ADMIN";

        } else {

            throw new MessageDeliveryException(
                    "Vai trò không được phép gửi tín hiệu."
            );
        }

        // =========================================
        // 6. TẠO DỮ LIỆU CHUYỂN TIẾP
        // =========================================

        Map<String, Object> forwardedSignal =
                new LinkedHashMap<>();

        forwardedSignal.put("type", type);
        forwardedSignal.put("data", data);
        forwardedSignal.put("senderType", senderType);
        forwardedSignal.put("senderId", senderName);
        forwardedSignal.put("sessionCode", sessionCode);

        // =========================================
        // 7. CHUYỂN TIẾP TÍN HIỆU ĐẾN TOPIC
        // =========================================

        String destination =
                "/topic/screen-sharing/" + sessionCode;

        // Ép kiểu Object để tránh Spring overload ambiguity
        messagingTemplate.convertAndSend(
                destination,
                (Object) forwardedSignal
        );

        // =========================================
        // 8. GHI LOG
        // =========================================

        System.out.println(
                "[SCREEN SHARING] Đã chuyển tiếp tín hiệu "
                        + type
                        + " từ "
                        + senderType
                        + " trong phiên "
                        + sessionCode
        );
    }
}