package com.pbl4.server.dto.response;

import com.pbl4.server.enums.CommandStatus;
import com.pbl4.server.enums.CommandType;

public class PendingRemoteCommandResponse {

    private Long commandId;
    private CommandType commandType;
    private CommandStatus status;

    public PendingRemoteCommandResponse() {
    }

    public PendingRemoteCommandResponse(
            Long commandId,
            CommandType commandType,
            CommandStatus status) {
        this.commandId = commandId;
        this.commandType = commandType;
        this.status = status;
    }

    public Long getCommandId() {
        return commandId;
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public CommandStatus getStatus() {
        return status;
    }
}