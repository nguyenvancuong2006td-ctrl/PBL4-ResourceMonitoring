package com.pbl4.server.dto.response;

import com.pbl4.server.enums.ScreenSharingStatus;

public record ScreenSharingSessionResponse(
        Long sessionId,
        String sessionCode,
        String clientId,
        String agentId,
        ScreenSharingStatus status,
        Long commandId,
        String message
) {
}