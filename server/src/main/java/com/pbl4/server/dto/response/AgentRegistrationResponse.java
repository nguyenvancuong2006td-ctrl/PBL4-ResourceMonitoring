package com.pbl4.server.dto.response;

public class AgentRegistrationResponse {

    private String agentId;
    private String clientId;
    private String status;
    private String message;

    public AgentRegistrationResponse() {
    }

    public AgentRegistrationResponse(
            String agentId,
            String clientId,
            String status,
            String message
    ) {
        this.agentId = agentId;
        this.clientId = clientId;
        this.status = status;
        this.message = message;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}