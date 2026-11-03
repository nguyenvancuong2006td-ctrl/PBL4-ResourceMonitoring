package com.pbl4.server.dto.response;

import com.pbl4.server.model.SystemConfig;

import java.time.LocalDateTime;

/**
 * Cấu hình hệ thống trả về cho giao diện.
 *
 * updatedBy / updatedAt là null khi cấu hình chưa từng được lưu
 * (hệ thống đang dùng giá trị mặc định).
 */
public record SystemConfigResponse(
        int collectionIntervalSeconds,
        int heartbeatIntervalSeconds,
        int heartbeatTimeoutSeconds,
        String updatedBy,
        LocalDateTime updatedAt
) {

    public static SystemConfigResponse from(SystemConfig config) {

        return new SystemConfigResponse(
                config.getCollectionIntervalSeconds(),
                config.getHeartbeatIntervalSeconds(),
                config.getHeartbeatTimeoutSeconds(),
                config.getUpdatedBy() != null
                        ? config.getUpdatedBy().getUsername()
                        : null,
                config.getUpdatedAt()
        );
    }
}