package com.pbl4.server.controller;

import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.service.AgentCredentialService;
import com.pbl4.server.service.AgentRegistrationService;
import com.pbl4.server.service.ClientAgentService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/client-agents")
public class ClientAgentController {

    private final ClientAgentService clientAgentService;
    private final AgentCredentialService agentCredentialService;
    private final AgentRegistrationService agentRegistrationService;

    public ClientAgentController(
            ClientAgentService clientAgentService,
            AgentCredentialService agentCredentialService,
            AgentRegistrationService agentRegistrationService) {

        this.clientAgentService = clientAgentService;
        this.agentCredentialService = agentCredentialService;
        this.agentRegistrationService = agentRegistrationService;
    }

    // ==========================================
    // THÊM CLIENT AGENT
    // ==========================================

    @PostMapping
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<ClientAgent> createAgent(
            @RequestParam String agentId,
            @RequestParam String clientId,
            @RequestParam String version) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không được để trống"
            );
        }

        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID không được để trống"
            );
        }

        if (version == null || version.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent Version không được để trống"
            );
        }

        ClientAgent agent = clientAgentService.createAgent(
                agentId.trim(),
                clientId.trim(),
                version.trim()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(agent);
    }

    // ==========================================
    // LẤY TẤT CẢ CLIENT AGENT
    // ==========================================

    @GetMapping
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<List<ClientAgent>> getAllAgents() {

        List<ClientAgent> agents =
                clientAgentService.getAllAgents();

        return ResponseEntity.ok(agents);
    }

    // ==========================================
    // DANH SÁCH AGENT CÓ PHÂN TRANG
    // page bắt đầu từ 0, size tối đa 100, status = lọc trạng thái
    // ==========================================

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<PageResponse<ClientAgent>> getAgentsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AgentStatus status) {

        return ResponseEntity.ok(
                clientAgentService.getAgentsPage(page, size, status)
        );
    }

    // ==========================================
    // LẤY CLIENT AGENT THEO AGENT ID
    // ==========================================

    @GetMapping("/{agentId}")
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<ClientAgent> getAgentById(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        ClientAgent agent =
                clientAgentService.getAgentById(agentId.trim());

        return ResponseEntity.ok(agent);
    }

    // ==========================================
    // LẤY CLIENT AGENT THEO CLIENT ID
    // ==========================================

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAuthority('AGENT_VIEW')")
    public ResponseEntity<ClientAgent> getAgentByClientId(
            @PathVariable String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID không hợp lệ"
            );
        }

        ClientAgent agent =
                clientAgentService.getAgentByClientId(clientId.trim());

        return ResponseEntity.ok(agent);
    }

    // ==========================================
    // PHÊ DUYỆT AGENT
    // ==========================================

    @PostMapping("/{agentId}/approve")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<String> approveAgent(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        try {
            agentRegistrationService.approveAgent(agentId.trim());

            return ResponseEntity.ok(
                    "Phê duyệt Agent thành công. Credential đã được tạo và mã hóa."
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }

    // ==========================================
    // TỪ CHỐI AGENT
    // ==========================================

    @PostMapping("/{agentId}/reject")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<String> rejectAgent(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        try {
            agentRegistrationService.rejectAgent(agentId.trim());

            return ResponseEntity.ok(
                    "Từ chối yêu cầu đăng ký Agent thành công."
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }

    // ==========================================
    // THU HỒI AGENT
    // ==========================================

    @PostMapping("/{agentId}/revoke")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<String> revokeAgent(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        try {
            agentRegistrationService.revokeAgent(agentId.trim());

            return ResponseEntity.ok(
                    "Đã thu hồi Agent. Credential không còn hiệu lực."
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }

    // ==========================================
    // HEARTBEAT
    // ==========================================

    @PostMapping("/{agentId}/heartbeat")
    public ResponseEntity<ClientAgent> heartbeat(
            @PathVariable String agentId,
            @RequestHeader(
                    value = "X-Agent-Credential",
                    required = false
            ) String credential) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        if (credential == null || credential.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Thiếu Agent Credential"
            );
        }

        boolean valid = agentCredentialService.validateCredential(
                agentId.trim(),
                credential.trim()
        );

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Agent Credential không hợp lệ"
            );
        }

        ClientAgent agent =
                clientAgentService.heartbeat(agentId.trim());

        return ResponseEntity.ok(agent);
    }

    // ==========================================
    // NGẮT KẾT NỐI CLIENT AGENT
    // ==========================================

    @PostMapping("/{agentId}/disconnect")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<ClientAgent> disconnect(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        ClientAgent agent =
                clientAgentService.disconnect(agentId.trim());

        return ResponseEntity.ok(agent);
    }

    // ==========================================
    // XÓA CLIENT AGENT
    // Xóa luôn Client và mọi dữ liệu liên quan (không thể hoàn tác)
    // ==========================================

    @DeleteMapping("/{agentId}")
    @PreAuthorize("hasAuthority('AGENT_MANAGE')")
    public ResponseEntity<String> deleteAgent(
            @PathVariable String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID không hợp lệ"
            );
        }

        try {
            clientAgentService.deleteAgent(agentId.trim());

            return ResponseEntity.ok(
                    "Xóa Client Agent thành công. "
                            + "Client và toàn bộ dữ liệu liên quan đã được xóa."
            );

        } catch (IllegalArgumentException e) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );

        } catch (IllegalStateException e) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }
}