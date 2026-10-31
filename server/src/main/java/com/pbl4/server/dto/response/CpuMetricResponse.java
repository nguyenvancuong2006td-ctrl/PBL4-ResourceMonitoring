
package com.pbl4.server.dto.response;

public class CpuMetricResponse {

    private Double usagePercent;
    private Double loadAverage1m;

    public Double getUsagePercent() {
        return usagePercent;
    }

    public void setUsagePercent(Double usagePercent) {
        this.usagePercent = usagePercent;
    }

    public Double getLoadAverage1m() {
        return loadAverage1m;
    }

    public void setLoadAverage1m(Double loadAverage1m) {
        this.loadAverage1m = loadAverage1m;
    }
}
