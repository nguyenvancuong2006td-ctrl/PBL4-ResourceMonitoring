package com.pbl4.server.model;

import com.pbl4.server.enums.RegistrationStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "agent_registration_requests",
    indexes = {
        @Index(
            name = "idx_registration_status",
            columnList = "status"
        ),
        @Index(
            name = "idx_registration_expires_at",
            columnList = "expires_at"
        )
    }
)
public class AgentRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registration_id")
    private Long registrationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "agent_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_registration_agent")
    )
    private ClientAgent agent;

    @Column(
        name = "token_hash",
        nullable = false,
        length = 255
    )
    private String tokenHash;

    @Column(
        name = "credential_ciphertext",
        columnDefinition = "TEXT"
    )
    private String credentialCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "status",
        nullable = false,
        length = 20
    )
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "credential_delivered_at")
    private LocalDateTime credentialDeliveredAt;

    @Column(name = "credential_expires_at")
    private LocalDateTime credentialExpiresAt;

    public AgentRegistrationRequest() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = RegistrationStatus.PENDING;
        }
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public ClientAgent getAgent() {
        return agent;
    }

    public void setAgent(ClientAgent agent) {
        this.agent = agent;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getCredentialCiphertext() {
        return credentialCiphertext;
    }

    public void setCredentialCiphertext(String credentialCiphertext) {
        this.credentialCiphertext = credentialCiphertext;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public LocalDateTime getCredentialDeliveredAt() {
        return credentialDeliveredAt;
    }

    public void setCredentialDeliveredAt(LocalDateTime credentialDeliveredAt) {
        this.credentialDeliveredAt = credentialDeliveredAt;
    }

    public LocalDateTime getCredentialExpiresAt() {
        return credentialExpiresAt;
    }

    public void setCredentialExpiresAt(LocalDateTime credentialExpiresAt) {
        this.credentialExpiresAt = credentialExpiresAt;
    }
}