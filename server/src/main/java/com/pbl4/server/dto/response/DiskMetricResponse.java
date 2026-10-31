
package com.pbl4.server.dto.response;

public class DiskMetricResponse {

    private Long diskId;
    private String deviceName;
    private String mountPoint;
    private String fileSystem;

    private Long totalBytes;
    private Long usedBytes;
    private Long freeBytes;

    private Double usagePercent;

    public Long getDiskId() {
        return diskId;
    }

    public void setDiskId(Long diskId) {
        this.diskId = diskId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public String getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(String fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Long getFreeBytes() {
        return freeBytes;
    }

    public void setFreeBytes(Long freeBytes) {
        this.freeBytes = freeBytes;
    }

    public Double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(Double usagePercent) {
        this.usagePercent = usagePercent;
    }
}
