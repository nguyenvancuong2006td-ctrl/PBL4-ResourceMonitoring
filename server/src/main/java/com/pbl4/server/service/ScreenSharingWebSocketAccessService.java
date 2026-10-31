package com.pbl4.server.service;

import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.ScreenSharingSession;
import com.pbl4.server.repository.ScreenSharingSessionRepository;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
public class ScreenSharingWebSocketAccessService {

    private final ScreenSharingSessionRepository sessionRepository;
    private final AccountDetailsService accountDetailsService;

    private static final Set<ScreenSharingStatus> ALLOWED_STATUSES =
            EnumSet.of(
                    ScreenSharingStatus.REQUESTED,
                    ScreenSharingStatus.STARTING,
                    ScreenSharingStatus.ACTIVE
            );

    public ScreenSharingWebSocketAccessService(
            ScreenSharingSessionRepository sessionRepository,
            AccountDetailsService accountDetailsService) {

        this.sessionRepository = sessionRepository;
        this.accountDetailsService = accountDetailsService;
    }

    /**
     * Kiểm tra người dùng có quyền truy cập phiên chia sẻ màn hình hay không.
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(
            Authentication authentication,
            String sessionCode) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || sessionCode == null
                || sessionCode.isBlank()) {

            return false;
        }

        // Tìm phiên chia sẻ màn hình
        ScreenSharingSession session =
                sessionRepository.findBySessionCode(sessionCode)
                        .orElse(null);

        if (session == null
                || !ALLOWED_STATUSES.contains(session.getStatus())) {

            return false;
        }

        String principalName = authentication.getName();

        if (principalName == null || principalName.isBlank()) {
            return false;
        }

        // Trường hợp Agent
        if (principalName.startsWith("AGENT:")) {

            String agentId =
                    principalName.substring("AGENT:".length());

            return session.getAgent() != null
                    && agentId.equals(
                            session.getAgent().getAgentId()
                    );
        }

        // Trường hợp Admin
        try {
            Account account =
                    accountDetailsService.getAccountByUsername(
                            principalName
                    );

            return account != null
                    && session.getAccount() != null
                    && account.getAccountId().equals(
                            session.getAccount().getAccountId()
                    );

        } catch (Exception e) {
            return false;
        }
    }
}