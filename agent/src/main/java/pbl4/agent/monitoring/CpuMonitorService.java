package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

public class CpuMonitorService {

    private final CentralProcessor processor;

    public CpuMonitorService() {

        SystemInfo systemInfo =
                new SystemInfo();

        this.processor =
                systemInfo.getHardware()
                        .getProcessor();
    }

    public double getCpuUsage() {

        long[] previousTicks =
                processor.getSystemCpuLoadTicks();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        double cpuUsage =
                processor.getSystemCpuLoadBetweenTicks(
                        previousTicks
                ) * 100;

        return cpuUsage;
    }
}