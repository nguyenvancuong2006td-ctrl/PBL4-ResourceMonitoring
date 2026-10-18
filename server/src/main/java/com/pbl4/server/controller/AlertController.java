package com.pbl4.server.controller;

import com.pbl4.server.dto.response.AlertResponse;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.model.Alert;
import com.pbl4.server.repository.AlertRepository;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRepository alertRepository;

    public AlertController(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
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

    // Xác nhận đã tiếp nhận cảnh báo
    @PutMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    @Transactional
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable Long alertId) {

        Alert alert = findAlertOrThrow(alertId);

        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Chỉ có thể xác nhận cảnh báo đang ACTIVE"
            );
        }

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedAt(LocalDateTime.now());

        Alert savedAlert = alertRepository.save(alert);

        return ResponseEntity.ok(new AlertResponse(savedAlert));
    }

    private Alert findAlertOrThrow(Long alertId) {

        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cảnh báo có ID: " + alertId
                ));
    }
}
