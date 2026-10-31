package com.pbl4.server.dto.request;

public class AgentCredentialPollRequest {

    private String registrationToken;

    public AgentCredentialPollRequest() {
    }

    public AgentCredentialPollRequest(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }
}