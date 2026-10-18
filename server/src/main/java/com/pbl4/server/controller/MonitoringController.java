
package com.pbl4.server.controller;

import com.pbl4.server.dto.request.MonitoringDataRequest;
import com.pbl4.server.dto.request.NetworkDataRequest;
import com.pbl4.server.dto.request.ProcessDataRequest;

import com.pbl4.server.enums.ResourceType;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.DiskInfo;
import com.pbl4.server.model.DiskMetric;
import com.pbl4.server.model.MemoryMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkInterfaceInfo;

import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.DiskInfoRepository;
import com.pbl4.server.repository.NetworkInterfaceInfoRepository;

import com.pbl4.server.service.AgentCredentialService;
import com.pbl4.server.service.AlertService;
import com.pbl4.server.service.CpuMetricService;
import com.pbl4.server.service.DiskMetricService;
import com.pbl4.server.service.MemoryMetricService;
import com.pbl4.server.service.MonitoringSnapshotService;
import com.pbl4.server.service.NetworkMetricService;
import com.pbl4.server.service.ProcessInfoService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final AgentCredentialService agentCredentialService;
    private final ClientAgentRepository clientAgentRepository;
    private final MonitoringSnapshotService monitoringSnapshotService;
    private final CpuMetricService cpuMetricService;
    private final MemoryMetricService memoryMetricService;
    private final DiskInfoRepository diskInfoRepository;
    private final DiskMetricService diskMetricService;
    private final NetworkInterfaceInfoRepository networkInterfaceInfoRepository;
    private final NetworkMetricService networkMetricService;
    private final ProcessInfoService processInfoService;
    private final AlertService alertService;

    public MonitoringController(
            AgentCredentialService agentCredentialService,
            ClientAgentRepository clientAgentRepository,
            MonitoringSnapshotService monitoringSnapshotService,
            CpuMetricService cpuMetricService,
            MemoryMetricService memoryMetricService,
            DiskInfoRepository diskInfoRepository,
            DiskMetricService diskMetricService,
            NetworkInterfaceInfoRepository networkInterfaceInfoRepository,
            NetworkMetricService networkMetricService,
            ProcessInfoService processInfoService,
            AlertService alertService) {

        this.agentCredentialService = agentCredentialService;
        this.clientAgentRepository = clientAgentRepository;
        this.monitoringSnapshotService = monitoringSnapshotService;
        this.cpuMetricService = cpuMetricService;
        this.memoryMetricService = memoryMetricService;
        this.diskInfoRepository = diskInfoRepository;
        this.diskMetricService = diskMetricService;
        this.networkInterfaceInfoRepository = networkInterfaceInfoRepository;
        this.networkMetricService = networkMetricService;
        this.processInfoService = processInfoService;
        this.alertService = alertService;
    }

    // Nhận dữ liệu giám sát từ Agent.
    @PostMapping("/{agentId}")
    public ResponseEntity<?> receiveMonitoringData(
            @PathVariable String agentId,
            @RequestHeader("X-Agent-Credential") String credential,
            @RequestBody MonitoringDataRequest request) {

        // 1. Kiểm tra dữ liệu.
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body("Monitoring data không được để trống");
        }

        // 2. Kiểm tra credential.
        boolean valid = agentCredentialService.validateCredential(
                agentId, credential);

        if (!valid) {
            return ResponseEntity.status(401)
                    .body("Credential không hợp lệ");
        }

        // 3. Kiểm tra Agent ID.
        if (request.getAgentId() == null
                || !agentId.equals(request.getAgentId())) {
            return ResponseEntity.badRequest()
                    .body("Agent ID không khớp");
        }

        // 4. Tìm Client liên kết với Agent.
        ClientAgent clientAgent = clientAgentRepository
                .findById(agentId)
                .orElse(null);

        if (clientAgent == null || clientAgent.getClient() == null) {
            return ResponseEntity.status(404)
                    .body("Agent chưa được liên kết với Client");
        }

        Client client = clientAgent.getClient();
        String clientId = client.getClientId();

        // 5. Kiểm tra thời gian thu thập.
        LocalDateTime collectedAt;

        try {
            collectedAt = LocalDateTime.parse(
                    request.getCollectedAt());

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Collected At không đúng định dạng");
        }

        // 6. Lưu snapshot.
        MonitoringSnapshot snapshot;

        try {
            snapshot = monitoringSnapshotService.saveSnapshot(
                    clientId, collectedAt);

        } catch (Exception e) {
            return serverError(
                    "Không thể lưu Monitoring Snapshot", e);
        }

        // 7. Lưu CPU Metric.
        CpuMetric cpuMetric;

        try {
            cpuMetric = cpuMetricService.saveCpuMetric(
                    snapshot,
                    request.getCpuUsage(),
                    null);

        } catch (Exception e) {
            return serverError("Không thể lưu CPU Metric", e);
        }

        // 8. Lưu Memory Metric.
        MemoryMetric memoryMetric;

        try {
            memoryMetric = memoryMetricService.saveMemoryMetric(
                    snapshot,
                    request.getTotalMemory(),
                    request.getUsedMemory(),
                    request.getAvailableMemory(),
                    request.getMemoryUsage());

        } catch (Exception e) {
            return serverError("Không thể lưu Memory Metric", e);
        }

        // 9. Tìm DiskInfo của Client.
        DiskInfo diskInfo;

        try {
            List<DiskInfo> disks =
                    diskInfoRepository.findByClientClientId(clientId);

            if (disks == null || disks.isEmpty()) {
                return ResponseEntity.status(404)
                        .body("Không tìm thấy DiskInfo của " + clientId);
            }

            diskInfo = disks.get(0);

        } catch (Exception e) {
            return serverError("Không thể tìm DiskInfo", e);
        }

        // 10. Lưu Disk Metric.
        DiskMetric diskMetric;

        try {
            diskMetric = diskMetricService.saveDiskMetric(
                    snapshot,
                    diskInfo,
                    request.getTotalDisk(),
                    request.getUsedDisk(),
                    request.getAvailableDisk(),
                    request.getDiskUsage());

        } catch (Exception e) {
            return serverError("Không thể lưu Disk Metric", e);
        }

        // 11. Lưu Network Metric.
        if (request.getNetworkData() != null) {
            for (NetworkDataRequest networkData
                    : request.getNetworkData()) {

                if (networkData == null
                        || networkData.getInterfaceName() == null) {
                    return ResponseEntity.badRequest()
                            .body("Network Data không hợp lệ");
                }

                try {
                    List<NetworkInterfaceInfo> interfaces =
                            networkInterfaceInfoRepository
                                    .findByClientClientId(clientId);

                    NetworkInterfaceInfo networkInterface = null;

                    for (NetworkInterfaceInfo item : interfaces) {
                        if (networkData.getInterfaceName().equals(
                                item.getInterfaceName())) {
                            networkInterface = item;
                            break;
                        }
                    }

                    if (networkInterface == null) {
                        return ResponseEntity.status(404)
                                .body("Không tìm thấy NetworkInterfaceInfo: "
                                        + networkData.getInterfaceName());
                    }

                    networkMetricService.saveNetworkMetric(
                            snapshot,
                            networkInterface,
                            networkData.getBytesReceived(),
                            networkData.getBytesSent(),
                            0,
                            0);

                } catch (Exception e) {
                    return serverError("Không thể lưu Network Metric", e);
                }
            }
        }

        // 12. Lưu Process Info.
        if (request.getProcessData() != null) {
            for (ProcessDataRequest processData
                    : request.getProcessData()) {

                if (processData == null) {
                    return ResponseEntity.badRequest()
                            .body("Process Data không hợp lệ");
                }

                try {
                    processInfoService.saveProcess(
                            snapshot,
                            (long) processData.getProcessId(),
                            processData.getProcessName(),
                            processData.getCpuUsage(),
                            (double) processData.getMemoryUsage());

                } catch (Exception e) {
                    return serverError("Không thể lưu Process Info", e);
                }
            }
        }

        // 13. Kiểm tra cảnh báo CPU, RAM và Disk.
        try {
            alertService.checkResource(
                    client,
                    ResourceType.CPU,
                    request.getCpuUsage());

            alertService.checkResource(
                    client,
                    ResourceType.MEMORY,
                    request.getMemoryUsage());

            alertService.checkResource(
                    client,
                    ResourceType.DISK,
                    request.getDiskUsage());

        } catch (Exception e) {
            return serverError("Không thể kiểm tra cảnh báo", e);
        }

        // 14. In thông tin giám sát.
        System.out.println();
        System.out.println("========== MONITORING DATA RECEIVED ==========");
        System.out.println("Agent ID      : " + agentId);
        System.out.println("Client ID     : " + clientId);
        System.out.println("Collected At  : " + request.getCollectedAt());
        System.out.println("Received At   : " + snapshot.getReceivedAt());

        System.out.printf("CPU Usage     : %.2f%%%n",
                request.getCpuUsage());

        System.out.printf("Memory Usage  : %.2f%%%n",
                request.getMemoryUsage());

        System.out.println("Total Memory  : "
                + request.getTotalMemory() + " bytes");

        System.out.println("Used Memory   : "
                + request.getUsedMemory() + " bytes");

        System.out.println("Available Mem : "
                + request.getAvailableMemory() + " bytes");

        System.out.printf("Disk Usage    : %.2f%%%n",
                request.getDiskUsage());

        System.out.println("Total Disk    : "
                + request.getTotalDisk() + " bytes");

        System.out.println("Used Disk     : "
                + request.getUsedDisk() + " bytes");

        System.out.println("Available Disk: "
                + request.getAvailableDisk() + " bytes");

        // In Network.
        if (request.getNetworkData() != null) {
            for (NetworkDataRequest networkData
                    : request.getNetworkData()) {

                System.out.println("Network Interface : "
                        + networkData.getInterfaceName());

                System.out.println("Display Name      : "
                        + networkData.getDisplayName());

                System.out.println("Bytes Received    : "
                        + networkData.getBytesReceived());

                System.out.println("Bytes Sent        : "
                        + networkData.getBytesSent());
            }
        }

        // In Process.
        if (request.getProcessData() != null) {
            System.out.println("Process Count : "
                    + request.getProcessData().size());

            for (ProcessDataRequest processData
                    : request.getProcessData()) {

                System.out.println("Process PID    : "
                        + processData.getProcessId());

                System.out.println("Process Name   : "
                        + processData.getProcessName());

                System.out.printf("Process CPU    : %.2f%%%n",
                        processData.getCpuUsage());

                System.out.println("Process Memory : "
                        + processData.getMemoryUsage() + " bytes");

                System.out.println("----------------------------------------------");
            }
        }

        // In ID.
        System.out.println("Snapshot ID   : " + snapshot.getSnapshotId());
        System.out.println("CPU Metric ID : " + cpuMetric.getMetricId());
        System.out.println("Memory ID     : " + memoryMetric.getMetricId());
        System.out.println("Disk ID       : " + diskMetric.getMetricId());
        System.out.println("==============================================");

        return ResponseEntity.ok(
                "Monitoring data received and saved successfully");
    }

    // Trả lỗi máy chủ.
    private ResponseEntity<String> serverError(
            String message, Exception e) {

        System.err.println(message + ": " + e.getMessage());

        return ResponseEntity.internalServerError()
                .body(message);
    }
}
