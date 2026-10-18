package com.pbl4.server.controller;

import com.pbl4.server.model.AlertRule;
import com.pbl4.server.service.AlertRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(
            AlertRuleService alertRuleService) {

        this.alertRuleService =
                alertRuleService;
    }

    // =========================
    // LẤY TẤT CẢ RULE
    // =========================
    @GetMapping
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public ResponseEntity<List<AlertRule>> getAllRules() {

        return ResponseEntity.ok(
                alertRuleService.getAllRules()
        );
    }

    // =========================
    // LẤY RULE ĐANG BẬT
    // =========================
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public ResponseEntity<List<AlertRule>> getEnabledRules() {

        return ResponseEntity.ok(
                alertRuleService.getEnabledRules()
        );
    }

    // =========================
    // LẤY RULE THEO ID
    // =========================
    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('ALERT_VIEW')")
    public ResponseEntity<AlertRule> getRuleById(
            @PathVariable Long ruleId) {

        return ResponseEntity.ok(
                alertRuleService.getRuleById(ruleId)
        );
    }

    // =========================
    // TẠO RULE
    // =========================
    @PostMapping
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public ResponseEntity<AlertRule> createRule(
            @RequestBody AlertRule rule) {

        return ResponseEntity.ok(
                alertRuleService.createRule(rule)
        );
    }

    // =========================
    // CẬP NHẬT RULE
    // =========================
    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public ResponseEntity<AlertRule> updateRule(
            @PathVariable Long ruleId,
            @RequestBody AlertRule rule) {

        return ResponseEntity.ok(
                alertRuleService.updateRule(
                        ruleId,
                        rule
                )
        );
    }

    // =========================
    // BẬT RULE
    // =========================
    @PutMapping("/{ruleId}/enable")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public ResponseEntity<AlertRule> enableRule(
            @PathVariable Long ruleId) {

        return ResponseEntity.ok(
                alertRuleService.enableRule(
                        ruleId
                )
        );
    }

    // =========================
    // TẮT RULE
    // =========================
    @PutMapping("/{ruleId}/disable")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public ResponseEntity<AlertRule> disableRule(
            @PathVariable Long ruleId) {

        return ResponseEntity.ok(
                alertRuleService.disableRule(
                        ruleId
                )
        );
    }

    // =========================
    // XÓA RULE
    // =========================
    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('ALERT_MANAGE')")
    public ResponseEntity<String> deleteRule(
            @PathVariable Long ruleId) {

        alertRuleService.deleteRule(ruleId);

        return ResponseEntity.ok(
                "Xóa Alert Rule thành công"
        );
    }
}