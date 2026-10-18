package pbl4.agent.monitoring;

import pbl4.agent.model.MonitoringData;
import pbl4.agent.model.NetworkData;
import pbl4.agent.model.ProcessData;

import java.time.LocalDateTime;
import java.util.List;

public class MonitoringService {

    private final CpuMonitorService cpuMonitorService;
    private final MemoryMonitorService memoryMonitorService;
    private final DiskMonitorService diskMonitorService;
    private final NetworkMonitorService networkMonitorService;
    private final ProcessMonitorService processMonitorService;

    public MonitoringService() {

        cpuMonitorService =
                new CpuMonitorService();

        memoryMonitorService =
                new MemoryMonitorService();

        diskMonitorService =
                new DiskMonitorService();

        networkMonitorService =
                new NetworkMonitorService();

        processMonitorService =
                new ProcessMonitorService();
    }

    public MonitoringData collect(
            String agentId) {

        // ==============================
        // CPU
        // ==============================

        double cpuUsage =
                cpuMonitorService.getCpuUsage();

        // ==============================
        // MEMORY
        // ==============================

        double memoryUsage =
                memoryMonitorService.getMemoryUsage();

        long totalMemory =
                memoryMonitorService.getTotalMemory();

        long usedMemory =
                memoryMonitorService.getUsedMemory();

        long availableMemory =
                memoryMonitorService.getAvailableMemory();

        // ==============================
        // DISK
        // ==============================

        double diskUsage =
                diskMonitorService.getDiskUsage();

        long totalDisk =
                diskMonitorService.getTotalSpace();

        long usedDisk =
                diskMonitorService.getUsedSpace();

        long availableDisk =
                diskMonitorService.getUsableSpace();

        // ==============================
        // NETWORK
        // ==============================

        List<NetworkData> networkDataList =
                networkMonitorService.collectNetworkData();

        // ==============================
        // PROCESS
        // ==============================

        List<ProcessData> processDataList =
                processMonitorService.collectProcessData();

        // ==============================
        // CREATE MONITORING DATA
        // ==============================

        return new MonitoringData(
                agentId,
                LocalDateTime.now(),

                cpuUsage,

                memoryUsage,
                totalMemory,
                usedMemory,
                availableMemory,

                diskUsage,
                totalDisk,
                usedDisk,
                availableDisk,

                networkDataList,

                processDataList
        );
    }
}