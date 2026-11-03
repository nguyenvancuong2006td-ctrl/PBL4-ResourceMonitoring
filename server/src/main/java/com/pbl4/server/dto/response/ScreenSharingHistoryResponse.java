package com.pbl4.server.dto.response;

import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.ScreenSharingSession;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Một dòng trong lịch sử các phiên chia sẻ màn hình.
 */
public record ScreenSharingHistoryResponse(
        Long sessionId,
        String sessionCode,
        String clientId,
        String hostname,
        String agentId,
        String requestedBy,
        ScreenSharingStatus status,
        LocalDateTime requestedAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        String endReason,
        String errorMessage
) {

    public static ScreenSharingHistoryResponse from(
            ScreenSharingSession s) {

        Long duration = null;

        if (s.getStartedAt() != null && s.getEndedAt() != null) {
            duration = Math.max(
                    0,
                    Duration.between(s.getStartedAt(), s.getEndedAt())
                            .getSeconds()
            );
        }

        return new ScreenSharingHistoryResponse(
                s.getSessionId(),
                s.getSessionCode(),
                s.getClient() != null ? s.getClient().getClientId() : null,
                s.getClient() != null ? s.getClient().getHostname() : null,
                s.getAgent() != null ? s.getAgent().getAgentId() : null,
                s.getAccount() != null ? s.getAccount().getUsername() : null,
                s.getStatus(),
                s.getRequestedAt(),
                s.getStartedAt(),
                s.getEndedAt(),
                duration,
                s.getEndReason(),
                s.getErrorMessage()
        );
    }
}