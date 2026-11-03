package com.pbl4.server.repository;

import com.pbl4.server.model.MemoryMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemoryMetricRepository
        extends JpaRepository<MemoryMetric, Long> {

    /**
     * Tìm bản ghi RAM theo đối tượng snapshot.
     */
    Optional<MemoryMetric> findBySnapshot(
            MonitoringSnapshot snapshot
    );

    /**
     * Tìm bản ghi RAM theo ID snapshot.
     */
    Optional<MemoryMetric> findBySnapshotSnapshotId(
            Long snapshotId
    );

    /**
     * Lấy danh sách RAM theo ID snapshot.
     * Giữ phương thức này nếu Service đang sử dụng List.
     */
    List<MemoryMetric> findAllBySnapshotSnapshotId(
            Long snapshotId
    );

    List<MemoryMetric> findBySnapshotSnapshotIdIn(
            Collection<Long> snapshotIds
    );
}