package com.pbl4.server.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.ArrayList;
import java.util.List;

public class MonitoringDataRequest {

    private String agentId;
    private String collectedAt;

    // ==============================
    // CPU
    // ==============================

    @PositiveOrZero(message = "CPU Usage không được âm")
    private double cpuUsage;

    /**
     * CPU load average trong 1 phút.
     * Giá trị -1.0 biểu thị không lấy được dữ liệu.
     */
    private double loadAverage1m = -1.0;

    // ==============================
    // MEMORY
    // ==============================

    @PositiveOrZero(message = "Memory Usage không được âm")
    private double memoryUsage;

    @PositiveOrZero(message = "Total Memory không được âm")
    private long totalMemory;

    @PositiveOrZero(message = "Used Memory không được âm")
    private long usedMemory;

    @PositiveOrZero(message = "Available Memory không được âm")
    private long availableMemory;

    // ==============================
    // DISK TỔNG HỢP
    // ==============================

    @PositiveOrZero(message = "Disk Usage không được âm")
    private double diskUsage;

    @PositiveOrZero(message = "Total Disk không được âm")
    private long totalDisk;

    @PositiveOrZero(message = "Used Disk không được âm")
    private long usedDisk;

    @PositiveOrZero(message = "Available Disk không được âm")
    private long availableDisk;

    // ==============================
    // DANH SÁCH DISK / PHÂN VÙNG
    // ==============================

    @Valid
    private List<DiskDataRequest> diskData = new ArrayList<>();

    // ==============================
    // NETWORK
    // ==============================

    @Valid
    private List<NetworkDataRequest> networkData;

    // ==============================
    // PROCESS
    // ==============================

    @Valid
    private List<ProcessDataRequest> processData;

    // ==============================
    // CONSTRUCTOR
    // ==============================

    public MonitoringDataRequest() {
    }

    // ==============================
    // AGENT
    // ==============================

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(String collectedAt) {
        this.collectedAt = collectedAt;
    }

    // ==============================
    // CPU
    // ==============================

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getLoadAverage1m() {
        return loadAverage1m;
    }

    public void setLoadAverage1m(double loadAverage1m) {
        this.loadAverage1m = loadAverage1m;
    }

    // ==============================
    // MEMORY
    // ==============================

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public void setUsedMemory(long usedMemory) {
        this.usedMemory = usedMemory;
    }

    public long getAvailableMemory() {
        return availableMemory;
    }

    public void setAvailableMemory(long availableMemory) {
        this.availableMemory = availableMemory;
    }

    // ==============================
    // DISK TỔNG HỢP
    // ==============================

    public double getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(double diskUsage) {
        this.diskUsage = diskUsage;
    }

    public long getTotalDisk() {
        return totalDisk;
    }

    public void setTotalDisk(long totalDisk) {
        this.totalDisk = totalDisk;
    }

    public long getUsedDisk() {
        return usedDisk;
    }

    public void setUsedDisk(long usedDisk) {
        this.usedDisk = usedDisk;
    }

    public long getAvailableDisk() {
        return availableDisk;
    }

    public void setAvailableDisk(long availableDisk) {
        this.availableDisk = availableDisk;
    }

    // ==============================
    // DANH SÁCH DISK / PHÂN VÙNG
    // ==============================

    public List<DiskDataRequest> getDiskData() {
        return diskData;
    }

    public void setDiskData(List<DiskDataRequest> diskData) {
        this.diskData = diskData != null
                ? diskData
                : new ArrayList<>();
    }

    // ==============================
    // NETWORK
    // ==============================

    public List<NetworkDataRequest> getNetworkData() {
        return networkData;
    }

    public void setNetworkData(
            List<NetworkDataRequest> networkData) {
        this.networkData = networkData;
    }

    // ==============================
    // PROCESS
    // ==============================

    public List<ProcessDataRequest> getProcessData() {
        return processData;
    }

    public void setProcessData(
            List<ProcessDataRequest> processData) {
        this.processData = processData;
    }
}