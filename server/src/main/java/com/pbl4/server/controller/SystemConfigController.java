package com.pbl4.server.controller;

import com.pbl4.server.dto.request.UpdateSystemConfigRequest;
import com.pbl4.server.dto.response.SystemConfigResponse;
import com.pbl4.server.service.SystemConfigService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cấu hình hệ thống: chu kỳ thu thập dữ liệu, chu kỳ Heartbeat và
 * Heartbeat Timeout. Cả đọc và ghi đều cần quyền SYSTEM_CONFIG.
 */
@RestController
@RequestMapping("/api/system-config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(
            SystemConfigService systemConfigService) {

        this.systemConfigService = systemConfigService;
    }

    // ==========================================
    // LẤY CẤU HÌNH HIỆN TẠI
    // ==========================================

    @GetMapping
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public ResponseEntity<SystemConfigResponse> getConfig() {

        return ResponseEntity.ok(
                systemConfigService.getConfig()
        );
    }

    // ==========================================
    // CẬP NHẬT CẤU HÌNH
    // ==========================================

    @PutMapping
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG')")
    public ResponseEntity<?> updateConfig(
            @RequestBody UpdateSystemConfigRequest request,
            Authentication authentication) {

        try {
            return ResponseEntity.ok(
                    systemConfigService.updateConfig(
                            request,
                            authentication.getName()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}