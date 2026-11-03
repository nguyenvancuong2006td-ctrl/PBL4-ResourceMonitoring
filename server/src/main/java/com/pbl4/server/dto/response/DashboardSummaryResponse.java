package com.pbl4.server.dto.response;

import com.pbl4.server.model.Client;

import java.util.List;
import java.util.Map;

/**
 * Toàn bộ dữ liệu Dashboard trong một lần gọi.
 *
 * latestByClient chỉ gồm CPU, RAM, ổ đĩa của các máy ONLINE (networks và
 * processes luôn rỗng - trang chi tiết máy tự tải bằng /monitoring/latest).
 *
 * errors: phần nào bị bỏ qua vì thiếu quyền, ví dụ
 * { "alerts": "Tài khoản không có quyền xem dữ liệu này." }.
 */
public record DashboardSummaryResponse(
        Stats stats,
        List<Client> clients,
        List<AlertResponse> recentAlerts,
        Map<String, MonitoringSnapshotDetailResponse> latestByClient,
        Map<String, String> errors
) {

    public record Stats(
            long totalClients,
            long onlineClients,
            long offlineClients,
            long pendingClients,
            double onlineRate,
            long openAlerts,
            long activeAlerts,
            long criticalAlerts,
            long warningAlerts,
            Double avgCpu,
            Double avgMemory,
            Double avgDisk
    ) {
    }
}