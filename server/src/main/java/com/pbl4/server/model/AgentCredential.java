package com.pbl4.server.model;

import com.pbl4.server.enums.CredentialStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_credentials")
public class AgentCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credential_id")
    private Long credentialId;

    @OneToOne
    @JoinColumn(
        name = "agent_id",
        nullable = false,
        unique = true
    )
    private ClientAgent agent;

    @Column(name = "credential_hash", length = 255, nullable = false)
    private String credentialHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private CredentialStatus status;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public AgentCredential() {
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public ClientAgent getAgent() {
        return agent;
    }

    public void setAgent(ClientAgent agent) {
        this.agent = agent;
    }

    public String getCredentialHash() {
        return credentialHash;
    }

    public void setCredentialHash(String credentialHash) {
        this.credentialHash = credentialHash;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public void setStatus(CredentialStatus status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}