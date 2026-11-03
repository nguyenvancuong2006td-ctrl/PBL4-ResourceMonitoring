package com.pbl4.server.service;

import com.pbl4.server.websocket.ScreenStreamWebSocketHandler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xóa vĩnh viễn một Agent cùng Client của nó và MỌI dữ liệu liên quan.
 *
 * Các khóa ngoại trong schema (sql.sql, bang_dk_tamthoi.sql,
 * share_screen.sql) đã khai báo ON DELETE CASCADE / SET NULL, nên DB sẽ tự
 * dọn nếu chỉ xóa bản ghi cha. Dù vậy lớp này vẫn xóa TƯỜNG MINH, bảng con
 * trước – bảng cha sau, vì:
 *   - chủ động thứ tự xóa và thống kê số bản ghi đã xóa (ghi log);
 *   - tránh một lệnh DELETE cha kéo cascade quá lớn trong một bước;
 *   - không phụ thuộc hoàn toàn vào việc DB thật có đủ cascade hay không
 *     (ddl-auto=none nên Hibernate không quản lý schema).
 *
 *   process_info, cpu/memory/disk/network_metrics   (theo snapshot)
 *     -> monitoring_snapshots
 *     -> disk_info, network_interface_info, cpu_info, memory_info
 *     -> alerts, remote_commands, screen_sharing_sessions
 *     -> system_logs (chỉ gỡ liên kết, giữ lại log)
 *     -> agent_registration_requests, agent_credentials
 *     -> client_agents
 *     -> clients
 *
 * Dùng JPQL xóa hàng loạt (không nạp từng entity) vì lịch sử giám sát
 * của một Client có thể rất lớn.
 *
 * Toàn bộ chạy trong MỘT transaction của nơi gọi: lỗi ở bước nào thì
 * rollback hết, không để lại dữ liệu nửa vời. Việc đóng WebSocket và dọn
 * cache trong RAM chỉ chạy SAU KHI commit thành công.
 *
 * Lưu ý: nếu thêm bảng mới có khóa ngoại tới clients / client_agents mà
 * KHÔNG có ON DELETE CASCADE thì phải bổ sung bước xóa tương ứng ở đây.
 */
@Service
public class ClientDataPurgeService {

