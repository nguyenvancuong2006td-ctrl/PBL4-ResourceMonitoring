package com.pbl4.server.repository;

import com.pbl4.server.model.AgentCredential;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgentCredentialRepository
        extends JpaRepository<AgentCredential, Long> {

    // Tìm Credential theo Agent ID
    Optional<AgentCredential> findByAgentAgentId(String agentId);

    // Tìm và khóa Credential khi cần cấp lại
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM AgentCredential c
        WHERE c.agent.agentId = :agentId
    """)
    Optional<AgentCredential> findByAgentAgentIdForUpdate(
            @Param("agentId") String agentId
    );
}