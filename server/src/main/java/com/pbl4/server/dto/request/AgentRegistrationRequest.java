package com.pbl4.server.dto.request;

import java.time.LocalDateTime;

public class AgentRegistrationRequest {

    private String hostname;
    private String ipAddress;
    private String osName;
    private String osVersion;
    private String kernelVersion;
    private LocalDateTime bootTime;
    private String agentVersion;
    private String registrationToken;

    public AgentRegistrationRequest() {
    }

    public AgentRegistrationRequest(
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
}