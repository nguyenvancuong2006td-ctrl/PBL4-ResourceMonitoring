package com.pbl4.server.service;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;
import org.springframework.stereotype.Service;

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

    // =========================
    // THÊM CLIENT AGENT
    // =========================
    public ClientAgent createAgent(
            String agentId,
            String clientId,
            String version) {

        if (clientAgentRepository.existsById(agentId)) {
            throw new RuntimeException(
                    "Agent ID đã tồn tại"
            );
        }

        Client client = clientRepository
                .findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Client: " + clientId
                        )
                );

        if (clientAgentRepository
                .findByClientClientId(clientId)
                .isPresent()) {

            throw new RuntimeException(
                    "Client này đã có Agent"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        ClientAgent agent = new ClientAgent();

        agent.setAgentId(agentId);
        agent.setClient(client);
        agent.setVersion(version);

        // Agent mới đăng ký nhưng chưa kết nối
        agent.setStatus(AgentStatus.OFFLINE);
        agent.setRegisteredAt(now);
        agent.setLastConnectedAt(null);
        agent.setLastHeartbeatAt(null);
        agent.setDisconnectedAt(null);

        return clientAgentRepository.save(agent);
    }

    // =========================
    // LẤY TẤT CẢ AGENT
    // =========================
    public List<ClientAgent> getAllAgents() {

        return clientAgentRepository.findAll();
    }

    // =========================
    // LẤY AGENT THEO ID
    // =========================
    public ClientAgent getAgentById(String agentId) {

        return clientAgentRepository
                .findById(agentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Agent: " + agentId
                        )
                );
    }

    // =========================
    // LẤY AGENT THEO CLIENT
    // =========================
    public ClientAgent getAgentByClientId(
            String clientId) {

        return clientAgentRepository
                .findByClientClientId(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Client chưa có Agent"
                        )
                );
    }

    // =========================
    // CẬP NHẬT HEARTBEAT
    // =========================
    public ClientAgent heartbeat(
            String agentId) {

        ClientAgent agent = clientAgentRepository
                .findById(agentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Agent: " + agentId
                        )
                );

        LocalDateTime now = LocalDateTime.now();

        Client client = agent.getClient();

        // Cập nhật Agent
        agent.setStatus(AgentStatus.ONLINE);
        agent.setLastHeartbeatAt(now);
        agent.setDisconnectedAt(null);

        // Cập nhật Client
        client.setStatus(ClientStatus.ONLINE);
        client.setLastSeenAt(now);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        return clientAgentRepository.save(agent);
    }

    // =========================
    // ĐÁNH DẤU AGENT OFFLINE
    // =========================
    public ClientAgent disconnect(
            String agentId) {

        ClientAgent agent = clientAgentRepository
                .findById(agentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Agent: " + agentId
                        )
                );

        LocalDateTime now = LocalDateTime.now();

        Client client = agent.getClient();

        // Cập nhật Agent
        agent.setStatus(AgentStatus.OFFLINE);
        agent.setDisconnectedAt(now);

        // Cập nhật Client
        client.setStatus(ClientStatus.OFFLINE);
        client.setUpdatedAt(now);

        clientRepository.save(client);

        return clientAgentRepository.save(agent);
    }

    // =========================
    // XÓA AGENT
    // =========================
    public void deleteAgent(String agentId) {

        if (!clientAgentRepository.existsById(agentId)) {

            throw new RuntimeException(
                    "Không tìm thấy Agent: " + agentId
            );
        }

        clientAgentRepository.deleteById(agentId);
    }
}