package com.pbl4.server.controller;

import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.service.ClientAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.pbl4.server.service.AgentCredentialService;

import java.util.List;

@RestController
@RequestMapping("/api/client-agents")
public class ClientAgentController {

    private final ClientAgentService clientAgentService;
    private final AgentCredentialService agentCredentialService;

    public ClientAgentController(
        ClientAgentService clientAgentService,
        AgentCredentialService agentCredentialService) {

    this.clientAgentService = clientAgentService;
    this.agentCredentialService = agentCredentialService;
}

    // =========================
    // THÊM CLIENT AGENT
    // =========================
    @PostMapping
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<ClientAgent> createAgent(
            @RequestParam String agentId,
            @RequestParam String clientId,
            @RequestParam String version) {

        ClientAgent agent =
                clientAgentService.createAgent(
                        agentId,
                        clientId,
                        version
                );

        return ResponseEntity.ok(agent);
    }

    // =========================
    // LẤY TẤT CẢ AGENT
    // =========================
    @GetMapping
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<List<ClientAgent>> getAllAgents() {

        return ResponseEntity.ok(
                clientAgentService.getAllAgents()
        );
    }

    // =========================
    // LẤY AGENT THEO ID
    // =========================
    @GetMapping("/{agentId}")
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<ClientAgent> getAgentById(
            @PathVariable String agentId) {

        return ResponseEntity.ok(
                clientAgentService.getAgentById(agentId)
        );
    }

    // =========================
    // LẤY AGENT THEO CLIENT ID
    // =========================
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<ClientAgent> getAgentByClientId(
            @PathVariable String clientId) {

        return ResponseEntity.ok(
                clientAgentService
                        .getAgentByClientId(clientId)
        );
    }

    // =========================
    // HEARTBEAT
    // =========================
    @PostMapping("/{agentId}/heartbeat")
public ResponseEntity<ClientAgent> heartbeat(
        @PathVariable String agentId,
        @RequestHeader("X-Agent-Credential") String credential) {

    boolean valid =
            agentCredentialService.validateCredential(
                    agentId,
                    credential
            );

    if (!valid) {
        return ResponseEntity.status(401).build();
    }

    return ResponseEntity.ok(
            clientAgentService.heartbeat(agentId)
    );
}

    // =========================
    // DISCONNECT
    // =========================
    // Tạm thời giữ để test.
    // Sau này sẽ chuyển sang Agent Authentication.
    @PostMapping("/{agentId}/disconnect")
    public ResponseEntity<ClientAgent> disconnect(
            @PathVariable String agentId) {

        return ResponseEntity.ok(
                clientAgentService.disconnect(agentId)
        );
    }

    // =========================
    // XÓA AGENT
    // =========================
    @DeleteMapping("/{agentId}")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<String> deleteAgent(
            @PathVariable String agentId) {

        clientAgentService.deleteAgent(agentId);

        return ResponseEntity.ok(
                "Xóa Client Agent thành công"
        );
    }
}