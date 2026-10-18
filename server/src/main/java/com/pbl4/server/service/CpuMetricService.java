package com.pbl4.server.service;

import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.repository.CpuMetricRepository;
import org.springframework.stereotype.Service;

@Service
public class CpuMetricService {

    private final CpuMetricRepository cpuMetricRepository;

    public CpuMetricService(
            CpuMetricRepository cpuMetricRepository) {

        this.cpuMetricRepository =
                cpuMetricRepository;
    }

    public CpuMetric saveCpuMetric(
            MonitoringSnapshot snapshot,
            Double usagePercent,
            Double loadAverage1m) {

        CpuMetric cpuMetric =
                new CpuMetric();

        cpuMetric.setSnapshot(snapshot);
        cpuMetric.setUsagePercent(usagePercent);
        cpuMetric.setLoadAverage1m(loadAverage1m);

        return cpuMetricRepository.save(cpuMetric);
    }
}