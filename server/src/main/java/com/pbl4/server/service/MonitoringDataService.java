
package com.pbl4.server.service;

import com.pbl4.server.dto.request.DiskDataRequest;
import com.pbl4.server.dto.request.MonitoringDataRequest;
import com.pbl4.server.dto.request.NetworkDataRequest;
import com.pbl4.server.dto.request.ProcessDataRequest;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.DiskInfo;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkInterfaceInfo;

import com.pbl4.server.repository.DiskInfoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MonitoringDataService {

    private final MonitoringSnapshotService monitoringSnapshotService;
    private final CpuMetricService cpuMetricService;
    private final MemoryMetricService memoryMetricService;
    private final DiskInfoRepository diskInfoRepository;
    private final DiskMetricService diskMetricService;
    private final NetworkMetricService networkMetricService;
    private final ProcessInfoService processInfoService;

    public MonitoringDataService(
            MonitoringSnapshotService monitoringSnapshotService,
            CpuMetricService cpuMetricService,
            MemoryMetricService memoryMetricService,
            DiskInfoRepository diskInfoRepository,
            DiskMetricService diskMetricService,
            NetworkMetricService networkMetricService,
            ProcessInfoService processInfoService) {

        this.monitoringSnapshotService = monitoringSnapshotService;
        this.cpuMetricService = cpuMetricService;
        this.memoryMetricService = memoryMetricService;
        this.diskInfoRepository = diskInfoRepository;
        this.diskMetricService = diskMetricService;
        this.networkMetricService = networkMetricService;
        this.processInfoService = processInfoService;
    }

    /**
     * Lưu toàn bộ dữ liệu của một lần thu thập
     * trong cùng một transaction.
     *
     * Nếu bất kỳ bước ghi dữ liệu nào thất bại,
     * exception sẽ được ném ra ngoài để Spring rollback.
     */
    @Transactional(rollbackFor = Exception.class)
    public MonitoringSnapshot saveMonitoringData(
            Client client,
            String clientId,
            MonitoringDataRequest request,
            LocalDateTime collectedAt,
            Map<String, NetworkInterfaceInfo> interfaceMap) {

        // 1. Lưu Snapshot.
        MonitoringSnapshot snapshot =
                monitoringSnapshotService.saveSnapshot(
                        clientId,
                        collectedAt
                );

        // 2. Lưu CPU Metric.
        double loadAverage1m = request.getLoadAverage1m();

        if (!Double.isFinite(loadAverage1m)) {
            loadAverage1m = -1.0;
        }

        cpuMetricService.saveCpuMetric(
                snapshot,
                request.getCpuUsage(),
                loadAverage1m
        );

        // 3. Lưu Memory Metric.
        memoryMetricService.saveMemoryMetric(
                snapshot,
                request.getTotalMemory(),
                request.getUsedMemory(),
                request.getAvailableMemory(),
                request.getMemoryUsage()
        );

        // 4. Lưu Disk Metric.
        List<DiskDataRequest> diskDataList = request.getDiskData();

        if (diskDataList != null && !diskDataList.isEmpty()) {

            for (DiskDataRequest diskData : diskDataList) {

                String deviceName = diskData.getName().trim();
                String mountPoint = diskData.getMountPoint().trim();

                DiskInfo diskInfo =
                        diskInfoRepository
                                .findByClientAndDeviceNameAndMountPoint(
                                        client,
                                        deviceName,
                                        mountPoint
                                )
                                .orElseGet(() -> {
                                    DiskInfo newDisk = new DiskInfo();
                                    newDisk.setClient(client);
                                    newDisk.setDeviceName(deviceName);
                                    newDisk.setMountPoint(mountPoint);
                                    return newDisk;
                                });

                diskInfo.setTotalBytes(diskData.getTotalSpace());

                diskInfo = diskInfoRepository.save(diskInfo);

                diskMetricService.saveDiskMetric(
                        snapshot,
                        diskInfo,
                        diskData.getTotalSpace(),
                        diskData.getUsedSpace(),
                        diskData.getUsableSpace(),
                        diskData.getUsagePercent()
                );
            }

        } else {
            /*
             * Tương thích Agent cũ chưa gửi danh sách ổ đĩa.
             * Nếu không có DiskInfo để ghi metric, phải ném lỗi.
             * Không được return ResponseEntity tại đây vì sẽ
             * khiến transaction có thể commit dữ liệu một phần.
             */
            List<DiskInfo> disks =
                    diskInfoRepository.findByClientClientId(clientId);

            if (disks == null || disks.isEmpty()) {
                throw new IllegalStateException(
                        "Không tìm thấy DiskInfo của Client: " + clientId
                );
            }

            diskMetricService.saveDiskMetric(
                    snapshot,
                    disks.get(0),
                    request.getTotalDisk(),
                    request.getUsedDisk(),
                    request.getAvailableDisk(),
                    request.getDiskUsage()
            );
        }

        // 5. Lưu Network Metric.
        if (request.getNetworkData() != null) {

            for (NetworkDataRequest networkData
                    : request.getNetworkData()) {

                String interfaceName =
                        networkData.getInterfaceName().trim();

                NetworkInterfaceInfo networkInterface =
                        interfaceMap.get(interfaceName);

                if (networkInterface == null) {
                    throw new IllegalStateException(
                            "Không tìm thấy NetworkInterfaceInfo: "
                                    + interfaceName
                    );
                }

                networkMetricService.saveNetworkMetric(
                        snapshot,
                        networkInterface,
                        networkData.getBytesReceived(),
                        networkData.getBytesSent(),
                        collectedAt
                );
            }
        }

        // 6. Lưu Process Info.
        if (request.getProcessData() != null) {

            for (ProcessDataRequest processData
                    : request.getProcessData()) {

                processInfoService.saveProcess(
                        snapshot,
                        (long) processData.getProcessId(),
                        processData.getProcessName(),
                        processData.getCpuUsage(),
                        (double) processData.getMemoryUsage()
                );
            }
        }

        // Chỉ trả về khi tất cả thao tác lưu đã hoàn thành.
        return snapshot;
    }
}
