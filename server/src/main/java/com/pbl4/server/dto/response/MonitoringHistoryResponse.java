package com.pbl4.server.dto.response;

import java.time.LocalDateTime;

public class MonitoringHistoryResponse {

    private Long snapshotId;
    private LocalDateTime collectedAt;

    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Double diskUsagePercent;

    private Double networkDownloadSpeed;
    private Double networkUploadSpeed;

    public MonitoringHistoryResponse() {
    }

    public MonitoringHistoryResponse(
            Long snapshotId,
            LocalDateTime collectedAt,
            Double cpuUsagePercent,
            Double memoryUsagePercent,
            Double diskUsagePercent,
            Double networkDownloadSpeed,
            Double networkUploadSpeed) {

        this.snapshotId = snapshotId;
        this.collectedAt = collectedAt;
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsagePercent = memoryUsagePercent;
        this.diskUsagePercent = diskUsagePercent;
        this.networkDownloadSpeed = networkDownloadSpeed;
        this.networkUploadSpeed = networkUploadSpeed;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }

    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }

    public Double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }

    public void setMemoryUsagePercent(Double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }

    public Double getDiskUsagePercent() {
        return diskUsagePercent;
    }

    public void setDiskUsagePercent(Double diskUsagePercent) {
        this.diskUsagePercent = diskUsagePercent;
    }

    public Double getNetworkDownloadSpeed() {
        return networkDownloadSpeed;
    }

    public void setNetworkDownloadSpeed(Double networkDownloadSpeed) {
        this.networkDownloadSpeed = networkDownloadSpeed;
    }

    public Double getNetworkUploadSpeed() {
        return networkUploadSpeed;
    }

    public void setNetworkUploadSpeed(Double networkUploadSpeed) {
        this.networkUploadSpeed = networkUploadSpeed;
    }
}