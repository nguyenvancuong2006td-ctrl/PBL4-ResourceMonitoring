package com.pbl4.server.repository;

import com.pbl4.server.model.DiskMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiskMetricRepository
        extends JpaRepository<DiskMetric, Long> {

    List<DiskMetric>
    findBySnapshotSnapshotId(Long snapshotId);

    List<DiskMetric>
    findBySnapshotSnapshotIdIn(Collection<Long> snapshotIds);

    List<DiskMetric>
    findByDiskDiskIdOrderBySnapshotCollectedAtDesc(Long diskId);
}   