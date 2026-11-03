package com.pbl4.server.repository;

import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.ScreenSharingSession;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    List<ScreenSharingSession> findByStatusIn(
        Collection<ScreenSharingStatus> statuses
    );

    // Lịch sử các phiên chia sẻ (có phân trang); tham số null thì không lọc
    @EntityGraph(attributePaths = {"account", "client", "agent"})
    @Query("""
            SELECT s
            FROM ScreenSharingSession s
            WHERE (:clientId IS NULL OR s.client.clientId = :clientId)
              AND (:status IS NULL OR s.status = :status)
            """)
    Page<ScreenSharingSession> search(
            @Param("clientId") String clientId,
            @Param("status") ScreenSharingStatus status,
            Pageable pageable
    );
}