package pbl4.agent.model;

import java.time.LocalDateTime;

public class AgentRegistrationData {

    private String hostname;
    private String ipAddress;
    private String osName;
    private String osVersion;
    private String kernelVersion;
    private LocalDateTime bootTime;
    private String agentVersion;
    private String registrationToken;

    // Thông tin phần cứng tĩnh (CPU, RAM). Có thể null nếu không đọc được.
    private String cpuModel;
    private String cpuManufacturer;
    private Integer cpuCoreCount;
    private Integer cpuThreadCount;
    private Double cpuFrequencyMhz;
    private Long memoryTotalBytes;

    public AgentRegistrationData() {
    }

    public AgentRegistrationData(
            String hostname,
            String ipAddress,
            String osName,
            String osVersion,
            String kernelVersion,
            LocalDateTime bootTime,
            String agentVersion,
            String registrationToken
    ) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.osName = osName;
        this.osVersion = osVersion;
        this.kernelVersion = kernelVersion;
        this.bootTime = bootTime;
        this.agentVersion = agentVersion;
        this.registrationToken = registrationToken;
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

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
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
}