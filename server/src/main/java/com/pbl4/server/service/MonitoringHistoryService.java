
package com.pbl4.server.service;

import com.pbl4.server.dto.response.MonitoringHistoryResponse;
import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.DiskMetric;
import com.pbl4.server.model.MemoryMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkMetric;
import com.pbl4.server.repository.CpuMetricRepository;
import com.pbl4.server.repository.DiskMetricRepository;
import com.pbl4.server.repository.MemoryMetricRepository;
import com.pbl4.server.repository.MonitoringSnapshotRepository;
import com.pbl4.server.repository.NetworkMetricRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MonitoringHistoryService {

    private final MonitoringSnapshotRepository snapshotRepository;
    private final CpuMetricRepository cpuMetricRepository;
    private final MemoryMetricRepository memoryMetricRepository;
    private final DiskMetricRepository diskMetricRepository;
    private final NetworkMetricRepository networkMetricRepository;

    public MonitoringHistoryService(
            MonitoringSnapshotRepository snapshotRepository,
            CpuMetricRepository cpuMetricRepository,
            MemoryMetricRepository memoryMetricRepository,
            DiskMetricRepository diskMetricRepository,
            NetworkMetricRepository networkMetricRepository) {

        this.snapshotRepository = snapshotRepository;
        this.cpuMetricRepository = cpuMetricRepository;
        this.memoryMetricRepository = memoryMetricRepository;
        this.diskMetricRepository = diskMetricRepository;
        this.networkMetricRepository = networkMetricRepository;
    }

    @Transactional(readOnly = true)
    public List<MonitoringHistoryResponse> getHistory(
            String clientId,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống");
        }

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Thời gian không được để trống");
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc");
        }

        List<MonitoringSnapshot> snapshots =
                snapshotRepository
                        .findByClientClientIdAndCollectedAtBetweenOrderByCollectedAtDesc(
                                clientId, startTime, endTime);

        List<MonitoringHistoryResponse> result =
                new ArrayList<>();

        for (MonitoringSnapshot snapshot : snapshots) {

            Long snapshotId = snapshot.getSnapshotId();

            MonitoringHistoryResponse item =
                    new MonitoringHistoryResponse();

            item.setSnapshotId(snapshotId);
            item.setCollectedAt(snapshot.getCollectedAt());

            // CPU: mỗi snapshot có tối đa một CPU metric.
            CpuMetric cpu =
                    cpuMetricRepository
                            .findBySnapshotSnapshotId(snapshotId)
                            .orElse(null);

            if (cpu != null) {
                item.setCpuUsagePercent(cpu.getUsagePercent());
            }

            // RAM: mỗi snapshot có tối đa một Memory metric.
            MemoryMetric memory =
                    memoryMetricRepository
                            .findBySnapshotSnapshotId(snapshotId)
                            .orElse(null);

            if (memory != null) {
                item.setMemoryUsagePercent(memory.getUsagePercent());
            }

            // Disk: lấy trung bình phần trăm sử dụng
            // của các phân vùng thuộc snapshot.
            List<DiskMetric> disks =
                    diskMetricRepository
                            .findBySnapshotSnapshotId(snapshotId);

            double diskTotal = 0.0;
            int diskCount = 0;

            for (DiskMetric disk : disks) {
                if (disk.getUsagePercent() != null) {
                    diskTotal += disk.getUsagePercent();
                    diskCount++;
                }
            }

            if (diskCount > 0) {
                item.setDiskUsagePercent(
                        diskTotal / diskCount);
            }

            // Network: cộng tốc độ của các interface.
            List<NetworkMetric> networks =
                    networkMetricRepository
                            .findBySnapshotSnapshotId(snapshotId);

            double downloadTotal = 0.0;
            double uploadTotal = 0.0;
            boolean hasDownload = false;
            boolean hasUpload = false;

            for (NetworkMetric network : networks) {
                if (network.getDownloadSpeed() != null) {
                    downloadTotal += network.getDownloadSpeed();
                    hasDownload = true;
                }

                if (network.getUploadSpeed() != null) {
                    uploadTotal += network.getUploadSpeed();
                    hasUpload = true;
                }
            }

            if (hasDownload) {
                item.setNetworkDownloadSpeed(downloadTotal);
            }

            if (hasUpload) {
                item.setNetworkUploadSpeed(uploadTotal);
            }

            result.add(item);
        }

        return result;
    }
}
