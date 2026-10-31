
package com.pbl4.server.service;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientAgentService {

    private final ClientAgentRepository clientAgentRepository;
    private final ClientRepository clientRepository;

    public ClientAgentService(
            ClientAgentRepository clientAgentRepository,
            ClientRepository clientRepository) {

        this.clientAgentRepository = clientAgentRepository;
        this.clientRepository = clientRepository;
    }

    // ==========================================
    // THÊM CLIENT AGENT
    // ==========================================
    @Transactional
    public ClientAgent createAgent(
            String agentId,
            String clientId,
            String version) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        final String normalizedAgentId = agentId.trim();
        final String normalizedClientId = clientId.trim();

        if (clientAgentRepository.existsById(normalizedAgentId)) {
            throw new IllegalArgumentException(
                    "Agent ID đã tồn tại."
            );
        }

        Client client = clientRepository
                .findById(normalizedClientId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Client: "
                                        + normalizedClientId
                        )
                );

        if (clientAgentRepository
                .findByClientClientId(normalizedClientId)
                .isPresent()) {

            throw new IllegalStateException(
                    "Client này đã có Agent liên kết."
            );
        }

        LocalDateTime now = LocalDateTime.now();

        ClientAgent agent = new ClientAgent();

        agent.setAgentId(normalizedAgentId);
        agent.setClient(client);
        agent.setVersion(version);
        agent.setStatus(AgentStatus.OFFLINE);

        agent.setRegisteredAt(now);
        agent.setLastConnectedAt(null);
        agent.setLastHeartbeatAt(null);
        agent.setDisconnectedAt(null);

        client.setStatus(ClientStatus.OFFLINE);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        return clientAgentRepository.save(agent);
    }

    // ==========================================
    // LẤY TẤT CẢ AGENT
    // ==========================================
    @Transactional(readOnly = true)
    public List<ClientAgent> getAllAgents() {

        return clientAgentRepository.findAll();
    }

    // ==========================================
    // LẤY AGENT THEO ID
    // ==========================================
    @Transactional(readOnly = true)
    public ClientAgent getAgentById(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        final String normalizedAgentId = agentId.trim();

        return clientAgentRepository
                .findById(normalizedAgentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Agent: "
                                        + normalizedAgentId
                        )
                );
    }

    // ==========================================
    // LẤY AGENT THEO CLIENT
    // ==========================================
    @Transactional(readOnly = true)
    public ClientAgent getAgentByClientId(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        final String normalizedClientId = clientId.trim();

        return clientAgentRepository
                .findByClientClientId(normalizedClientId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Client chưa có Agent."
                        )
                );
    }

    // ==========================================
    // KIỂM TRA AGENT ĐƯỢC PHÉP HOẠT ĐỘNG
    // ==========================================
    private void validateAgentCanConnect(ClientAgent agent) {

        AgentStatus status = agent.getStatus();

        if (status != AgentStatus.ONLINE
                && status != AgentStatus.OFFLINE) {

            throw new IllegalStateException(
                    "Agent chưa được phê duyệt hoặc đã bị vô hiệu hóa."
            );
        }
    }

    // ==========================================
    // CẬP NHẬT HEARTBEAT
    // ==========================================
    @Transactional
    public ClientAgent heartbeat(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        final String normalizedAgentId = agentId.trim();

        ClientAgent agent = clientAgentRepository
                .findById(normalizedAgentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Agent: "
                                        + normalizedAgentId
                        )
                );

        // Chỉ Agent đã được phê duyệt mới được kết nối
        validateAgentCanConnect(agent);

        LocalDateTime now = LocalDateTime.now();

        Client client = agent.getClient();

        // Nếu Agent vừa kết nối lại
        if (agent.getStatus() != AgentStatus.ONLINE) {
            agent.setLastConnectedAt(now);
        }

        agent.setStatus(AgentStatus.ONLINE);
        agent.setLastHeartbeatAt(now);
        agent.setDisconnectedAt(null);

        // Đồng bộ trạng thái Client
        client.setStatus(ClientStatus.ONLINE);
        client.setLastSeenAt(now);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        return clientAgentRepository.save(agent);
    }

    // ==========================================
    // ĐÁNH DẤU AGENT OFFLINE
    // ==========================================
    @Transactional
    public ClientAgent disconnect(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        final String normalizedAgentId = agentId.trim();

        ClientAgent agent = clientAgentRepository
                .findById(normalizedAgentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Agent: "
                                        + normalizedAgentId
                        )
                );

        // Không cho Agent chưa được duyệt hoặc bị khóa
        // thay đổi trạng thái kết nối
        validateAgentCanConnect(agent);

        LocalDateTime now = LocalDateTime.now();

        Client client = agent.getClient();

        // Chỉ ghi nhận thời điểm ngắt kết nối khi đang ONLINE
        if (agent.getStatus() == AgentStatus.ONLINE) {
            agent.setDisconnectedAt(now);
        }

        agent.setStatus(AgentStatus.OFFLINE);

        client.setStatus(ClientStatus.OFFLINE);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        return clientAgentRepository.save(agent);
    }

    // ==========================================
    // XÓA AGENT
    // ==========================================
    @Transactional
    public void deleteAgent(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        final String normalizedAgentId = agentId.trim();

        ClientAgent agent = clientAgentRepository
                .findById(normalizedAgentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Agent: "
                                        + normalizedAgentId
                        )
                );

        LocalDateTime now = LocalDateTime.now();

        Client client = agent.getClient();

        client.setStatus(ClientStatus.OFFLINE);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        clientAgentRepository.delete(agent);
    }
}
