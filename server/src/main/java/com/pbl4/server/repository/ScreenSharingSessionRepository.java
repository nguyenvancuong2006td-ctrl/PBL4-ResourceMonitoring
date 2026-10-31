package com.pbl4.server.repository;

import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.ScreenSharingSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScreenSharingSessionRepository
        extends JpaRepository<ScreenSharingSession, Long> {

    Optional<ScreenSharingSession> findBySessionCode(
            String sessionCode
    );

    List<ScreenSharingSession>
    findByAccountAccountIdAndStatusInOrderByRequestedAtDesc(
            Long accountId,
            Collection<ScreenSharingStatus> statuses
    );

    List<ScreenSharingSession>
    findByClientClientIdAndStatusIn(
            String clientId,
            Collection<ScreenSharingStatus> statuses
    );

    Optional<ScreenSharingSession>
    findFirstByAccountAccountIdAndStatusInOrderByRequestedAtDesc(
            Long accountId,
            Collection<ScreenSharingStatus> statuses
    );

    Optional<ScreenSharingSession>
    findFirstByClientClientIdAndStatusInOrderByRequestedAtDesc(
            String clientId,
            Collection<ScreenSharingStatus> statuses
    );

    boolean existsByAccountAccountIdAndStatusIn(
        Long accountId,
        Collection<ScreenSharingStatus> statuses
);

    boolean existsByClientClientIdAndStatusIn(
        String clientId,
        Collection<ScreenSharingStatus> statuses
);
}