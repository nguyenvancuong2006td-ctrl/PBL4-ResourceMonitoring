
package com.pbl4.server.repository;

import com.pbl4.server.model.Alert;
import com.pbl4.server.model.Client;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.enums.ResourceType;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
