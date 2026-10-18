package pbl4.agent.model;

import java.time.LocalDateTime;
import java.util.List;

public class MonitoringData {

    private String agentId;

    private LocalDateTime collectedAt;

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

    private List<NetworkData> networkDataList;

    // ==============================
    // PROCESS
    // ==============================

    private List<ProcessData> processDataList;

    // ==============================
    // CONSTRUCTOR
    // ==============================

    public MonitoringData() {
    }

    public MonitoringData(
            String agentId,
            LocalDateTime collectedAt,
            double cpuUsage,
            double memoryUsage,
            long totalMemory,
            long usedMemory,
            long availableMemory,
            double diskUsage,
            long totalDisk,
            long usedDisk,
            long availableDisk,
            List<NetworkData> networkDataList,
            List<ProcessData> processDataList) {

        this.agentId = agentId;
        this.collectedAt = collectedAt;
        this.cpuUsage = cpuUsage;

        this.memoryUsage = memoryUsage;
        this.totalMemory = totalMemory;
        this.usedMemory = usedMemory;
        this.availableMemory = availableMemory;

        this.diskUsage = diskUsage;
        this.totalDisk = totalDisk;
        this.usedDisk = usedDisk;
        this.availableDisk = availableDisk;

        this.networkDataList = networkDataList;

        this.processDataList = processDataList;
    }

    // ==============================
    // AGENT ID
    // ==============================

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    // ==============================
    // COLLECTED AT
    // ==============================

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
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

    public List<NetworkData> getNetworkDataList() {
        return networkDataList;
    }

    public void setNetworkDataList(
            List<NetworkData> networkDataList) {

        this.networkDataList = networkDataList;
    }

    // ==============================
    // PROCESS
    // ==============================

    public List<ProcessData> getProcessDataList() {
        return processDataList;
    }

    public void setProcessDataList(
            List<ProcessData> processDataList) {

        this.processDataList = processDataList;
    }

    // ==============================
    // TO STRING
    // ==============================

    @Override
    public String toString() {

        return "MonitoringData{" +
                "agentId='" + agentId + '\'' +
                ", collectedAt=" + collectedAt +
                ", cpuUsage=" + cpuUsage +
                ", memoryUsage=" + memoryUsage +
                ", totalMemory=" + totalMemory +
                ", usedMemory=" + usedMemory +
                ", availableMemory=" + availableMemory +
                ", diskUsage=" + diskUsage +
                ", totalDisk=" + totalDisk +
                ", usedDisk=" + usedDisk +
                ", availableDisk=" + availableDisk +
                ", networkDataList=" + networkDataList +
                ", processDataList=" + processDataList +
                '}';
    }
}