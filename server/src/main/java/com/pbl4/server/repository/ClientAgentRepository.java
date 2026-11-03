
package com.pbl4.server.repository;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.model.ClientAgent;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientAgentRepository
        extends JpaRepository<ClientAgent, String> {

    // Tìm Agent theo Client ID
    Optional<ClientAgent> findByClientClientId(String clientId);

    // Tìm Agent theo trạng thái
    List<ClientAgent> findByStatus(AgentStatus status);

    // Phân trang theo trạng thái
    Page<ClientAgent> findByStatus(AgentStatus status, Pageable pageable);

    long countByStatus(AgentStatus status);

    /**
     * Tìm và khóa bản ghi Agent trong transaction.
     *
     * Ngăn các transaction khác đồng thời cấp Credential
     * cho cùng một Agent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM ClientAgent a
        WHERE a.agentId = :agentId
    """)
    Optional<ClientAgent> findByAgentIdForUpdate(
            @Param("agentId") String agentId
    );
}