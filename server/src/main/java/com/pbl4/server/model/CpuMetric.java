package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "cpu_metrics")
public class CpuMetric {

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

    @Column(name = "usage_percent")
    private Double usagePercent;

    @Column(name = "load_average_1m")
    private Double loadAverage1m;

    public CpuMetric() {
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

    public Double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(Double usagePercent) {
        this.usagePercent = usagePercent;
    }

    public Double getLoadAverage1m() {
        return loadAverage1m;
    }

    public void setLoadAverage1m(Double loadAverage1m) {
        this.loadAverage1m = loadAverage1m;
    }
}