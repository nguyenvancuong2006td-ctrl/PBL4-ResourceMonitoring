package pbl4.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

public class RemoteCommandData {

    private Long commandId;
    private String commandType;
    private String status;
    private JsonNode parameters;

    public RemoteCommandData() {
    }

    public RemoteCommandData(
            Long commandId,
            String commandType,
            String status,
            JsonNode parameters) {

        this.commandId = commandId;
        this.commandType = commandType;
        this.status = status;
        this.parameters = parameters;
    }

    public Long getCommandId() {
        return commandId;
    }

    public void setCommandId(Long commandId) {
        this.commandId = commandId;
    }

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "RemoteCommandData{" +
                "commandId=" + commandId +
                ", commandType='" + commandType + '\'' +
                ", status='" + status + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}