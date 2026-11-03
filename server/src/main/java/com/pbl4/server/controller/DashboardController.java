package com.pbl4.server.controller;

import com.pbl4.server.dto.response.DashboardSummaryResponse;
import com.pbl4.server.service.DashboardService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Dữ liệu tổng hợp cho Dashboard / danh sách máy trong MỘT request.
     *
     * Cần CLIENT_VIEW. Phần cảnh báo cần thêm ALERT_VIEW, phần số liệu cần
     * thêm MONITORING_VIEW; thiếu quyền thì phần đó bị bỏ trống và được ghi
     * vào "errors" thay vì làm hỏng cả response.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam(defaultValue = "10") int recentAlerts,
            Authentication authentication) {

        return ResponseEntity.ok(
                dashboardService.getSummary(
                        recentAlerts,
                        hasAuthority(authentication, "ALERT_VIEW"),
                        hasAuthority(authentication, "MONITORING_VIEW")
                )
        );
    }

    private boolean hasAuthority(Authentication authentication, String code) {

        return authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> code.equals(a.getAuthority()));
    }
}