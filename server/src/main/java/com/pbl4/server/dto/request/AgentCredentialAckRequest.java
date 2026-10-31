package com.pbl4.server.dto.request;

public class AgentCredentialAckRequest {

    private String registrationToken;

    public AgentCredentialAckRequest() {
    }

    public AgentCredentialAckRequest(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }
}