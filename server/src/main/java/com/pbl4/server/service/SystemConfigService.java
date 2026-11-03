package com.pbl4.server.service;

import com.pbl4.server.dto.request.UpdateSystemConfigRequest;
import com.pbl4.server.dto.response.SystemConfigResponse;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.SystemConfig;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.SystemConfigRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Đọc và cập nhật cấu hình vận hành của hệ thống (bảng system_configs).
 *
 * Hệ thống chỉ có MỘT dòng cấu hình, khóa config_key = 'SYSTEM'.
 * Nếu dòng này chưa tồn tại thì dùng giá trị mặc định (trùng với DEFAULT
 * của bảng trong sql.sql) và chỉ tạo dòng khi có người lưu cấu hình.
 */
@Service
public class SystemConfigService {

    public static final String CONFIG_KEY = "SYSTEM";

    // Giá trị mặc định
    public static final int DEFAULT_COLLECTION_INTERVAL_SECONDS = 10;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 15;

    // Phạm vi cho phép (giây). Giao diện dùng cùng các giới hạn này.
    public static final int MIN_SECONDS = 1;
    public static final int MAX_COLLECTION_INTERVAL_SECONDS = 3600;
    public static final int MAX_HEARTBEAT_INTERVAL_SECONDS = 600;
    public static final int MAX_HEARTBEAT_TIMEOUT_SECONDS = 3600;

    private final SystemConfigRepository systemConfigRepository;
    private final AccountRepository accountRepository;

    public SystemConfigService(
            SystemConfigRepository systemConfigRepository,
            AccountRepository accountRepository) {

        this.systemConfigRepository = systemConfigRepository;
        this.accountRepository = accountRepository;
    }

    // ==========================================
    // ĐỌC CẤU HÌNH
    // ==========================================
    @Transactional(readOnly = true)
    public SystemConfigResponse getConfig() {

        return systemConfigRepository
                .findByConfigKey(CONFIG_KEY)
                .map(SystemConfigResponse::from)
                .orElseGet(() -> new SystemConfigResponse(
                        DEFAULT_COLLECTION_INTERVAL_SECONDS,
                        DEFAULT_HEARTBEAT_INTERVAL_SECONDS,
                        DEFAULT_HEARTBEAT_TIMEOUT_SECONDS,
                        null,
                        null
                ));
    }

    /**
     * Thời gian tối đa (giây) Agent được phép không gửi Heartbeat.
     * OfflineDetectionScheduler gọi hàm này ở mỗi lần quét.
     */
    @Transactional(readOnly = true)
    public int getHeartbeatTimeoutSeconds() {

        return systemConfigRepository
                .findByConfigKey(CONFIG_KEY)
                .map(SystemConfig::getHeartbeatTimeoutSeconds)
                .filter(value -> value != null && value >= MIN_SECONDS)
                .orElse(DEFAULT_HEARTBEAT_TIMEOUT_SECONDS);
    }

    // ==========================================
    // CẬP NHẬT CẤU HÌNH
    // ==========================================

    /**
     * @throws IllegalArgumentException khi thiếu trường, ngoài phạm vi
     *         hoặc Heartbeat Timeout không lớn hơn Chu kỳ Heartbeat.
     */
    @Transactional
    public SystemConfigResponse updateConfig(
            UpdateSystemConfigRequest request,
            String username) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Thiếu dữ liệu cấu hình."
            );
        }

        int collectionInterval = requireInRange(
                request.collectionIntervalSeconds(),
                "Chu kỳ thu thập dữ liệu",
                MAX_COLLECTION_INTERVAL_SECONDS
        );

        int heartbeatInterval = requireInRange(
                request.heartbeatIntervalSeconds(),
                "Chu kỳ Heartbeat",
                MAX_HEARTBEAT_INTERVAL_SECONDS
        );

        int heartbeatTimeout = requireInRange(
                request.heartbeatTimeoutSeconds(),
                "Heartbeat Timeout",
                MAX_HEARTBEAT_TIMEOUT_SECONDS
        );

        // Timeout phải dài hơn chu kỳ gửi, nếu không Agent đang hoạt động
        // bình thường vẫn bị đánh dấu mất kết nối.
        if (heartbeatTimeout <= heartbeatInterval) {
            throw new IllegalArgumentException(
                    "Heartbeat Timeout phải lớn hơn Chu kỳ Heartbeat."
            );
        }

        SystemConfig config = systemConfigRepository
                .findByConfigKey(CONFIG_KEY)
                .orElseGet(() -> {
                    SystemConfig created = new SystemConfig();
                    created.setConfigKey(CONFIG_KEY);
                    return created;
                });

        Account updatedBy = (username == null || username.isBlank())
                ? null
                : accountRepository.findByUsername(username).orElse(null);

        config.setCollectionIntervalSeconds(collectionInterval);
        config.setHeartbeatIntervalSeconds(heartbeatInterval);
        config.setHeartbeatTimeoutSeconds(heartbeatTimeout);
        config.setUpdatedBy(updatedBy);
        config.setUpdatedAt(LocalDateTime.now());

        return SystemConfigResponse.from(
                systemConfigRepository.save(config)
        );
    }

    private static int requireInRange(
            Integer value,
            String label,
            int max) {

        if (value == null) {
            throw new IllegalArgumentException(
                    label + " không được để trống."
            );
        }

        if (value < MIN_SECONDS || value > max) {
            throw new IllegalArgumentException(
                    label + " phải từ " + MIN_SECONDS
                            + " đến " + max + " giây."
            );
        }

        return value;
    }
}