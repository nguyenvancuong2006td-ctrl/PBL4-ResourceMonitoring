package com.pbl4.server.dto.response;

import java.time.LocalDateTime;

public class AgentCredentialPollResponse {

    private String agentId;
    private String status;
    private String credential;
    private LocalDateTime expiresAt;
    private String message;

    public AgentCredentialPollResponse() {
    }

    public AgentCredentialPollResponse(
            String agentId,
            String status,
            String credential,
            LocalDateTime expiresAt,
            String message
    ) {
        this.agentId = agentId;
        this.status = status;
        this.credential = credential;
        this.expiresAt = expiresAt;
        this.message = message;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}