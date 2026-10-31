package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class CpuMonitorService {

    private final CentralProcessor processor;

    public CpuMonitorService() {
        SystemInfo systemInfo = new SystemInfo();

        this.processor = systemInfo.getHardware()
                .getProcessor();
    }

    /**
     * Lấy phần trăm sử dụng CPU.
     */
    public double getCpuUsage() {

        long[] previousTicks =
                processor.getSystemCpuLoadTicks();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0.0;
        }

        double cpuUsage =
                processor.getSystemCpuLoadBetweenTicks(
                        previousTicks
                ) * 100;

        return Math.max(0.0, cpuUsage);
    }

    /**
     * Lấy CPU Load Average trong 1 phút.
     */
    public double getLoadAverage1m() {

        double[] loadAverage =
                processor.getSystemLoadAverage(1);

        if (loadAverage == null
                || loadAverage.length == 0
                || loadAverage[0] < 0) {
            return -1.0;
        }

        return loadAverage[0];
    }
}