package com.pbl4.server.dto.response;

import com.pbl4.server.enums.AlertSeverity;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.enums.ResourceType;
import com.pbl4.server.model.Alert;

import java.time.LocalDateTime;

public class AlertResponse {

    private Long alertId;
    private String clientId;
    private Long ruleId;
    private String ruleName;
    private ResourceType resourceType;
    private double currentValue;
    private double thresholdValue;
    private AlertSeverity severity;
    private AlertStatus status;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private String handledBy;

    public AlertResponse(Alert alert) {
        this.alertId = alert.getAlertId();

        if (alert.getClient() != null) {
            this.clientId = alert.getClient().getClientId();
        }

        if (alert.getRule() != null) {
            this.ruleId = alert.getRule().getRuleId();
            this.ruleName = alert.getRule().getName();
        }

        this.resourceType = alert.getResourceType();
        this.currentValue = alert.getCurrentValue();
        this.thresholdValue = alert.getThresholdValue();
        this.severity = alert.getSeverity();
        this.status = alert.getStatus();
        this.message = alert.getMessage();
        this.createdAt = alert.getCreatedAt();
        this.acknowledgedAt = alert.getAcknowledgedAt();
        this.resolvedAt = alert.getResolvedAt();

        if (alert.getAccount() != null) {
            this.handledBy = alert.getAccount().getUsername();
        }
    }

    public Long getAlertId() {
        return alertId;
    }

    public String getClientId() {
        return clientId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public double getThresholdValue() {
        return thresholdValue;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    // Người dùng đã xác nhận / đóng cảnh báo (null nếu hệ thống tự xử lý)
    public String getHandledBy() {
        return handledBy;
    }
}