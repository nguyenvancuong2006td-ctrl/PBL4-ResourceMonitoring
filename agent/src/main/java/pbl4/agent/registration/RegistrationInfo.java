package pbl4.agent.registration;

import java.time.LocalDateTime;

/**
 * Trạng thái đăng ký của Agent được lưu cục bộ trên máy Client.
 *
 * Đối tượng này được CredentialStorage ghi xuống đĩa, nên Agent
 * có thể tiếp tục đúng chỗ dở dang sau khi khởi động lại.
 *
 * Lưu ý: chứa Credential, tuyệt đối không in nội dung ra log.
 */
public class RegistrationInfo {

    // Server đã đăng ký (để phát hiện Agent bị chuyển sang Server khác)
    private String serverUrl;

    // Do Server cấp trong phản hồi đăng ký (AGT-...)
    private String agentId;

    // Do Server cấp trong phản hồi đăng ký (CLI-...)
    private String clientId;

    // Do Agent tự sinh, Server chỉ lưu bản băm SHA-256
    private String registrationToken;

    private RegistrationState state;

    private LocalDateTime registeredAt;

    private LocalDateTime lastUpdatedAt;

    // Credential dùng cho header X-Agent-Credential
    private String credential;

    private LocalDateTime credentialExpiresAt;

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

    // ==========================================
    // TRẠNG THÁI CREDENTIAL
    // ==========================================

    public boolean hasCredential() {
        return credential != null && !credential.isBlank();
    }

    /**
     * Credential coi như hết hạn nếu không có thời hạn
     * hoặc thời điểm hiện tại đã chạm mốc hết hạn.
     * (Cùng quy tắc với Server: !now.isBefore(expiresAt))
     */
    public boolean isCredentialExpired(LocalDateTime now) {
        return credentialExpiresAt == null
                || !now.isBefore(credentialExpiresAt);
    }

    public void clearCredential() {
        this.credential = null;
        this.credentialExpiresAt = null;
        updateTimestamp();
    }

    // ==========================================
    // GETTER / SETTER
    // ==========================================

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        updateTimestamp();
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

    /**
     * Chỉ dùng khi nạp lại dữ liệu từ đĩa để giữ nguyên mốc thời gian gốc.
     */
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
        updateTimestamp();
    }

    public LocalDateTime getCredentialExpiresAt() {
        return credentialExpiresAt;
    }

    public void setCredentialExpiresAt(
            LocalDateTime credentialExpiresAt) {

        this.credentialExpiresAt = credentialExpiresAt;
        updateTimestamp();
    }

    private void updateTimestamp() {
        this.lastUpdatedAt = LocalDateTime.now();
    }

    /**
     * Không bao gồm Registration Token và Credential
     * để tránh lộ bí mật khi ghi log.
     */
    @Override
    public String toString() {
        return "RegistrationInfo{"
                + "agentId='" + agentId + '\''
                + ", clientId='" + clientId + '\''
                + ", state=" + state
                + ", registeredAt=" + registeredAt
                + ", hasCredential=" + hasCredential()
                + ", credentialExpiresAt=" + credentialExpiresAt
                + '}';
    }
}