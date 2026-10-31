package com.pbl4.server.service;

import com.pbl4.server.dto.response.CpuMetricResponse;
import com.pbl4.server.dto.response.MemoryMetricResponse;
import com.pbl4.server.dto.response.DiskMetricResponse;
import com.pbl4.server.dto.response.NetworkMetricResponse;
import com.pbl4.server.dto.response.ProcessInfoResponse;
import com.pbl4.server.dto.response.MonitoringSnapshotDetailResponse;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.MemoryMetric;

import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.MonitoringSnapshotRepository;
import com.pbl4.server.repository.CpuMetricRepository;
import com.pbl4.server.repository.MemoryMetricRepository;
import com.pbl4.server.repository.DiskMetricRepository;
import com.pbl4.server.repository.NetworkMetricRepository;
import com.pbl4.server.repository.ProcessInfoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MonitoringSnapshotService {

    private final MonitoringSnapshotRepository monitoringSnapshotRepository;
    private final ClientRepository clientRepository;
    private final CpuMetricRepository cpuMetricRepository;
    private final MemoryMetricRepository memoryMetricRepository;
    private final DiskMetricRepository diskMetricRepository;
    private final NetworkMetricRepository networkMetricRepository;
    private final ProcessInfoRepository processInfoRepository;

    public MonitoringSnapshotService(
            MonitoringSnapshotRepository monitoringSnapshotRepository,
            ClientRepository clientRepository,
            CpuMetricRepository cpuMetricRepository,
            MemoryMetricRepository memoryMetricRepository,
            DiskMetricRepository diskMetricRepository,
            NetworkMetricRepository networkMetricRepository,
            ProcessInfoRepository processInfoRepository) {

        this.monitoringSnapshotRepository = monitoringSnapshotRepository;
        this.clientRepository = clientRepository;
        this.cpuMetricRepository = cpuMetricRepository;
        this.memoryMetricRepository = memoryMetricRepository;
        this.diskMetricRepository = diskMetricRepository;
        this.networkMetricRepository = networkMetricRepository;
        this.processInfoRepository = processInfoRepository;
    }

    /**
     * Lưu thông tin một lần thu thập dữ liệu.
     */
    public MonitoringSnapshot saveSnapshot(
            String clientId,
            LocalDateTime collectedAt) {

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Client: " + clientId
                        )
                );

        MonitoringSnapshot snapshot = new MonitoringSnapshot();
        snapshot.setClient(client);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setReceivedAt(LocalDateTime.now());

        return monitoringSnapshotRepository.save(snapshot);
    }

    /**
     * Lấy toàn bộ lịch sử giám sát của Client.
     */
    @Transactional(readOnly = true)
    public List<MonitoringSnapshot> getHistory(String clientId) {

        return monitoringSnapshotRepository
                .findByClientClientIdOrderByCollectedAtDesc(clientId);
    }

    /**
     * Lấy lịch sử giám sát trong khoảng thời gian.
     */
    @Transactional(readOnly = true)
    public List<MonitoringSnapshot> getHistoryByTimeRange(
            String clientId,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu và kết thúc không được để trống."
            );
        }

        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException(
                    "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc."
            );
        }

        return monitoringSnapshotRepository
                .findByClientClientIdAndCollectedAtBetweenOrderByCollectedAtDesc(
                        clientId,
                        startTime,
                        endTime
                );
    }

    /**
     * Lấy snapshot mới nhất của Client.
     */
    @Transactional(readOnly = true)
    public MonitoringSnapshot getLatest(String clientId) {

        return monitoringSnapshotRepository
                .findTopByClientClientIdOrderByCollectedAtDesc(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Client chưa có dữ liệu monitoring."
                        )
                );
    }

    /**
     * Lấy chi tiết một snapshot gồm CPU, RAM,
     * ổ đĩa, mạng và tiến trình.
     */
    @Transactional(readOnly = true)
    public MonitoringSnapshotDetailResponse getSnapshotDetails(
            Long snapshotId) {

        MonitoringSnapshot snapshot =
                monitoringSnapshotRepository.findById(snapshotId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy snapshot: "
                                                + snapshotId
                                )
                        );

        MonitoringSnapshotDetailResponse response =
                new MonitoringSnapshotDetailResponse();

        response.setSnapshotId(snapshot.getSnapshotId());

        response.setClientId(
                snapshot.getClient() != null
                        ? snapshot.getClient().getClientId()
                        : null
        );

        response.setCollectedAt(snapshot.getCollectedAt());
        response.setReceivedAt(snapshot.getReceivedAt());

        // ==================== CPU ====================

        CpuMetric cpu =
                cpuMetricRepository
                        .findBySnapshotSnapshotId(snapshotId)
                        .orElse(null);

        if (cpu != null) {
            CpuMetricResponse cpuResponse =
                    new CpuMetricResponse();

            cpuResponse.setUsagePercent(cpu.getUsagePercent());
            cpuResponse.setLoadAverage1m(cpu.getLoadAverage1m());

            response.setCpu(cpuResponse);
        }

        // ==================== RAM ====================

        MemoryMetric memory =
                memoryMetricRepository
                        .findBySnapshotSnapshotId(snapshotId)
                        .orElse(null);

        if (memory != null) {
            MemoryMetricResponse memoryResponse =
                    new MemoryMetricResponse();

            memoryResponse.setTotalBytes(memory.getTotalBytes());
            memoryResponse.setUsedBytes(memory.getUsedBytes());
            memoryResponse.setAvailableBytes(memory.getAvailableBytes());
            memoryResponse.setUsagePercent(memory.getUsagePercent());

            response.setMemory(memoryResponse);
        }

        // ==================== Ổ ĐĨA ====================

        List<DiskMetricResponse> diskResponses =
                diskMetricRepository
                        .findBySnapshotSnapshotId(snapshotId)
                        .stream()
                        .map(disk -> {

                            DiskMetricResponse item =
                                    new DiskMetricResponse();

                            item.setDiskId(
                                    disk.getDisk() != null
                                            ? disk.getDisk().getDiskId()
                                            : null
                            );

                            item.setDeviceName(
                                    disk.getDisk() != null
                                            ? disk.getDisk().getDeviceName()
                                            : null
                            );

                            item.setMountPoint(
                                    disk.getDisk() != null
                                            ? disk.getDisk().getMountPoint()
                                            : null
                            );

                            item.setFileSystem(
                                    disk.getDisk() != null
                                            ? disk.getDisk().getFileSystem()
                                            : null
                            );

                            item.setTotalBytes(disk.getTotalBytes());
                            item.setUsedBytes(disk.getUsedBytes());
                            item.setFreeBytes(disk.getFreeBytes());
                            item.setUsagePercent(disk.getUsagePercent());

                            return item;
                        })
                        .collect(Collectors.toList());

        response.setDisks(diskResponses);

        // ==================== MẠNG ====================

        List<NetworkMetricResponse> networkResponses =
                networkMetricRepository
                        .findBySnapshotSnapshotId(snapshotId)
                        .stream()
                        .map(network -> {

                            NetworkMetricResponse item =
                                    new NetworkMetricResponse();

                            item.setInterfaceId(
                                    network.getNetworkInterface() != null
                                            ? network.getNetworkInterface()
                                                    .getInterfaceId()
                                            : null
                            );

                            item.setInterfaceName(
                                    network.getNetworkInterface() != null
                                            ? network.getNetworkInterface()
                                                    .getInterfaceName()
                                            : null
                            );

                            item.setIpAddress(
                                    network.getNetworkInterface() != null
                                            ? network.getNetworkInterface()
                                                    .getIpAddress()
                                            : null
                            );

                            item.setMacAddress(
                                    network.getNetworkInterface() != null
                                            ? network.getNetworkInterface()
                                                    .getMacAddress()
                                            : null
                            );

                            item.setDescription(
                                    network.getNetworkInterface() != null
                                            ? network.getNetworkInterface()
                                                    .getDescription()
                                            : null
                            );

                            item.setStatus(
                                    network.getNetworkInterface() != null
                                            && network.getNetworkInterface()
                                                    .getStatus() != null
                                            ? network.getNetworkInterface()
                                                    .getStatus().name()
                                            : null
                            );

                            item.setReceivedBytes(
                                    network.getReceivedBytes()
                            );

                            item.setTransmittedBytes(
                                    network.getTransmittedBytes()
                            );

                            item.setDownloadSpeed(
                                    network.getDownloadSpeed()
                            );

                            item.setUploadSpeed(
                                    network.getUploadSpeed()
                            );

                            return item;
                        })
                        .collect(Collectors.toList());

        response.setNetworks(networkResponses);

        // ==================== TIẾN TRÌNH ====================

        List<ProcessInfoResponse> processResponses =
                processInfoRepository
                        .findBySnapshotSnapshotId(snapshotId)
                        .stream()
                        .map(process -> {

                            ProcessInfoResponse item =
                                    new ProcessInfoResponse();

                            item.setPid(process.getPid());
                            item.setName(process.getName());
                            item.setCpuUsage(process.getCpuUsage());
                            item.setMemoryUsage(process.getMemoryUsage());

                            item.setStatus(
                                    process.getStatus() != null
                                            ? process.getStatus().name()
                                            : null
                            );

                            return item;
                        })
                        .collect(Collectors.toList());

        response.setProcesses(processResponses);

        return response;
    }
}