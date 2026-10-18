package com.pbl4.server.service;

import com.pbl4.server.model.DiskInfo;
import com.pbl4.server.model.DiskMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.repository.DiskMetricRepository;
import org.springframework.stereotype.Service;

@Service
public class DiskMetricService {

    private final DiskMetricRepository diskMetricRepository;

    public DiskMetricService(
            DiskMetricRepository diskMetricRepository) {

        this.diskMetricRepository =
                diskMetricRepository;
    }

    public DiskMetric saveDiskMetric(
            MonitoringSnapshot snapshot,
            DiskInfo disk,
            long totalBytes,
            long usedBytes,
            long freeBytes,
            double usagePercent) {

        DiskMetric metric =
                new DiskMetric();

        metric.setSnapshot(snapshot);
        metric.setDisk(disk);
        metric.setTotalBytes(totalBytes);
        metric.setUsedBytes(usedBytes);
        metric.setFreeBytes(freeBytes);
        metric.setUsagePercent(usagePercent);

        return diskMetricRepository.save(metric);
    }
}