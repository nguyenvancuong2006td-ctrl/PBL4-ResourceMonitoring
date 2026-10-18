package com.pbl4.server.repository;

import com.pbl4.server.model.ServerMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerMetricRepository
        extends JpaRepository<ServerMetric, Long> {

    List<ServerMetric>
    findByServerServerIdOrderByCollectedAtDesc(Long serverId);
}