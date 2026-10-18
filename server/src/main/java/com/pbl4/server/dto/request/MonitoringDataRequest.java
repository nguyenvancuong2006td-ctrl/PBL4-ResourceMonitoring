package com.pbl4.server.dto.request;

import java.util.List;

public class MonitoringDataRequest {

    private String agentId;
    private String collectedAt;

    // ==============================
    // CPU
    // ==============================

    private double cpuUsage;

    // ==============================
    // MEMORY
    // ==============================

    private double memoryUsage;
    private long totalMemory;
    private long usedMemory;
    private long availableMemory;

    // ==============================
    // DISK
    // ==============================

    private double diskUsage;
    private long totalDisk;
    private long usedDisk;
    private long availableDisk;

    // ==============================
    // NETWORK
    // ==============================

    private List<NetworkDataRequest> networkData;

    // ==============================
    // PROCESS
    // ==============================

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
    // DISK
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