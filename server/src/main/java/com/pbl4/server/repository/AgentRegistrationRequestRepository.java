package com.pbl4.server.repository;

import com.pbl4.server.model.AgentRegistrationRequest;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgentRegistrationRequestRepository
        extends JpaRepository<AgentRegistrationRequest, Long> {

    // ==========================================
    // TÌM YÊU CẦU ĐĂNG KÝ THEO AGENT ID
    // ==========================================
    Optional<AgentRegistrationRequest> findByAgentAgentId(
            String agentId
    );

    // ==========================================
    // TÌM YÊU CẦU ĐĂNG KÝ THEO TOKEN HASH
    // ==========================================
    Optional<AgentRegistrationRequest> findByTokenHash(
            String tokenHash
    );

    // ==========================================
    // KHÓA YÊU CẦU ĐĂNG KÝ THEO AGENT ID
    // DÙNG KHI ADMIN PHÊ DUYỆT / TỪ CHỐI
    // ==========================================
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM AgentRegistrationRequest r
        WHERE r.agent.agentId = :agentId
    """)
    Optional<AgentRegistrationRequest> findByAgentAgentIdForUpdate(
            @Param("agentId") String agentId
    );

    // ==========================================
    // KHÓA YÊU CẦU ĐĂNG KÝ THEO TOKEN HASH
    // DÙNG KHI AGENT POLLING / NHẬN CREDENTIAL
    // ==========================================
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM AgentRegistrationRequest r
        WHERE r.tokenHash = :tokenHash
    """)
    Optional<AgentRegistrationRequest> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );
}