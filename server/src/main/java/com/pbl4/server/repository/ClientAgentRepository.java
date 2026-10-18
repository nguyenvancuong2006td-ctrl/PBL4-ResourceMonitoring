package com.pbl4.server.repository;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.model.ClientAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientAgentRepository
        extends JpaRepository<ClientAgent, String> {

    Optional<ClientAgent> findByClientClientId(String clientId);

    List<ClientAgent> findByStatus(AgentStatus status);
}