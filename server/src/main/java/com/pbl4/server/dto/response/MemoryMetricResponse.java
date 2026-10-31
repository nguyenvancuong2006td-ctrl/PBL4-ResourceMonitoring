
package com.pbl4.server.dto.response;

public class MemoryMetricResponse {

    private Long totalBytes;
    private Long usedBytes;
    private Long availableBytes;
    private Double usagePercent;

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

    public Long getAvailableBytes() {
        return availableBytes;
    }

    public void setAvailableBytes(Long availableBytes) {
        this.availableBytes = availableBytes;
    }

    public Double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(Double usagePercent) {
        this.usagePercent = usagePercent;
    }
}
