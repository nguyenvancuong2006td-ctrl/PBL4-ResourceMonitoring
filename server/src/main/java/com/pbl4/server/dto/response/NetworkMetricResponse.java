
package com.pbl4.server.dto.response;

public class NetworkMetricResponse {

    private Long interfaceId;
    private String interfaceName;
    private String ipAddress;
    private String macAddress;
    private String description;
    private String status;

    private Long receivedBytes;
    private Long transmittedBytes;

    private Double downloadSpeed;
    private Double uploadSpeed;

    public Long getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(Long interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
