package com.pbl4.server.service;

import com.pbl4.server.dto.response.AlertResponse;
import com.pbl4.server.dto.response.CpuMetricResponse;
import com.pbl4.server.dto.response.DashboardSummaryResponse;
import com.pbl4.server.dto.response.DiskMetricResponse;
import com.pbl4.server.dto.response.MemoryMetricResponse;
import com.pbl4.server.dto.response.MonitoringSnapshotDetailResponse;
import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.enums.AlertSeverity;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.CpuMetric;
import com.pbl4.server.model.DiskMetric;
import com.pbl4.server.model.MemoryMetric;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.repository.AlertRepository;
import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.CpuMetricRepository;
import com.pbl4.server.repository.DiskMetricRepository;
import com.pbl4.server.repository.MemoryMetricRepository;
import com.pbl4.server.repository.MonitoringSnapshotRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gom dữ liệu Dashboard vào một lần gọi.
 *
 * Trước đây frontend gọi /clients, /alerts rồi /monitoring/latest/{id} cho
 * TỪNG máy online mỗi 5 giây (N + 2 request, mỗi request lại tải cả tiến trình
 * và mạng). Ở đây số truy vấn cố định, không phụ thuộc số máy:
 * danh sách máy, 4 truy vấn đếm cảnh báo, N cảnh báo mới nhất,
 * snapshot mới nhất của các máy online và CPU / RAM / ổ đĩa của chúng.
 */
@Service
public class DashboardService {

    private static final int MAX_RECENT_ALERTS = 100;

    private final ClientRepository clientRepository;
    private final AlertRepository alertRepository;
    private final MonitoringSnapshotRepository snapshotRepository;
    private final CpuMetricRepository cpuMetricRepository;
    private final MemoryMetricRepository memoryMetricRepository;
    private final DiskMetricRepository diskMetricRepository;

    public DashboardService(
            ClientRepository clientRepository,
            AlertRepository alertRepository,
            MonitoringSnapshotRepository snapshotRepository,
            CpuMetricRepository cpuMetricRepository,
            MemoryMetricRepository memoryMetricRepository,
            DiskMetricRepository diskMetricRepository) {

        this.clientRepository = clientRepository;
        this.alertRepository = alertRepository;
        this.snapshotRepository = snapshotRepository;
        this.cpuMetricRepository = cpuMetricRepository;
        this.memoryMetricRepository = memoryMetricRepository;
        this.diskMetricRepository = diskMetricRepository;
    }

