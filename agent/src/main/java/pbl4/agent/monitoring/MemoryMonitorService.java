package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

public class MemoryMonitorService {

    private final GlobalMemory memory;

    public MemoryMonitorService() {

        SystemInfo systemInfo =
                new SystemInfo();

        this.memory =
                systemInfo.getHardware()
                        .getMemory();
    }

    public long getTotalMemory() {

        return memory.getTotal();
    }

    public long getAvailableMemory() {

        return memory.getAvailable();
    }

    public long getUsedMemory() {

        long total =
                memory.getTotal();

        long available =
                memory.getAvailable();

        return total - available;
    }

    public double getMemoryUsage() {

        long total =
                memory.getTotal();

        long available =
                memory.getAvailable();

        long used =
                total - available;

        return ((double) used / total) * 100;
    }
}