    private static final Logger logger =
            LoggerFactory.getLogger(ClientDataPurgeService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final ScreenStreamWebSocketHandler screenStreamHandler;
    private final AlertService alertService;
    private final NetworkMetricService networkMetricService;

    public ClientDataPurgeService(
            ScreenStreamWebSocketHandler screenStreamHandler,
            AlertService alertService,
            NetworkMetricService networkMetricService) {

        this.screenStreamHandler = screenStreamHandler;
        this.alertService = alertService;
        this.networkMetricService = networkMetricService;
    }

    /**
     * Xóa Agent, Client của Agent và toàn bộ dữ liệu liên quan.
     *
     * Bắt buộc gọi bên trong transaction đang mở.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(String agentId, String clientId) {

        if (agentId == null || agentId.isBlank()
                || clientId == null || clientId.isBlank()) {

            throw new IllegalArgumentException(
                    "Agent ID và Client ID không được để trống."
            );
        }

        // Đẩy các thay đổi đang chờ xuống DB trước khi xóa hàng loạt
        entityManager.flush();

        // 1. Thu thập thông tin cần để dọn dẹp SAU commit
        //    (phải lấy trước khi xóa vì bản ghi sẽ không còn)
        List<String> sessionCodes = entityManager
                .createQuery(
                        "SELECT s.sessionCode FROM ScreenSharingSession s "
                                + "WHERE s.client.clientId = :clientId "
                                + "OR s.agent.agentId = :agentId",
                        String.class
                )
                .setParameter("clientId", clientId)
                .setParameter("agentId", agentId)
                .getResultList();

        List<Long> interfaceIds = entityManager
                .createQuery(
                        "SELECT n.interfaceId FROM NetworkInterfaceInfo n "
                                + "WHERE n.client.clientId = :clientId",
                        Long.class
                )
                .setParameter("clientId", clientId)
                .getResultList();

        // 2. Xóa dữ liệu theo thứ tự khóa ngoại
        Map<String, Integer> deleted = new LinkedHashMap<>();

        // 2a. Dữ liệu giám sát gắn với snapshot của Client
        final String snapshotsOfClient =
                "(SELECT s.snapshotId FROM MonitoringSnapshot s "
                        + "WHERE s.client.clientId = :clientId)";

        deleted.put("process_info", execute(
                "DELETE FROM ProcessInfo p "
                        + "WHERE p.snapshot.snapshotId IN " + snapshotsOfClient,
                clientId, null));

        deleted.put("cpu_metrics", execute(
                "DELETE FROM CpuMetric m "
                        + "WHERE m.snapshot.snapshotId IN " + snapshotsOfClient,
                clientId, null));

        deleted.put("memory_metrics", execute(
                "DELETE FROM MemoryMetric m "
                        + "WHERE m.snapshot.snapshotId IN " + snapshotsOfClient,
                clientId, null));

        deleted.put("disk_metrics (theo snapshot)", execute(
                "DELETE FROM DiskMetric m "
                        + "WHERE m.snapshot.snapshotId IN " + snapshotsOfClient,
                clientId, null));

        deleted.put("network_metrics (theo snapshot)", execute(
                "DELETE FROM NetworkMetric m "
                        + "WHERE m.snapshot.snapshotId IN " + snapshotsOfClient,
                clientId, null));

        // 2b. Metric còn sót tham chiếu tới đĩa / giao diện mạng của Client
        //     (phải xóa trước disk_info / network_interface_info)
        deleted.put("disk_metrics (theo đĩa)", execute(
                "DELETE FROM DiskMetric m WHERE m.disk.diskId IN "
                        + "(SELECT d.diskId FROM DiskInfo d "
                        + "WHERE d.client.clientId = :clientId)",
                clientId, null));

        deleted.put("network_metrics (theo giao diện)", execute(
                "DELETE FROM NetworkMetric m "
                        + "WHERE m.networkInterface.interfaceId IN "
                        + "(SELECT n.interfaceId FROM NetworkInterfaceInfo n "
                        + "WHERE n.client.clientId = :clientId)",
                clientId, null));

        // 2c. Snapshot rồi tới thông tin phần cứng tĩnh
        deleted.put("monitoring_snapshots", execute(
                "DELETE FROM MonitoringSnapshot s "
                        + "WHERE s.client.clientId = :clientId",
                clientId, null));

        deleted.put("disk_info", execute(
                "DELETE FROM DiskInfo d WHERE d.client.clientId = :clientId",
                clientId, null));

        deleted.put("network_interface_info", execute(
                "DELETE FROM NetworkInterfaceInfo n "
                        + "WHERE n.client.clientId = :clientId",
                clientId, null));

        deleted.put("cpu_info", execute(
                "DELETE FROM CpuInfo c WHERE c.client.clientId = :clientId",
                clientId, null));

        deleted.put("memory_info", execute(
                "DELETE FROM MemoryInfo m WHERE m.client.clientId = :clientId",
                clientId, null));

        // 2d. Cảnh báo, lệnh từ xa, phiên chia sẻ màn hình
        deleted.put("alerts", execute(
                "DELETE FROM Alert a WHERE a.client.clientId = :clientId",
                clientId, null));

        deleted.put("remote_commands", execute(
                "DELETE FROM RemoteCommand rc "
                        + "WHERE rc.client.clientId = :clientId",
                clientId, null));

        deleted.put("screen_sharing_sessions", execute(
                "DELETE FROM ScreenSharingSession s "
                        + "WHERE s.client.clientId = :clientId "
                        + "OR s.agent.agentId = :agentId",
                clientId, agentId));

        // 2e. Nhật ký hệ thống: giữ lại để phục vụ kiểm tra,
        //     chỉ gỡ liên kết tới Client / Agent sắp bị xóa
        deleted.put("system_logs (gỡ client)", execute(
                "UPDATE SystemLog l SET l.client = NULL "
                        + "WHERE l.client.clientId = :clientId",
                clientId, null));

        deleted.put("system_logs (gỡ agent)", execute(
                "UPDATE SystemLog l SET l.agent = NULL "
                        + "WHERE l.agent.agentId = :agentId",
                null, agentId));

        // 2f. Dữ liệu đăng ký / xác thực của Agent
        deleted.put("agent_registration_requests", execute(
                "DELETE FROM AgentRegistrationRequest r "
                        + "WHERE r.agent.agentId = :agentId",
                null, agentId));

        deleted.put("agent_credentials", execute(
                "DELETE FROM AgentCredential c "
                        + "WHERE c.agent.agentId = :agentId",
                null, agentId));

        // 2g. Cuối cùng: Agent rồi tới Client
        deleted.put("client_agents", execute(
                "DELETE FROM ClientAgent a WHERE a.agentId = :agentId",
                null, agentId));

        deleted.put("clients", execute(
                "DELETE FROM Client c WHERE c.clientId = :clientId",
                clientId, null));

        // Xóa hàng loạt không đi qua persistence context nên các entity
        // Agent / Client đã nạp trước đó giờ là dữ liệu cũ: bỏ chúng đi.
        entityManager.clear();

        logger.info(
                "Đã xóa Agent {} và Client {}. Số bản ghi đã xóa: {}",
                agentId, clientId, deleted
        );

        // 3. Dọn trạng thái trong RAM / kết nối đang mở sau khi commit
        runAfterCommit(() -> cleanUpRuntimeState(
                clientId, sessionCodes, interfaceIds
        ));
    }

    // ==========================================
    // HÀM PHỤ
    // ==========================================

    private int execute(String jpql, String clientId, String agentId) {

        var query = entityManager.createQuery(jpql);

        if (clientId != null) {
            query.setParameter("clientId", clientId);
        }

        if (agentId != null) {
            query.setParameter("agentId", agentId);
        }

        return query.executeUpdate();
    }

    /**
     * Đóng phiên chia sẻ màn hình đang mở và xóa cache của Client.
     * Không được ném lỗi: dữ liệu trong DB đã xóa xong và đã commit.
     */
    private void cleanUpRuntimeState(
            String clientId,
            List<String> sessionCodes,
            List<Long> interfaceIds) {

        try {
            screenStreamHandler.closeSessions(sessionCodes);
        } catch (Exception e) {
            logger.warn(
                    "Không đóng được phiên chia sẻ màn hình của Client {}: {}",
                    clientId, e.getMessage()
            );
        }

        try {
            alertService.evictClientState(clientId);
            networkMetricService.evictInterfaces(interfaceIds);
        } catch (Exception e) {
            logger.warn(
                    "Không dọn được cache của Client {}: {}",
                    clientId, e.getMessage()
            );
        }
    }

    private void runAfterCommit(Runnable task) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                }
        );
    }
}