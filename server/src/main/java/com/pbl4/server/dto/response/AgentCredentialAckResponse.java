package com.pbl4.server.dto.response;

import java.time.LocalDateTime;

public class AgentCredentialAckResponse {

    private String agentId;
    private String status;
    private LocalDateTime deliveredAt;
    private String message;

    public AgentCredentialAckResponse() {
    }

    public AgentCredentialAckResponse(
            String agentId,
            String status,
            LocalDateTime deliveredAt,
            String message
    ) {
        this.agentId = agentId;
        this.status = status;
        this.deliveredAt = deliveredAt;
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

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}