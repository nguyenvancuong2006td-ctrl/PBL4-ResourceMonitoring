package com.pbl4.server.repository;

import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CpuMetricRepository
        extends JpaRepository<CpuMetric, Long> {

    Optional<CpuMetric> findBySnapshot(
            MonitoringSnapshot snapshot
    );

    Optional<CpuMetric> findBySnapshotSnapshotId(
            Long snapshotId
    );

    List<CpuMetric> findBySnapshotSnapshotIdIn(
            Collection<Long> snapshotIds
    );
}