package com.pbl4.server.repository;

import com.pbl4.server.model.NetworkMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NetworkMetricRepository
        extends JpaRepository<NetworkMetric, Long> {

    List<NetworkMetric>
    findBySnapshotSnapshotId(Long snapshotId);

    List<NetworkMetric>
    findByNetworkInterfaceInterfaceIdOrderBySnapshotCollectedAtDesc(
            Long interfaceId
    );
}