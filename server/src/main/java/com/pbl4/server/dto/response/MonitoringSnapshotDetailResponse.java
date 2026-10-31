
package com.pbl4.server.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class MonitoringSnapshotDetailResponse {

    private Long snapshotId;
    private String clientId;

    private LocalDateTime collectedAt;
    private LocalDateTime receivedAt;

    private CpuMetricResponse cpu;
    private MemoryMetricResponse memory;

    private List<DiskMetricResponse> disks;
    private List<NetworkMetricResponse> networks;
    private List<ProcessInfoResponse> processes;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public CpuMetricResponse getCpu() {
        return cpu;
    }

    public void setCpu(CpuMetricResponse cpu) {
        this.cpu = cpu;
    }

    public MemoryMetricResponse getMemory() {
        return memory;
    }

    public void setMemory(MemoryMetricResponse memory) {
        this.memory = memory;
    }

    public List<DiskMetricResponse> getDisks() {
        return disks;
    }

    public void setDisks(List<DiskMetricResponse> disks) {
        this.disks = disks;
    }

    public List<NetworkMetricResponse> getNetworks() {
        return networks;
    }

    public void setNetworks(List<NetworkMetricResponse> networks) {
        this.networks = networks;
    }

    public List<ProcessInfoResponse> getProcesses() {
        return processes;
    }

    public void setProcesses(List<ProcessInfoResponse> processes) {
        this.processes = processes;
    }
}
