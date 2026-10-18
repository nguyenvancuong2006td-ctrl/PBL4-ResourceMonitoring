package com.pbl4.server.controller;

import com.pbl4.server.model.AgentCredential;
import com.pbl4.server.service.AgentCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agent-credentials")
public class AgentCredentialController {

    private final AgentCredentialService agentCredentialService;

    public AgentCredentialController(
            AgentCredentialService agentCredentialService) {

        this.agentCredentialService = agentCredentialService;
    }

    // =========================
    // TẠO CREDENTIAL
    // =========================
    @PostMapping
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<String> createCredential(
            @RequestParam String agentId) {

        String credential =
                agentCredentialService.createCredential(
                        agentId
                );

        return ResponseEntity.ok(credential);
    }

    // =========================
    // LẤY TẤT CẢ CREDENTIAL
    // =========================
    @GetMapping
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<List<AgentCredential>>
    getAllCredentials() {

        return ResponseEntity.ok(
                agentCredentialService
                        .getAllCredentials()
        );
    }

    // =========================
    // LẤY CREDENTIAL THEO ID
    // =========================
    @GetMapping("/{credentialId}")
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<AgentCredential>
    getCredentialById(
            @PathVariable Long credentialId) {

        return ResponseEntity.ok(
                agentCredentialService
                        .getCredentialById(credentialId)
        );
    }

    // =========================
    // LẤY CREDENTIAL THEO AGENT
    // =========================
    @GetMapping("/agent/{agentId}")
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<AgentCredential>
    getCredentialByAgentId(
            @PathVariable String agentId) {

        return ResponseEntity.ok(
                agentCredentialService
                        .getCredentialByAgentId(agentId)
        );
    }

    // =========================
    // THU HỒI CREDENTIAL
    // =========================
    @PutMapping("/{credentialId}/revoke")
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<String> revokeCredential(
            @PathVariable Long credentialId) {

        agentCredentialService.revokeCredential(
                credentialId
        );

        return ResponseEntity.ok(
                "Đã thu hồi Agent Credential"
        );
    }

    // =========================
    // VÔ HIỆU HÓA CREDENTIAL
    // =========================
    @PutMapping("/{credentialId}/disable")
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<String> disableCredential(
            @PathVariable Long credentialId) {

        agentCredentialService.disableCredential(
                credentialId
        );

        return ResponseEntity.ok(
                "Đã vô hiệu hóa Agent Credential"
        );
    }

    // =========================
    // KÍCH HOẠT CREDENTIAL
    // =========================
    @PutMapping("/{credentialId}/activate")
    @PreAuthorize("hasAuthority('CREDENTIAL_MANAGE')")
    public ResponseEntity<String> activateCredential(
            @PathVariable Long credentialId) {

        agentCredentialService.activateCredential(
                credentialId
        );

        return ResponseEntity.ok(
                "Đã kích hoạt Agent Credential"
        );
    }
}