package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "memory_metrics")
public class MemoryMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @OneToOne
    @JoinColumn(
        name = "snapshot_id",
        nullable = false,
        unique = true
    )
    private MonitoringSnapshot snapshot;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "used_bytes")
    private Long usedBytes;

    @Column(name = "available_bytes")
    private Long availableBytes;

    @Column(name = "usage_percent")
    private Double usagePercent;

    public MemoryMetric() {
    }

    public Long getMetricId() {
        return metricId;
    }

    public void setMetricId(Long metricId) {
        this.metricId = metricId;
    }

    public MonitoringSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(MonitoringSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Long getAvailableBytes() {
        return availableBytes;
    }

    public void setAvailableBytes(Long availableBytes) {
        this.availableBytes = availableBytes;
    }

    public Double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(Double usagePercent) {
        this.usagePercent = usagePercent;
    }
}