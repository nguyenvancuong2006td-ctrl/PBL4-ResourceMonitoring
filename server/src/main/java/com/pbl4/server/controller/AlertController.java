package com.pbl4.server.controller;

import com.pbl4.server.dto.response.AlertResponse;
import com.pbl4.server.dto.response.AlertSummaryResponse;
import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.enums.AlertSeverity;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Alert;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.AlertRepository;
import com.pbl4.server.service.DashboardService;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_LIMIT = 500;

    private final AlertRepository alertRepository;
    private final DashboardService dashboardService;
    private final AccountRepository accountRepository;

    public AlertController(
            AlertRepository alertRepository,
            DashboardService dashboardService,
            AccountRepository accountRepository) {
        this.alertRepository = alertRepository;
        this.dashboardService = dashboardService;
        this.accountRepository = accountRepository;
    }

    // Lấy tất cả cảnh báo
    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AlertResponse>> getAllAlerts() {

        List<AlertResponse> responses = alertRepository.findAll()
                .stream()
                .map(AlertResponse::new)
                .toList();

        return ResponseEntity.ok(responses);
    }

    // Cảnh báo mới nhất, có giới hạn số lượng (mặc định 10, tối đa 500)
    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AlertResponse>> getRecentAlerts(
            @RequestParam(defaultValue = "" + DEFAULT_RECENT_LIMIT) int limit) {

        return ResponseEntity.ok(
                findNewest(null, null, null, limit)
        );
    }

    // Cảnh báo của một máy, mới nhất trước, có giới hạn (mặc định 100, tối đa 500)
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AlertResponse>> getAlertsByClient(
            @PathVariable String clientId,
            @RequestParam(defaultValue = "100") int limit) {

        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID không hợp lệ"
            );
        }

        return ResponseEntity.ok(
                findNewest(clientId.trim(), null, null, limit)
        );
    }

    // Danh sách có phân trang + lọc (page bắt đầu từ 0, size tối đa 100)
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<AlertResponse>> getAlertsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) String clientId) {

        String client = clientId == null || clientId.isBlank()
                ? null
                : clientId.trim();

        return ResponseEntity.ok(
                PageResponse.of(
                        alertRepository.search(
                                client, status, severity,
                                PageResponse.pageable(page, size, newestFirst())
                        ),
                        AlertResponse::new
                )
        );
    }

    // Số lượng cảnh báo theo trạng thái / mức độ (không tải danh sách)
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public ResponseEntity<AlertSummaryResponse> getAlertSummary() {
        return ResponseEntity.ok(dashboardService.getAlertSummary());
    }

    // Xem chi tiết cảnh báo
    @GetMapping("/{alertId}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<AlertResponse> getAlertById(
            @PathVariable Long alertId) {

        Alert alert = findAlertOrThrow(alertId);

        return ResponseEntity.ok(new AlertResponse(alert));
    }

    // Lọc cảnh báo theo trạng thái
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AlertResponse>> getAlertsByStatus(
            @PathVariable AlertStatus status) {

        List<AlertResponse> responses =
                alertRepository.findByStatus(status)
                        .stream()
                        .map(AlertResponse::new)
                        .toList();

        return ResponseEntity.ok(responses);
    }

    private List<AlertResponse> findNewest(
            String clientId,
            AlertStatus status,
            AlertSeverity severity,
            int limit) {

        int safeLimit = limit <= 0 ? DEFAULT_RECENT_LIMIT : Math.min(limit, MAX_LIMIT);

        return alertRepository
                .search(clientId, status, severity,
                        org.springframework.data.domain.PageRequest.of(
                                0, safeLimit, newestFirst()))
                .getContent()
                .stream()
                .map(AlertResponse::new)
                .toList();
    }

    private Sort newestFirst() {
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "alertId"));
    }

    // Xác nhận đã tiếp nhận cảnh báo
    @PutMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Transactional
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable Long alertId,
            Authentication authentication) {

        Alert alert = findAlertOrThrow(alertId);

        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể xác nhận cảnh báo đang ACTIVE"
            );
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAccount(findAccount(authentication));

        Alert savedAlert = alertRepository.save(alert);

        return ResponseEntity.ok(new AlertResponse(savedAlert));
    }

    // Đóng cảnh báo thủ công (không cần chờ hệ thống tự xử lý)
    // PUT /api/alerts/{alertId}/resolve
    @PutMapping("/{alertId}/resolve")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Transactional
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable Long alertId,
            Authentication authentication) {

        Alert alert = findAlertOrThrow(alertId);

        if (alert.getStatus() == AlertStatus.RESOLVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cảnh báo đã được đóng"
            );
        }

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setAccount(findAccount(authentication));

        Alert savedAlert = alertRepository.save(alert);

        return ResponseEntity.ok(new AlertResponse(savedAlert));
    }

    // Tài khoản đang đăng nhập (ghi nhận ai xử lý cảnh báo)
    private Account findAccount(Authentication authentication) {

        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        return accountRepository
                .findByUsername(authentication.getName())
                .orElse(null);
    }

    private Alert findAlertOrThrow(Long alertId) {

        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cảnh báo có ID: " + alertId
                ));
    }
}