package com.pbl4.server.repository;

import com.pbl4.server.model.CpuMetric;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CpuMetricRepository
        extends JpaRepository<CpuMetric, Long> {
}