package com.pbl4.server.dto.request;

/**
 * Nội dung cập nhật cấu hình hệ thống (PUT /api/system-config).
 *
 * Dùng kiểu Integer để phân biệt "thiếu trường" (null) với giá trị 0.
 * Việc kiểm tra phạm vi được thực hiện trong SystemConfigService.
 */
public record UpdateSystemConfigRequest(
        Integer collectionIntervalSeconds,
        Integer heartbeatIntervalSeconds,
        Integer heartbeatTimeoutSeconds
) {
}