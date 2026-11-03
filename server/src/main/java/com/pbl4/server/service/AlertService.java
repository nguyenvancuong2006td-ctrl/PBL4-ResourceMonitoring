
package com.pbl4.server.service;

import com.pbl4.server.enums.AlertOperator;
import com.pbl4.server.enums.AlertStatus;
import com.pbl4.server.enums.ResourceType;
import com.pbl4.server.model.Alert;
import com.pbl4.server.model.AlertRule;
import com.pbl4.server.model.Client;
import com.pbl4.server.repository.AlertRepository;
import com.pbl4.server.repository.AlertRuleRepository;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;

    // Thời điểm bắt đầu vi phạm của từng Client và quy tắc.
    private final ConcurrentMap<String, LocalDateTime> exceededSince =
            new ConcurrentHashMap<>();

    public AlertService(
            AlertRepository alertRepository,
            AlertRuleRepository alertRuleRepository) {

        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
    }

    /**
     * Xóa trạng thái "đang vượt ngưỡng" của một Client khỏi RAM.
     *
     * Gọi khi Client bị xóa. Cần thiết vì ID Client có thể được cấp lại
     * (CLIENT-xxx = số lớn nhất hiện có + 1); nếu không xóa, Client mới
     * trùng ID sẽ thừa hưởng mốc thời gian vi phạm của Client cũ.
     */
    public void evictClientState(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            return;
        }

        // Khóa có dạng "<clientId>:<ruleId>:<resourceType>"
        String prefix = clientId + ":";

        exceededSince.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String createKey(
            Client client,
            AlertRule rule,
            ResourceType resourceType) {

        return client.getClientId()
                + ":" + rule.getRuleId()
                + ":" + resourceType.name();
    }

    private boolean isThresholdExceeded(
            double currentValue,
            AlertOperator operator,
            double thresholdValue) {

        if (!Double.isFinite(currentValue)
                || !Double.isFinite(thresholdValue)
                || operator == null) {
            return false;
        }

        switch (operator) {
            case GREATER_THAN:
                return currentValue > thresholdValue;

            case GREATER_THAN_OR_EQUAL:
                return currentValue >= thresholdValue;

            case LESS_THAN:
                return currentValue < thresholdValue;

            case LESS_THAN_OR_EQUAL:
                return currentValue <= thresholdValue;

            case EQUAL:
                return Double.compare(currentValue, thresholdValue) == 0;

            default:
                return false;
        }
    }

    private List<Alert> findOpenAlerts(Client client) {
        List<Alert> alerts = new ArrayList<>();

        alerts.addAll(
                alertRepository.findByClientAndStatus(
                        client, AlertStatus.ACTIVE));

        alerts.addAll(
                alertRepository.findByClientAndStatus(
                        client, AlertStatus.ACKNOWLEDGED));

        return alerts;
    }

    private List<Alert> findMatchingOpenAlerts(
            Client client,
            AlertRule rule,
            ResourceType resourceType) {

        List<Alert> matches = new ArrayList<>();

        for (Alert alert : findOpenAlerts(client)) {
            if (alert.getRule() == null
                    || !Objects.equals(
                            alert.getRule().getRuleId(),
                            rule.getRuleId())
                    || alert.getResourceType() != resourceType) {
                continue;
            }

            matches.add(alert);
        }

        return matches;
    }

    // Đồng bộ toàn bộ lần kiểm tra trong cùng một instance server.
    public synchronized void checkResource(
            Client client,
            ResourceType resourceType,
            double currentValue) {

        if (client == null
                || client.getClientId() == null
                || resourceType == null
                || !Double.isFinite(currentValue)) {
            return;
        }

        List<AlertRule> rules = alertRuleRepository.findAll();

        for (AlertRule rule : rules) {
            if (rule == null
                    || rule.getRuleId() == null
                    || rule.getResourceType() != resourceType) {
                continue;
            }

            String key = createKey(client, rule, resourceType);

            processRule(
                    client,
                    rule,
                    resourceType,
                    currentValue,
                    key);
        }
    }

    private void processRule(
            Client client,
            AlertRule rule,
            ResourceType resourceType,
            double currentValue,
            String key) {

        LocalDateTime now = LocalDateTime.now();

        List<Alert> openAlerts = findMatchingOpenAlerts(
                client, rule, resourceType);

        // Quy tắc bị tắt: giải quyết cảnh báo đang mở.
        if (!Boolean.TRUE.equals(rule.getEnabled())) {
            exceededSince.remove(key);

            for (Alert alert : openAlerts) {
                resolveAlert(
                        alert,
                        currentValue,
                        now,
                        "Quy tắc cảnh báo đã bị vô hiệu hóa.");
            }

            return;
        }

        // Không tiếp tục sử dụng quy tắc chưa cấu hình hợp lệ.
        if (rule.getOperator() == null
                || rule.getThresholdValue() == null
                || !Double.isFinite(rule.getThresholdValue())
                || rule.getSeverity() == null
                || rule.getDurationSeconds() == null
                || rule.getDurationSeconds() < 0) {

            exceededSince.remove(key);

            for (Alert alert : openAlerts) {
                resolveAlert(
                        alert,
                        currentValue,
                        now,
                        "Quy tắc cảnh báo không còn hợp lệ.");
            }

            return;
        }

        double threshold = rule.getThresholdValue();

        boolean exceeded = isThresholdExceeded(
                currentValue,
                rule.getOperator(),
                threshold);

        // Điều kiện đã trở lại bình thường.
        if (!exceeded) {
            exceededSince.remove(key);

            for (Alert alert : openAlerts) {
                resolveAlert(
                        alert,
                        currentValue,
                        now,
                        "Tài nguyên đã trở lại mức bình thường.");
            }

            return;
        }

        // Cảnh báo đã tồn tại: cập nhật thay vì tạo cảnh báo mới.
        if (!openAlerts.isEmpty()) {
            Alert alert = openAlerts.get(0);

            alert.setCurrentValue(currentValue);
            alert.setThresholdValue(threshold);
            alert.setSeverity(rule.getSeverity());
            alert.setMessage(buildMessage(
                    resourceType,
                    rule.getOperator(),
                    currentValue,
                    threshold));

            // Không thay đổi ACTIVE hoặc ACKNOWLEDGED.
            alertRepository.save(alert);

            exceededSince.remove(key);

            // Dọn các cảnh báo mở trùng nếu dữ liệu cũ đã có.
            for (int i = 1; i < openAlerts.size(); i++) {
                resolveAlert(
                        openAlerts.get(i),
                        currentValue,
                        now,
                        "Cảnh báo trùng đã được hợp nhất.");
            }

            return;
        }

        // Ghi nhận thời điểm bắt đầu vi phạm.
        LocalDateTime startTime =
                exceededSince.putIfAbsent(key, now);

        if (startTime == null) {
            startTime = now;
        }

        int durationSeconds = rule.getDurationSeconds();

        long elapsedMillis =
                Duration.between(startTime, now).toMillis();

        long requiredMillis = durationSeconds * 1000L;

        if (elapsedMillis < requiredMillis) {
            return;
        }

        // Tạo cảnh báo khi điều kiện đã duy trì đủ thời gian.
        Alert alert = new Alert();

        alert.setRule(rule);
        alert.setClient(client);
        alert.setResourceType(resourceType);
        alert.setCurrentValue(currentValue);
        alert.setThresholdValue(threshold);
        alert.setSeverity(rule.getSeverity());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setCreatedAt(now);
        alert.setAcknowledgedAt(null);
        alert.setResolvedAt(null);
        alert.setMessage(
                buildMessage(
                        resourceType,
                        rule.getOperator(),
                        currentValue,
                        threshold)
                        + "; điều kiện đã duy trì ít nhất "
                        + durationSeconds
                        + " giây.");

        alertRepository.save(alert);
        exceededSince.remove(key);

        System.out.println(
                "ALERT CREATED: " + alert.getMessage());
    }

    private String buildMessage(
            ResourceType resourceType,
            AlertOperator operator,
            double currentValue,
            double threshold) {

        String condition;

        switch (operator) {
            case GREATER_THAN:
                condition = "lớn hơn";
                break;

            case GREATER_THAN_OR_EQUAL:
                condition = "lớn hơn hoặc bằng";
                break;

            case LESS_THAN:
                condition = "nhỏ hơn";
                break;

            case LESS_THAN_OR_EQUAL:
                condition = "nhỏ hơn hoặc bằng";
                break;

            case EQUAL:
                condition = "bằng";
                break;

            default:
                condition = "vi phạm ngưỡng";
        }

        return resourceType
                + " hiện tại: " + currentValue
                + "; giá trị phải " + condition
                + " " + threshold;
    }

    private void resolveAlert(
            Alert alert,
            double currentValue,
            LocalDateTime now,
            String reason) {

        alert.setCurrentValue(currentValue);
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(now);
        alert.setMessage(
                reason + " Giá trị hiện tại: " + currentValue);

        alertRepository.save(alert);

        System.out.println(
                "ALERT RESOLVED: " + alert.getMessage());
    }
}