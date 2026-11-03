package pbl4.agent.model;

import java.time.LocalDateTime;

public class ClientSystemInfo {

    private String clientId;
    private String hostname;
    private String ipAddress;
    private String osName;
    private String osVersion;
    private String kernelVersion;
    private LocalDateTime bootTime;

    // Thông tin phần cứng tĩnh (CPU, RAM). Có thể null nếu không đọc được.
    private String cpuModel;
    private String cpuManufacturer;
    private Integer cpuCoreCount;
    private Integer cpuThreadCount;
    private Double cpuFrequencyMhz;
    private Long memoryTotalBytes;

    public ClientSystemInfo() {
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getKernelVersion() {
        return kernelVersion;
    }

    public void setKernelVersion(String kernelVersion) {
        this.kernelVersion = kernelVersion;
    }

    public LocalDateTime getBootTime() {
        return bootTime;
    }

    public void setBootTime(LocalDateTime bootTime) {
        this.bootTime = bootTime;
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public void setCpuModel(String cpuModel) {
        this.cpuModel = cpuModel;
    }

    public String getCpuManufacturer() {
        return cpuManufacturer;
    }

    public void setCpuManufacturer(String cpuManufacturer) {
        this.cpuManufacturer = cpuManufacturer;
    }

    public Integer getCpuCoreCount() {
        return cpuCoreCount;
    }

    public void setCpuCoreCount(Integer cpuCoreCount) {
        this.cpuCoreCount = cpuCoreCount;
    }

    public Integer getCpuThreadCount() {
        return cpuThreadCount;
    }

    public void setCpuThreadCount(Integer cpuThreadCount) {
        this.cpuThreadCount = cpuThreadCount;
    }

    public Double getCpuFrequencyMhz() {
        return cpuFrequencyMhz;
    }

    public void setCpuFrequencyMhz(Double cpuFrequencyMhz) {
        this.cpuFrequencyMhz = cpuFrequencyMhz;
    }

    public Long getMemoryTotalBytes() {
        return memoryTotalBytes;
    }

    public void setMemoryTotalBytes(Long memoryTotalBytes) {
        this.memoryTotalBytes = memoryTotalBytes;
    }

    @Override
    public String toString() {
        return "ClientSystemInfo{" +
                "clientId='" + clientId + '\'' +
                ", hostname='" + hostname + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", kernelVersion='" + kernelVersion + '\'' +
                ", bootTime=" + bootTime +
                ", cpuModel='" + cpuModel + '\'' +
                ", cpuCoreCount=" + cpuCoreCount +
                ", cpuThreadCount=" + cpuThreadCount +
                ", memoryTotalBytes=" + memoryTotalBytes +
                '}';
    }
}