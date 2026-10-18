package com.pbl4.server.repository;

import com.pbl4.server.model.MemoryMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryMetricRepository
        extends JpaRepository<MemoryMetric, Long> {
}