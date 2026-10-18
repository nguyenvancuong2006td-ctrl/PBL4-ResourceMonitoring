package com.pbl4.server.scheduler;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OfflineDetectionScheduler {

    private final ClientAgentRepository clientAgentRepository;
    private final ClientRepository clientRepository;

    public OfflineDetectionScheduler(
            ClientAgentRepository clientAgentRepository,
            ClientRepository clientRepository) {

        this.clientAgentRepository = clientAgentRepository;
        this.clientRepository = clientRepository;
    }

    @Scheduled(fixedRate = 5000)
    public void checkOfflineAgents() {

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime timeout =
                now.minusSeconds(15);

        List<ClientAgent> onlineAgents =
                clientAgentRepository
                        .findByStatus(AgentStatus.ONLINE);

        for (ClientAgent agent : onlineAgents) {

            LocalDateTime lastHeartbeat =
                    agent.getLastHeartbeatAt();

            if (lastHeartbeat == null) {
                continue;
            }

            if (lastHeartbeat.isBefore(timeout)) {

                Client client =
                        agent.getClient();

                agent.setStatus(
                        AgentStatus.OFFLINE
                );

                agent.setDisconnectedAt(now);

                client.setStatus(
                        com.pbl4.server.enums.ClientStatus.OFFLINE
                );

                client.setUpdatedAt(now);

                clientRepository.save(client);
                clientAgentRepository.save(agent);

                System.out.println(
                        "Agent " + agent.getAgentId()
                                + " đã OFFLINE"
                );
            }
        }
    }
}