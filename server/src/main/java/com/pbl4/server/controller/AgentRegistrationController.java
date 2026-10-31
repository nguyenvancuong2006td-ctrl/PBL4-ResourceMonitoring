package com.pbl4.server.controller;

import com.pbl4.server.dto.request.AgentRegistrationRequest;
import com.pbl4.server.dto.request.AgentCredentialPollRequest;
import com.pbl4.server.dto.request.AgentCredentialAckRequest;

import com.pbl4.server.dto.response.AgentRegistrationResponse;
import com.pbl4.server.dto.response.AgentCredentialPollResponse;
import com.pbl4.server.dto.response.AgentCredentialAckResponse;

import com.pbl4.server.service.AgentRegistrationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent-registration")
public class AgentRegistrationController {

    private final AgentRegistrationService agentRegistrationService;

    public AgentRegistrationController(
            AgentRegistrationService agentRegistrationService) {
        this.agentRegistrationService = agentRegistrationService;
    }

    // ==================================================
    // 1. ĐĂNG KÝ AGENT
    // ==================================================

    /**
     * Agent gửi thông tin đăng ký lên Server.
     *
     * POST /api/agent-registration
     */
    @PostMapping
    public ResponseEntity<AgentRegistrationResponse> registerAgent(
            @RequestBody AgentRegistrationRequest request) {

        AgentRegistrationResponse response =
                agentRegistrationService.registerAgent(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // ==================================================
    // 2. POLL CREDENTIAL
    // ==================================================

    /**
     * Agent kiểm tra Credential đã được Admin phê duyệt hay chưa.
     *
     * POST /api/agent-registration/{agentId}/credential/poll
     *
     * Registration Token được gửi trong Request Body.
     */
    @PostMapping("/{agentId}/credential/poll")
    public ResponseEntity<AgentCredentialPollResponse> pollCredential(
            @PathVariable String agentId,
            @RequestBody AgentCredentialPollRequest request) {

        AgentCredentialPollResponse response =
                agentRegistrationService.pollCredential(
                        agentId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    // ==================================================
    // 3. ACK CREDENTIAL
    // ==================================================

    /**
     * Agent xác nhận đã nhận và lưu Credential thành công.
     *
     * POST /api/agent-registration/{agentId}/credential/ack
     *
     * Registration Token được gửi trong Request Body.
     */
    @PostMapping("/{agentId}/credential/ack")
    public ResponseEntity<AgentCredentialAckResponse> acknowledgeCredential(
            @PathVariable String agentId,
            @RequestBody AgentCredentialAckRequest request) {

        AgentCredentialAckResponse response =
                agentRegistrationService.acknowledgeCredential(
                        agentId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}