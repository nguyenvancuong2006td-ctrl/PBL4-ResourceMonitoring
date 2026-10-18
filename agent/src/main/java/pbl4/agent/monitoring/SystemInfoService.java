package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OperatingSystem;

public class SystemInfoService {

    private final SystemInfo systemInfo;

    public SystemInfoService() {
        this.systemInfo = new SystemInfo();
    }

    public void printSystemInfo() {

        OperatingSystem os =
                systemInfo.getOperatingSystem();

        CentralProcessor processor =
                systemInfo.getHardware()
                        .getProcessor();

        GlobalMemory memory =
                systemInfo.getHardware()
                        .getMemory();

        System.out.println();
        System.out.println("================================");
        System.out.println("       SYSTEM INFORMATION");
        System.out.println("================================");

        System.out.println(
                "OS       : " + os
        );

        System.out.println(
                "CPU      : "
                        + processor
                                .getProcessorIdentifier()
                                .getName()
        );

        System.out.println(
                "CPU Core : "
                        + processor
                                .getLogicalProcessorCount()
        );

        System.out.println(
                "RAM      : "
                        + formatBytes(
                                memory.getTotal()
                        )
        );

        System.out.println("================================");
    }

    private String formatBytes(long bytes) {

        double gb =
                bytes / (1024.0 * 1024.0 * 1024.0);

        return String.format(
                "%.2f GB",
                gb
        );
    }
}