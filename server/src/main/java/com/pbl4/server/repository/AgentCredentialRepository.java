package com.pbl4.server.repository;

import com.pbl4.server.model.AgentCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentCredentialRepository
        extends JpaRepository<AgentCredential, Long> {

    Optional<AgentCredential> findByAgentAgentId(String agentId);
}