package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "network_metrics")
public class NetworkMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long metricId;

    @ManyToOne
    @JoinColumn(name = "snapshot_id", nullable = false)
    private MonitoringSnapshot snapshot;

    @ManyToOne
    @JoinColumn(name = "interface_id", nullable = false)
    private NetworkInterfaceInfo networkInterface;

    @Column(name = "received_bytes")
    private Long receivedBytes;

    @Column(name = "transmitted_bytes")
    private Long transmittedBytes;

    @Column(name = "download_speed")
    private Double downloadSpeed;

    @Column(name = "upload_speed")
    private Double uploadSpeed;

    public NetworkMetric() {
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

    public NetworkInterfaceInfo getNetworkInterface() {
        return networkInterface;
    }

    public void setNetworkInterface(NetworkInterfaceInfo networkInterface) {
        this.networkInterface = networkInterface;
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
}