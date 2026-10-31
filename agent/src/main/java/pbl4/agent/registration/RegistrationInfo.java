package pbl4.agent.registration;

import java.time.LocalDateTime;

public class RegistrationInfo {

    private String agentId;

    private String clientId;

    private String registrationToken;

    private RegistrationState state;

    private LocalDateTime registeredAt;

    private LocalDateTime lastUpdatedAt;

    public RegistrationInfo() {
        this.state = RegistrationState.NOT_REGISTERED;
    }

    public RegistrationInfo(
            String agentId,
            String clientId,
            String registrationToken,
            RegistrationState state
    ) {
        this.agentId = agentId;
        this.clientId = clientId;
        this.registrationToken = registrationToken;
        this.state = state;
        this.lastUpdatedAt = LocalDateTime.now();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
        updateTimestamp();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
        updateTimestamp();
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
        updateTimestamp();
    }

    public RegistrationState getState() {
        return state;
    }

    public void setState(RegistrationState state) {
        this.state = state;
        updateTimestamp();

        if (state == RegistrationState.REGISTERED) {
            this.registeredAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    private void updateTimestamp() {
        this.lastUpdatedAt = LocalDateTime.now();
    }
}