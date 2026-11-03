
package com.pbl4.server.repository;

import com.pbl4.server.model.Alert;
import com.pbl4.server.model.Client;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.enums.ResourceType;

import com.pbl4.server.enums.AlertSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Lấy cảnh báo theo Client ID
    List<Alert> findByClientClientId(String clientId);

    // Lấy cảnh báo theo Client và trạng thái
    List<Alert> findByClientAndStatus(
            Client client,
            AlertStatus status
    );

    // Lấy cảnh báo theo Client ID và loại tài nguyên
    List<Alert> findByClientClientIdAndResourceType(
            String clientId,
            ResourceType resourceType
    );

    // Lấy cảnh báo theo trạng thái
    List<Alert> findByStatus(AlertStatus status);

    // Tìm kiếm có phân trang; tham số nào null thì không lọc theo tham số đó
    @Query("""
        SELECT a
        FROM Alert a
        WHERE (:clientId IS NULL OR a.client.clientId = :clientId)
          AND (:status IS NULL OR a.status = :status)
          AND (:severity IS NULL OR a.severity = :severity)
    """)
    Page<Alert> search(
            @Param("clientId") String clientId,
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            Pageable pageable
    );

    // Đếm để thống kê, không cần tải danh sách
    long countByStatus(AlertStatus status);

    long countByStatusNot(AlertStatus status);

    long countBySeverityAndStatusNot(
            AlertSeverity severity,
            AlertStatus excludedStatus
    );
}