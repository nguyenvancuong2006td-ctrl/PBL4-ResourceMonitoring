package com.pbl4.server.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "server_metrics")
public class ServerMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @ManyToOne
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "ram_total_bytes")
    private Long ramTotalBytes;

    @Column(name = "ram_used_bytes")
    private Long ramUsedBytes;

    @Column(name = "ram_usage_percent")
    private Double ramUsagePercent;

    @Column(name = "disk_total_bytes")
    private Long diskTotalBytes;

    @Column(name = "disk_used_bytes")
    private Long diskUsedBytes;

    @Column(name = "disk_usage_percent")
    private Double diskUsagePercent;

    @Column(name = "received_bytes")
    private Long receivedBytes;

    @Column(name = "transmitted_bytes")
    private Long transmittedBytes;

    @Column(name = "download_speed")
    private Double downloadSpeed;

    @Column(name = "upload_speed")
    private Double uploadSpeed;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    public ServerMetric() {
    }

    public Long getMetricId() {
        return metricId;
    }

    public void setMetricId(Long metricId) {
        this.metricId = metricId;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Long getRamTotalBytes() {
        return ramTotalBytes;
    }

    public void setRamTotalBytes(Long ramTotalBytes) {
        this.ramTotalBytes = ramTotalBytes;
    }

    public Long getRamUsedBytes() {
        return ramUsedBytes;
    }

    public void setRamUsedBytes(Long ramUsedBytes) {
        this.ramUsedBytes = ramUsedBytes;
    }

    public Double getRamUsagePercent() {
        return ramUsagePercent;
    }

    public void setRamUsagePercent(Double ramUsagePercent) {
        this.ramUsagePercent = ramUsagePercent;
    }

    public Long getDiskTotalBytes() {
        return diskTotalBytes;
    }

    public void setDiskTotalBytes(Long diskTotalBytes) {
        this.diskTotalBytes = diskTotalBytes;
    }

    public Long getDiskUsedBytes() {
        return diskUsedBytes;
    }

    public void setDiskUsedBytes(Long diskUsedBytes) {
        this.diskUsedBytes = diskUsedBytes;
    }

    public Double getDiskUsagePercent() {
        return diskUsagePercent;
    }

    public void setDiskUsagePercent(Double diskUsagePercent) {
        this.diskUsagePercent = diskUsagePercent;
    }

    public Long getReceivedBytes() {
        return receivedBytes;
    }

    public void setReceivedBytes(Long receivedBytes) {
        this.receivedBytes = receivedBytes;
    }

    public Long getTransmittedBytes() {
        return transmittedBytes;
    }

    public void setTransmittedBytes(Long transmittedBytes) {
        this.transmittedBytes = transmittedBytes;
    }

    public Double getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(Double downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public Double getUploadSpeed() {
        return uploadSpeed;
    }

    public void setUploadSpeed(Double uploadSpeed) {
        this.uploadSpeed = uploadSpeed;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }
}