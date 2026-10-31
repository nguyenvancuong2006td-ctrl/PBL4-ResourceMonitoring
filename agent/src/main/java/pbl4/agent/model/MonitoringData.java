package pbl4.agent.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MonitoringData {

    private String agentId;
    private LocalDateTime collectedAt;

    // ==============================
    // CPU
    // ==============================

    private double cpuUsage;

    // Load Average trong 1 phút.
    // -1.0 nghĩa là chưa lấy được dữ liệu hợp lệ.
    private double loadAverage1m = -1.0;

    // ==============================
    // MEMORY
    // ==============================

    private double memoryUsage;
    private long totalMemory;
    private long usedMemory;
    private long availableMemory;

    // ==============================
    // DISK - TỔNG HỢP
    // ==============================

    private double diskUsage;
    private long totalDisk;
    private long usedDisk;
    private long availableDisk;

    // ==============================
    // DISK - CHI TIẾT TỪNG HỆ THỐNG TỆP
    // ==============================

    private List<DiskData> diskDataList = new ArrayList<>();

    // ==============================
    // NETWORK
    // ==============================

    private List<NetworkData> networkDataList = new ArrayList<>();

    // ==============================
    // PROCESS
    // ==============================

    private List<ProcessData> processDataList = new ArrayList<>();

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

        this.networkDataList = networkDataList != null
                ? networkDataList
                : new ArrayList<>();

        this.processDataList = processDataList != null
                ? processDataList
                : new ArrayList<>();
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
    // DISK - TỔNG HỢP
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
    // DISK - DANH SÁCH CHI TIẾT
    // ==============================

    public List<DiskData> getDiskDataList() {
        return diskDataList;
    }

    public void setDiskDataList(List<DiskData> diskDataList) {
        this.diskDataList = diskDataList != null
                ? diskDataList
                : new ArrayList<>();
    }

    // ==============================
    // NETWORK
    // ==============================

    public List<NetworkData> getNetworkDataList() {
        return networkDataList;
    }

    public void setNetworkDataList(
            List<NetworkData> networkDataList) {

        this.networkDataList = networkDataList != null
                ? networkDataList
                : new ArrayList<>();
    }

    // ==============================
    // PROCESS
    // ==============================

    public List<ProcessData> getProcessDataList() {
        return processDataList;
    }

    public void setProcessDataList(
            List<ProcessData> processDataList) {

        this.processDataList = processDataList != null
                ? processDataList
                : new ArrayList<>();
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
                ", loadAverage1m=" + loadAverage1m +
                ", memoryUsage=" + memoryUsage +
                ", totalMemory=" + totalMemory +
                ", usedMemory=" + usedMemory +
                ", availableMemory=" + availableMemory +
                ", diskUsage=" + diskUsage +
                ", totalDisk=" + totalDisk +
                ", usedDisk=" + usedDisk +
                ", availableDisk=" + availableDisk +
                ", diskDataList=" + diskDataList +
                ", networkDataList=" + networkDataList +
                ", processDataList=" + processDataList +
                '}';
    }
}