    /**
     * @param includeAlerts  false nếu người dùng không có quyền ALERT_VIEW
     * @param includeMetrics false nếu người dùng không có quyền MONITORING_VIEW
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(
            int recentAlertLimit,
            boolean includeAlerts,
            boolean includeMetrics) {

        Map<String, String> errors = new LinkedHashMap<>();

        // ---------- Máy ----------
        List<Client> clients = clientRepository.findAll();

        long total = clients.size();
        long online = countClients(clients, ClientStatus.ONLINE);
        long offline = countClients(clients, ClientStatus.OFFLINE);
        long pending = countClients(clients, ClientStatus.PENDING_APPROVAL);

        // ---------- Cảnh báo ----------
        long openAlerts = 0;
        long activeAlerts = 0;
        long criticalAlerts = 0;
        long warningAlerts = 0;
        List<AlertResponse> recentAlerts = List.of();

        if (includeAlerts) {
            openAlerts = alertRepository.countByStatusNot(AlertStatus.RESOLVED);
            activeAlerts = alertRepository.countByStatus(AlertStatus.ACTIVE);
            criticalAlerts = alertRepository.countBySeverityAndStatusNot(
                    AlertSeverity.CRITICAL, AlertStatus.RESOLVED);
            warningAlerts = alertRepository.countBySeverityAndStatusNot(
                    AlertSeverity.WARNING, AlertStatus.RESOLVED);

            int limit = Math.max(0, Math.min(recentAlertLimit, MAX_RECENT_ALERTS));

            if (limit > 0) {
                recentAlerts = alertRepository
                        .search(null, null, null,
                                PageResponse.pageable(0, limit, newestFirst()))
                        .getContent()
                        .stream()
                        .map(AlertResponse::new)
                        .toList();
            }
        } else {
            errors.put("alerts", "Tài khoản không có quyền xem dữ liệu này.");
        }

        // ---------- Số liệu mới nhất của các máy ONLINE ----------
        Map<String, MonitoringSnapshotDetailResponse> latestByClient =
                new LinkedHashMap<>();

        if (includeMetrics) {
            List<String> onlineIds = clients.stream()
                    .filter(c -> c.getStatus() == ClientStatus.ONLINE)
                    .map(Client::getClientId)
                    .toList();

            if (!onlineIds.isEmpty()) {
                latestByClient = loadLatestMetrics(onlineIds);
            }
        } else {
            errors.put("metrics", "Tài khoản không có quyền xem dữ liệu này.");
        }

        // ---------- Trung bình ----------
        List<Double> cpuValues = new ArrayList<>();
        List<Double> memoryValues = new ArrayList<>();
        List<Double> diskValues = new ArrayList<>();

        for (MonitoringSnapshotDetailResponse m : latestByClient.values()) {

            if (m.getCpu() != null && m.getCpu().getUsagePercent() != null) {
                cpuValues.add(m.getCpu().getUsagePercent());
            }

            if (m.getMemory() != null
                    && m.getMemory().getUsagePercent() != null) {
                memoryValues.add(m.getMemory().getUsagePercent());
            }

            // Trung bình các ổ đĩa của từng máy, rồi trung bình giữa các máy
            Double diskAvg = average(
                    m.getDisks().stream()
                            .map(DiskMetricResponse::getUsagePercent)
                            .toList()
            );

            if (diskAvg != null) {
                diskValues.add(diskAvg);
            }
        }

        double onlineRate = total == 0
                ? 0
                : Math.round((online * 1000.0) / total) / 10.0;

        DashboardSummaryResponse.Stats stats =
                new DashboardSummaryResponse.Stats(
                        total, online, offline, pending, onlineRate,
                        openAlerts, activeAlerts, criticalAlerts, warningAlerts,
                        average(cpuValues),
                        average(memoryValues),
                        average(diskValues)
                );

        return new DashboardSummaryResponse(
                stats, clients, recentAlerts, latestByClient, errors);
    }

    /**
     * Số lượng cảnh báo theo trạng thái / mức độ.
     */
    @Transactional(readOnly = true)
    public com.pbl4.server.dto.response.AlertSummaryResponse getAlertSummary() {

        long active = alertRepository.countByStatus(AlertStatus.ACTIVE);
        long acknowledged =
                alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED);
        long resolved = alertRepository.countByStatus(AlertStatus.RESOLVED);

