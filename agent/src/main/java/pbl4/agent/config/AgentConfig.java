package pbl4.agent.config;

public class AgentConfig {

    private String serverUrl;
    private String agentId;
    private String version;
    private String credential;

    public AgentConfig(
            String serverUrl,
            String agentId,
            String version,
            String credential) {

        this.serverUrl = serverUrl;
        this.agentId = agentId;
        this.version = version;
        this.credential = credential;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getVersion() {
        return version;
    }

    public String getCredential() {
        return credential;
    }
}