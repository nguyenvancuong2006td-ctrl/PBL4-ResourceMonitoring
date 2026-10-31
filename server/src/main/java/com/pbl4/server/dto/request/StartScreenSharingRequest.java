package com.pbl4.server.dto.request;

import jakarta.validation.constraints.NotBlank;

public class StartScreenSharingRequest {

    @NotBlank(message = "Client ID không được để trống")
    private String clientId;

    public StartScreenSharingRequest() {
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}