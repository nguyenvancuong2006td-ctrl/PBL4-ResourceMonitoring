package pbl4.agent.monitoring;

import pbl4.agent.model.DiskData;
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
        this.cpuMonitorService = new CpuMonitorService();
        this.memoryMonitorService = new MemoryMonitorService();
        this.diskMonitorService = new DiskMonitorService();
        this.networkMonitorService = new NetworkMonitorService();
        this.processMonitorService = new ProcessMonitorService();
    }

    /**
     * Thu thập dữ liệu giám sát tài nguyên của Agent.
     *
     * @param agentId mã định danh Agent
     * @return dữ liệu giám sát tại thời điểm hiện tại
     */
    public MonitoringData collect(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "agentId không được để trống"
            );
        }

        // ==============================
        // CPU
        // ==============================

        double cpuUsage =
                cpuMonitorService.getCpuUsage();

        double loadAverage1m =
                cpuMonitorService.getLoadAverage1m();

        if (!Double.isFinite(cpuUsage)) {
            cpuUsage = 0.0;
        }

        if (!Double.isFinite(loadAverage1m)
                || loadAverage1m < 0) {
            loadAverage1m = -1.0;
        }

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
        // DISK - TỔNG HỢP
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
        // DISK - CHI TIẾT TỪNG ĐIỂM MOUNT
        // ==============================

        List<DiskData> diskDataList =
                diskMonitorService.collectDiskData();

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

        MonitoringData monitoringData = new MonitoringData(
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

        // Bổ sung CPU Load Average
        monitoringData.setLoadAverage1m(loadAverage1m);

        // Bổ sung danh sách chi tiết ổ đĩa
        monitoringData.setDiskDataList(diskDataList);

        return monitoringData;
    }
}