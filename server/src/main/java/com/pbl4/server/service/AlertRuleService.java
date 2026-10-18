package com.pbl4.server.service;

import com.pbl4.server.model.AlertRule;
import com.pbl4.server.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    public AlertRuleService(
            AlertRuleRepository alertRuleRepository) {

        this.alertRuleRepository = alertRuleRepository;
    }

    // =========================
    // LẤY TẤT CẢ RULE
    // =========================
    public List<AlertRule> getAllRules() {

        return alertRuleRepository.findAll();
    }

    // =========================
    // LẤY CÁC RULE ĐANG BẬT
    // =========================
    public List<AlertRule> getEnabledRules() {

        return alertRuleRepository
                .findByEnabledTrue();
    }

    // =========================
    // LẤY RULE THEO ID
    // =========================
    public AlertRule getRuleById(Long ruleId) {

        return alertRuleRepository
                .findById(ruleId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Alert Rule: "
                                        + ruleId
                        )
                );
    }

    // =========================
    // TẠO RULE
    // =========================
    public AlertRule createRule(
            AlertRule rule) {

        LocalDateTime now =
                LocalDateTime.now();

        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);

        if (rule.getEnabled() == null) {
            rule.setEnabled(true);
        }

        return alertRuleRepository.save(rule);
    }

    // =========================
    // CẬP NHẬT RULE
    // =========================
    public AlertRule updateRule(
            Long ruleId,
            AlertRule request) {

        AlertRule rule =
                getRuleById(ruleId);

        rule.setName(
                request.getName()
        );

        rule.setResourceType(
                request.getResourceType()
        );

        rule.setOperator(
                request.getOperator()
        );

        rule.setThresholdValue(
                request.getThresholdValue()
        );

        rule.setDurationSeconds(
                request.getDurationSeconds()
        );

        rule.setSeverity(
                request.getSeverity()
        );

        rule.setEnabled(
                request.getEnabled()
        );

        rule.setUpdatedAt(
                LocalDateTime.now()
        );

        return alertRuleRepository.save(rule);
    }

    // =========================
    // BẬT RULE
    // =========================
    public AlertRule enableRule(
            Long ruleId) {

        AlertRule rule =
                getRuleById(ruleId);

        rule.setEnabled(true);
        rule.setUpdatedAt(
                LocalDateTime.now()
        );

        return alertRuleRepository.save(rule);
    }

    // =========================
    // TẮT RULE
    // =========================
    public AlertRule disableRule(
            Long ruleId) {

        AlertRule rule =
                getRuleById(ruleId);

        rule.setEnabled(false);
        rule.setUpdatedAt(
                LocalDateTime.now()
        );

        return alertRuleRepository.save(rule);
    }

    // =========================
    // XÓA RULE
    // =========================
    public void deleteRule(
            Long ruleId) {

        if (!alertRuleRepository
                .existsById(ruleId)) {

            throw new RuntimeException(
                    "Không tìm thấy Alert Rule: "
                            + ruleId
            );
        }

        alertRuleRepository.deleteById(
                ruleId
        );
    }
}