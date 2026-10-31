package com.pbl4.server.dto.request;

import tools.jackson.databind.JsonNode;
import com.pbl4.server.enums.CommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateRemoteCommandRequest {

    @NotBlank(message = "Client ID không được để trống")
    private String clientId;

    @NotNull(message = "Loại lệnh không được để trống")
    private CommandType commandType;

    // Tham số bổ sung của lệnh, ví dụ PID cần dừng.
    private JsonNode parameters;

    public CreateRemoteCommandRequest() {
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public void setCommandType(CommandType commandType) {
        this.commandType = commandType;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }
}