        return new com.pbl4.server.dto.response.AlertSummaryResponse(
                active + acknowledged + resolved,
                active,
                acknowledged,
                resolved,
                active + acknowledged,
                alertRepository.countBySeverityAndStatusNot(
                        AlertSeverity.CRITICAL, AlertStatus.RESOLVED),
                alertRepository.countBySeverityAndStatusNot(
                        AlertSeverity.WARNING, AlertStatus.RESOLVED)
        );
    }

    // ==================================================
    // HÀM HỖ TRỢ
    // ==================================================

    private Map<String, MonitoringSnapshotDetailResponse> loadLatestMetrics(
            Collection<String> clientIds) {

        List<MonitoringSnapshot> snapshots =
                snapshotRepository.findLatestForClients(clientIds);

        if (snapshots.isEmpty()) {
            return new LinkedHashMap<>();
        }

        List<Long> snapshotIds = snapshots.stream()
                .map(MonitoringSnapshot::getSnapshotId)
                .toList();

        Map<Long, CpuMetric> cpuBySnapshot =
                cpuMetricRepository.findBySnapshotSnapshotIdIn(snapshotIds)
                        .stream()
                        .collect(Collectors.toMap(
                                m -> m.getSnapshot().getSnapshotId(),
                                Function.identity(),
                                (a, b) -> a));

        Map<Long, MemoryMetric> memoryBySnapshot =
                memoryMetricRepository.findBySnapshotSnapshotIdIn(snapshotIds)
                        .stream()
                        .collect(Collectors.toMap(
                                m -> m.getSnapshot().getSnapshotId(),
                                Function.identity(),
                                (a, b) -> a));

        Map<Long, List<DiskMetric>> disksBySnapshot = new HashMap<>();

        for (DiskMetric disk
                : diskMetricRepository.findBySnapshotSnapshotIdIn(snapshotIds)) {

            disksBySnapshot
                    .computeIfAbsent(
                            disk.getSnapshot().getSnapshotId(),
                            k -> new ArrayList<>())
                    .add(disk);
        }

        Map<String, MonitoringSnapshotDetailResponse> result =
                new LinkedHashMap<>();

        for (MonitoringSnapshot snapshot : snapshots) {

            Long id = snapshot.getSnapshotId();

            MonitoringSnapshotDetailResponse item =
                    new MonitoringSnapshotDetailResponse();

            item.setSnapshotId(id);
            item.setClientId(snapshot.getClient().getClientId());
            item.setCollectedAt(snapshot.getCollectedAt());
            item.setReceivedAt(snapshot.getReceivedAt());

            CpuMetric cpu = cpuBySnapshot.get(id);
            if (cpu != null) {
                CpuMetricResponse r = new CpuMetricResponse();
                r.setUsagePercent(cpu.getUsagePercent());
                r.setLoadAverage1m(cpu.getLoadAverage1m());
                item.setCpu(r);
            }

            MemoryMetric memory = memoryBySnapshot.get(id);
            if (memory != null) {
                MemoryMetricResponse r = new MemoryMetricResponse();
                r.setTotalBytes(memory.getTotalBytes());
                r.setUsedBytes(memory.getUsedBytes());
                r.setAvailableBytes(memory.getAvailableBytes());
                r.setUsagePercent(memory.getUsagePercent());
                item.setMemory(r);
            }

            List<DiskMetricResponse> disks = new ArrayList<>();

            for (DiskMetric disk
                    : disksBySnapshot.getOrDefault(id, List.of())) {

                DiskMetricResponse r = new DiskMetricResponse();

                if (disk.getDisk() != null) {
                    r.setDiskId(disk.getDisk().getDiskId());
                    r.setDeviceName(disk.getDisk().getDeviceName());
                    r.setMountPoint(disk.getDisk().getMountPoint());
                    r.setFileSystem(disk.getDisk().getFileSystem());
                }

                r.setTotalBytes(disk.getTotalBytes());
                r.setUsedBytes(disk.getUsedBytes());
                r.setFreeBytes(disk.getFreeBytes());
                r.setUsagePercent(disk.getUsagePercent());

                disks.add(r);
            }

            item.setDisks(disks);
            item.setNetworks(List.of());
            item.setProcesses(List.of());

            result.put(item.getClientId(), item);
        }

        return result;
    }

    private long countClients(List<Client> clients, ClientStatus status) {
        return clients.stream().filter(c -> c.getStatus() == status).count();
    }

    private Double average(List<Double> values) {

        List<Double> valid = values.stream()
                .filter(v -> v != null && !v.isNaN() && !v.isInfinite())
                .toList();

        if (valid.isEmpty()) {
            return null;
        }

        double sum = 0;
        for (double v : valid) {
            sum += v;
        }

        return Math.round((sum / valid.size()) * 10) / 10.0;
    }

    private Sort newestFirst() {
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "alertId"));
    }
}