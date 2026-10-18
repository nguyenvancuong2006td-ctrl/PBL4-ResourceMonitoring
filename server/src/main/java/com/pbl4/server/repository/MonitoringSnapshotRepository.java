package com.pbl4.server.repository;

import com.pbl4.server.model.MonitoringSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoringSnapshotRepository
        extends JpaRepository<MonitoringSnapshot, Long> {

    List<MonitoringSnapshot>
    findByClientClientIdOrderByCollectedAtDesc(String clientId);

    Optional<MonitoringSnapshot>
    findTopByClientClientIdOrderByCollectedAtDesc(String clientId);
}