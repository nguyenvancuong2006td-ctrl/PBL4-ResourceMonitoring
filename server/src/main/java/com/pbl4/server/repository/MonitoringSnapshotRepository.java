package com.pbl4.server.repository;

import com.pbl4.server.model.MonitoringSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MonitoringSnapshotRepository
        extends JpaRepository<MonitoringSnapshot, Long> {

    List<MonitoringSnapshot>
    findByClientClientIdOrderByCollectedAtDesc(String clientId);

    // Snapshot mới nhất (ID lớn nhất) của từng Client trong một câu truy vấn.
    // clientIds không được rỗng.
    @Query("""
        SELECT s
        FROM MonitoringSnapshot s
        WHERE s.snapshotId IN (
            SELECT MAX(s2.snapshotId)
            FROM MonitoringSnapshot s2
            WHERE s2.client.clientId IN :clientIds
            GROUP BY s2.client.clientId
        )
    """)
    List<MonitoringSnapshot> findLatestForClients(
            @Param("clientIds") Collection<String> clientIds
    );

    Optional<MonitoringSnapshot>
    findTopByClientClientIdOrderByCollectedAtDesc(String clientId);

    List<MonitoringSnapshot>
    findByClientClientIdAndCollectedAtBetweenOrderByCollectedAtDesc(
            String clientId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );
}