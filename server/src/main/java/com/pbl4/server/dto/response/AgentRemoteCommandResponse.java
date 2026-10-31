package com.pbl4.server.dto.response;

import tools.jackson.databind.JsonNode;
import com.pbl4.server.enums.CommandType;
import com.pbl4.server.enums.CommandStatus;

public class AgentRemoteCommandResponse {

    private Long commandId;
    private CommandType commandType;
    private CommandStatus status;
    private JsonNode parameters;

    public AgentRemoteCommandResponse() {
    }

    public AgentRemoteCommandResponse(
            Long commandId,
            CommandType commandType,
            CommandStatus status,
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

    public CommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(CommandType commandType) {
        this.commandType = commandType;
    }

    public CommandStatus getStatus() {
        return status;
    }

    public void setStatus(CommandStatus status) {
        this.status = status;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }
}