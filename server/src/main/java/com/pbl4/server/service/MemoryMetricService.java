package com.pbl4.server.service;

import com.pbl4.server.model.MemoryMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.repository.MemoryMetricRepository;
import org.springframework.stereotype.Service;

@Service
public class MemoryMetricService {

    private final MemoryMetricRepository memoryMetricRepository;

    public MemoryMetricService(
            MemoryMetricRepository memoryMetricRepository) {

        this.memoryMetricRepository =
                memoryMetricRepository;
    }

    public MemoryMetric saveMemoryMetric(
            MonitoringSnapshot snapshot,
            Long totalBytes,
            Long usedBytes,
            Long availableBytes,
            Double usagePercent) {

        MemoryMetric memoryMetric =
                new MemoryMetric();

        memoryMetric.setSnapshot(snapshot);
        memoryMetric.setTotalBytes(totalBytes);
        memoryMetric.setUsedBytes(usedBytes);
        memoryMetric.setAvailableBytes(availableBytes);
        memoryMetric.setUsagePercent(usagePercent);

        return memoryMetricRepository.save(memoryMetric);
    }
}