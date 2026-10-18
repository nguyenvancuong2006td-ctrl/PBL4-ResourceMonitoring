package com.pbl4.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "process_info")
@IdClass(ProcessInfoId.class)
public class ProcessInfo {

    @Id
    @Column(name = "pid")
    private Long pid;

    @Id
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne
    @MapsId("snapshotId")
    @JoinColumn(
            name = "snapshot_id",
            nullable = false
    )
    private MonitoringSnapshot snapshot;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private com.pbl4.server.enums.ProcessStatus status;

    public ProcessInfo() {
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public MonitoringSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(MonitoringSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public com.pbl4.server.enums.ProcessStatus getStatus() {
        return status;
    }

    public void setStatus(
            com.pbl4.server.enums.ProcessStatus status) {
        this.status = status;
    }
}