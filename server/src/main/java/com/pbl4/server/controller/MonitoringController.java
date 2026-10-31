
package com.pbl4.server.controller;

import com.pbl4.server.dto.request.DiskDataRequest;
import com.pbl4.server.dto.request.MonitoringDataRequest;
import com.pbl4.server.dto.request.NetworkDataRequest;
import com.pbl4.server.dto.request.ProcessDataRequest;

import com.pbl4.server.dto.response.MonitoringHistoryResponse;
import com.pbl4.server.dto.response.MonitoringSnapshotDetailResponse;

import com.pbl4.server.enums.ResourceType;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkInterfaceInfo;

import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.NetworkInterfaceInfoRepository;

import com.pbl4.server.service.AgentCredentialService;
import com.pbl4.server.service.AlertService;
import com.pbl4.server.service.MonitoringDataService;
import com.pbl4.server.service.MonitoringHistoryService;
import com.pbl4.server.service.MonitoringSnapshotService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final AgentCredentialService agentCredentialService;
    private final ClientAgentRepository clientAgentRepository;
    private final MonitoringSnapshotService monitoringSnapshotService;
    private final MonitoringHistoryService monitoringHistoryService;
    private final NetworkInterfaceInfoRepository networkInterfaceInfoRepository;
    private final MonitoringDataService monitoringDataService;
    private final AlertService alertService;

    public MonitoringController(
            AgentCredentialService agentCredentialService,
            ClientAgentRepository clientAgentRepository,
            MonitoringSnapshotService monitoringSnapshotService,
            MonitoringHistoryService monitoringHistoryService,
            NetworkInterfaceInfoRepository networkInterfaceInfoRepository,
            MonitoringDataService monitoringDataService,
            AlertService alertService) {

        this.agentCredentialService = agentCredentialService;
        this.clientAgentRepository = clientAgentRepository;
        this.monitoringSnapshotService = monitoringSnapshotService;
        this.monitoringHistoryService = monitoringHistoryService;
        this.networkInterfaceInfoRepository =
                networkInterfaceInfoRepository;
        this.monitoringDataService = monitoringDataService;
        this.alertService = alertService;
    }

    /**
     * Nhận và lưu dữ liệu giám sát từ Agent.
     */
    @PostMapping("/{agentId}")
    public ResponseEntity<?> receiveMonitoringData(
            @PathVariable String agentId,
            @RequestHeader(
                    value = "X-Agent-Credential",
                    required = false
            ) String credential,
            @RequestBody MonitoringDataRequest request) {

        // 1. Kiểm tra dữ liệu đầu vào.
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body("Monitoring data không được để trống");
        }

        if (isBlank(agentId)) {
            return ResponseEntity.badRequest()
                    .body("Agent ID không được để trống");
        }

        if (isBlank(credential)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Thiếu Agent Credential");
        }

        if (request.getAgentId() == null
                || !agentId.equals(request.getAgentId())) {
            return ResponseEntity.badRequest()
                    .body("Agent ID không khớp");
        }

        if (isBlank(request.getCollectedAt())) {
            return ResponseEntity.badRequest()
                    .body("Collected At không được để trống");
        }

        // 2. Xác thực Agent.
        try {
            boolean valid = agentCredentialService
                    .validateCredential(agentId, credential);

            if (!valid) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Credential không hợp lệ");
            }
        } catch (Exception e) {
            return serverError("Không thể xác thực Agent", e);
        }

        // 3. Tìm Agent và Client.
        ClientAgent clientAgent;

        try {
            clientAgent = clientAgentRepository
                    .findById(agentId)
                    .orElse(null);
        } catch (Exception e) {
            return serverError("Không thể tìm thông tin Agent", e);
        }

        if (clientAgent == null || clientAgent.getClient() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Agent chưa được liên kết với Client");
        }

        Client client = clientAgent.getClient();
        String clientId = client.getClientId();

        // 4. Phân tích thời gian thu thập.
        LocalDateTime collectedAt;

        try {
            collectedAt = LocalDateTime.parse(
                    request.getCollectedAt().trim()
            );
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest()
                    .body("Collected At phải có định dạng ISO LocalDateTime");
        }

        // 5. Kiểm tra dữ liệu CPU.
        if (!Double.isFinite(request.getCpuUsage())
                || request.getCpuUsage() < 0
                || request.getCpuUsage() > 100) {
            return ResponseEntity.badRequest()
                    .body("CPU Usage phải nằm trong khoảng 0 đến 100");
        }

        // 6. Kiểm tra dữ liệu RAM.
        if (!Double.isFinite(request.getMemoryUsage())
                || request.getMemoryUsage() < 0
                || request.getMemoryUsage() > 100) {
            return ResponseEntity.badRequest()
                    .body("Memory Usage phải nằm trong khoảng 0 đến 100");
        }

        if (request.getTotalMemory() < 0
                || request.getUsedMemory() < 0
                || request.getAvailableMemory() < 0) {
            return ResponseEntity.badRequest()
                    .body("Dung lượng RAM không được âm");
        }

        if (request.getUsedMemory() > request.getTotalMemory()
                || request.getAvailableMemory() > request.getTotalMemory()) {
            return ResponseEntity.badRequest()
                    .body("Dung lượng RAM vượt quá tổng dung lượng");
        }

        // 7. Kiểm tra dữ liệu Network.
        if (request.getNetworkData() != null) {
            Set<String> seenInterfaces = new HashSet<>();

            for (NetworkDataRequest networkData
                    : request.getNetworkData()) {

                if (networkData == null
                        || isBlank(networkData.getInterfaceName())) {
                    return ResponseEntity.badRequest()
                            .body("Network Data không hợp lệ");
                }

                if (networkData.getBytesReceived() < 0
                        || networkData.getBytesSent() < 0) {
                    return ResponseEntity.badRequest()
                            .body("Bộ đếm byte mạng không được âm");
                }

                String interfaceName =
                        networkData.getInterfaceName().trim();

                if (!seenInterfaces.add(interfaceName)) {
                    return ResponseEntity.badRequest()
                            .body("Network Interface bị trùng: "
                                    + interfaceName);
                }
            }
        }

        // 8. Kiểm tra dữ liệu Process.
        if (request.getProcessData() != null) {
            for (ProcessDataRequest processData
                    : request.getProcessData()) {

                if (processData == null) {
                    return ResponseEntity.badRequest()
                            .body("Process Data không hợp lệ");
                }

                if (isBlank(processData.getProcessName())) {
                    return ResponseEntity.badRequest()
                            .body("Process Name không được để trống");
                }

                if (processData.getProcessId() < 0
                        || !Double.isFinite(processData.getCpuUsage())
                        || processData.getCpuUsage() < 0
                        || !Double.isFinite(processData.getMemoryUsage())
                        || processData.getMemoryUsage() < 0) {
                    return ResponseEntity.badRequest()
                            .body("Thông số Process không hợp lệ");
                }
            }
        }

        // 9. Kiểm tra dữ liệu Disk.
        List<DiskDataRequest> diskDataList = request.getDiskData();

        if (diskDataList != null) {
            Set<String> seenDisks = new HashSet<>();

            for (DiskDataRequest diskData : diskDataList) {

                if (diskData == null
                        || isBlank(diskData.getName())
                        || isBlank(diskData.getMountPoint())) {
                    return ResponseEntity.badRequest()
                            .body("Disk Data không hợp lệ");
                }

                if (diskData.getTotalSpace() < 0
                        || diskData.getUsedSpace() < 0
                        || diskData.getUsableSpace() < 0
                        || !Double.isFinite(diskData.getUsagePercent())
                        || diskData.getUsagePercent() < 0
                        || diskData.getUsagePercent() > 100) {
                    return ResponseEntity.badRequest()
                            .body("Thông số Disk không hợp lệ");
                }

                if (diskData.getUsedSpace() > diskData.getTotalSpace()
                        || diskData.getUsableSpace() > diskData.getTotalSpace()) {
                    return ResponseEntity.badRequest()
                            .body("Dung lượng Disk vượt quá tổng dung lượng");
                }

                String diskKey = diskData.getName().trim()
                        + "\u0000"
                        + diskData.getMountPoint().trim();

                if (!seenDisks.add(diskKey)) {
                    return ResponseEntity.badRequest()
                            .body("Disk bị trùng tên thiết bị và mount point");
                }
            }
        }

        // 10. Tìm hoặc đăng ký giao diện mạng cho Client.
        Map<String, NetworkInterfaceInfo> interfaceMap =
                new HashMap<>();

        if (request.getNetworkData() != null
                && !request.getNetworkData().isEmpty()) {

            try {
                List<NetworkInterfaceInfo> interfaces =
                        networkInterfaceInfoRepository
                                .findByClientClientId(clientId);

                // Đưa các giao diện mạng hiện có vào Map.
                if (interfaces != null) {
                    for (NetworkInterfaceInfo networkInterface
                            : interfaces) {

                        if (networkInterface == null
                                || isBlank(
                                        networkInterface.getInterfaceName())) {
                            continue;
                        }

                        String existingName =
                                networkInterface.getInterfaceName().trim();

                        interfaceMap.putIfAbsent(
                                existingName,
                                networkInterface
                        );
                    }
                }

                // Tự đăng ký những giao diện chưa tồn tại.
                for (NetworkDataRequest networkData
                        : request.getNetworkData()) {

                    String interfaceName =
                            networkData.getInterfaceName().trim();

                    if (interfaceMap.containsKey(interfaceName)) {
                        continue;
                    }

                    NetworkInterfaceInfo newInterface =
                            new NetworkInterfaceInfo();

                    newInterface.setClient(client);
                    newInterface.setInterfaceName(interfaceName);
                    newInterface.setDescription(
                            "Tự đăng ký từ Agent " + agentId
                    );

                    /*
                     * NetworkDataRequest hiện chưa cung cấp IP,
                     * MAC hoặc trạng thái giao diện.
                     * Không tự đặt các giá trị này.
                     */
                    newInterface.setIpAddress(null);
                    newInterface.setMacAddress(null);

                    /*
                     * Không tự gán enum status khi chưa xác nhận
                     * các giá trị của NetworkInterfaceStatus.
                     * Entity hiện tại cho phép status có thể null,
                     * nhưng database thực tế cũng phải cho phép.
                     */

                    try {
                        newInterface =
                                networkInterfaceInfoRepository
                                        .saveAndFlush(newInterface);

                    } catch (DataIntegrityViolationException e) {
                        /*
                         * Có thể một yêu cầu đồng thời đã tạo
                         * giao diện này. Tìm lại bản ghi sau đó.
                         */
                        newInterface =
                                networkInterfaceInfoRepository
                                        .findByClientClientId(clientId)
                                        .stream()
                                        .filter(item ->
                                                item != null
                                                        && interfaceName.equals(
                                                                item.getInterfaceName() == null
                                                                        ? null
                                                                        : item.getInterfaceName().trim()
                                                        )
                                        )
                                        .findFirst()
                                        .orElseThrow(() -> e);
                    }

                    interfaceMap.put(interfaceName, newInterface);
                }

            } catch (Exception e) {
                return serverError(
                        "Không thể tìm hoặc đăng ký giao diện mạng",
                        e
                );
            }
        }

        // 11. Lưu toàn bộ dữ liệu monitoring trong một transaction.
        MonitoringSnapshot snapshot;

        try {
            snapshot = monitoringDataService.saveMonitoringData(
                    client,
                    clientId,
                    request,
                    collectedAt,
                    interfaceMap
            );
        } catch (Exception e) {
            return serverError(
                    "Không thể lưu đầy đủ dữ liệu monitoring. "
                            + "Hãy kiểm tra log máy chủ.",
                    e
            );
        }

        // 12. Kiểm tra cảnh báo sau khi lưu dữ liệu.
        try {
            alertService.checkResource(
                    client,
                    ResourceType.CPU,
                    request.getCpuUsage()
            );

            alertService.checkResource(
                    client,
                    ResourceType.MEMORY,
                    request.getMemoryUsage()
            );

            alertService.checkResource(
                    client,
                    ResourceType.DISK,
                    request.getDiskUsage()
            );

        } catch (Exception e) {
            System.err.println(
                    "Đã lưu monitoring nhưng không thể kiểm tra cảnh báo."
            );
            e.printStackTrace();
        }

        // 13. In log giám sát.
        System.out.println();
        System.out.println("========== MONITORING DATA RECEIVED ==========");
        System.out.println("Agent ID      : " + agentId);
        System.out.println("Client ID     : " + clientId);
        System.out.println("Collected At  : " + collectedAt);
        System.out.println("Received At   : " + snapshot.getReceivedAt());

        System.out.printf(
                "CPU Usage     : %.2f%%%n",
                request.getCpuUsage()
        );

        System.out.printf(
                "CPU Load 1m   : %.2f%n",
                request.getLoadAverage1m()
        );

        System.out.printf(
                "Memory Usage  : %.2f%%%n",
                request.getMemoryUsage()
        );

        System.out.println(
                "Total Memory  : " + request.getTotalMemory() + " bytes"
        );

        System.out.println(
                "Used Memory   : " + request.getUsedMemory() + " bytes"
        );

        System.out.println(
                "Available Mem : " + request.getAvailableMemory() + " bytes"
        );

        System.out.printf(
                "Disk Usage    : %.2f%%%n",
                request.getDiskUsage()
        );

        System.out.println(
                "Total Disk    : " + request.getTotalDisk() + " bytes"
        );

        System.out.println(
                "Used Disk     : " + request.getUsedDisk() + " bytes"
        );

        System.out.println(
                "Available Disk: " + request.getAvailableDisk() + " bytes"
        );

        System.out.println(
                "Snapshot ID   : " + snapshot.getSnapshotId()
        );

        System.out.println("==============================================");

        return ResponseEntity.ok(
                "Monitoring data received and saved successfully"
        );
    }

    /**
     * Lấy danh sách snapshot trong khoảng thời gian.
     */
    @GetMapping("/history/{clientId}")
    @PreAuthorize("hasAuthority('HISTORY_VIEW')")
    public ResponseEntity<?> getMonitoringHistory(
            @PathVariable String clientId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {

        try {
            if (isBlank(clientId)) {
                return ResponseEntity.badRequest()
                        .body("Client ID không được để trống");
            }

            if (startTime.isAfter(endTime)) {
                return ResponseEntity.badRequest()
                        .body("Start Time phải nhỏ hơn hoặc bằng End Time");
            }

            List<MonitoringSnapshot> history =
                    monitoringSnapshotService.getHistoryByTimeRange(
                            clientId,
                            startTime,
                            endTime
                    );

            return ResponseEntity.ok(history);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {
            return serverError(
                    "Không thể lấy lịch sử giám sát", e);
        }
    }

    /**
     * Lấy dữ liệu lịch sử CPU, RAM, Disk và Network
     * phục vụ biểu đồ Dashboard.
     */
    @GetMapping("/history-chart/{clientId}")
    @PreAuthorize("hasAuthority('HISTORY_VIEW')")
    public ResponseEntity<?> getMonitoringHistoryChart(
            @PathVariable String clientId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {

        try {
            if (isBlank(clientId)) {
                return ResponseEntity.badRequest()
                        .body("Client ID không được để trống");
            }

            if (startTime.isAfter(endTime)) {
                return ResponseEntity.badRequest()
                        .body("Start Time phải nhỏ hơn hoặc bằng End Time");
            }

            List<MonitoringHistoryResponse> history =
                    monitoringHistoryService.getHistory(
                            clientId,
                            startTime,
                            endTime
                    );

            return ResponseEntity.ok(history);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {
            return serverError(
                    "Không thể lấy dữ liệu lịch sử biểu đồ", e);
        }
    }

    /**
     * Lấy chi tiết một snapshot.
     */
    @GetMapping("/snapshot/{snapshotId}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public ResponseEntity<?> getSnapshotDetails(
            @PathVariable Long snapshotId) {

        try {
            MonitoringSnapshotDetailResponse response =
                    monitoringSnapshotService.getSnapshotDetails(
                            snapshotId
                    );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());

        } catch (Exception e) {
            return serverError(
                    "Không thể lấy chi tiết snapshot", e);
        }
    }

    /**
     * Lấy dữ liệu giám sát mới nhất của Client.
     */
    @GetMapping("/latest/{clientId}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public ResponseEntity<?> getLatestMonitoringData(
            @PathVariable String clientId) {

        try {
            if (isBlank(clientId)) {
                return ResponseEntity.badRequest()
                        .body("Client ID không được để trống");
            }

            MonitoringSnapshot snapshot =
                    monitoringSnapshotService.getLatest(clientId);

            MonitoringSnapshotDetailResponse response =
                    monitoringSnapshotService.getSnapshotDetails(
                            snapshot.getSnapshotId()
                    );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());

        } catch (Exception e) {
            return serverError(
                    "Không thể lấy dữ liệu giám sát mới nhất", e);
        }
    }

    /**
     * Kiểm tra chuỗi null hoặc rỗng.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Trả lỗi máy chủ và ghi lại nguyên nhân.
     */
    private ResponseEntity<String> serverError(
            String message,
            Exception e) {

        System.err.println(message);
        e.printStackTrace();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message);
    }
}
