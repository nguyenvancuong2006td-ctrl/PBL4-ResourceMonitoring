package com.pbl4.server.controller;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.pbl4.server.dto.request.CreateRemoteCommandRequest;
import com.pbl4.server.dto.response.AgentRemoteCommandResponse;
import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.enums.CommandStatus;
import com.pbl4.server.enums.CommandType;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.RemoteCommand;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.service.RemoteCommandService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/remote/commands")
public class RemoteCommandController {

    private final RemoteCommandService remoteCommandService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;

    public RemoteCommandController(
            RemoteCommandService remoteCommandService,
            AccountRepository accountRepository,
            ObjectMapper objectMapper) {

        this.remoteCommandService = remoteCommandService;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Tạo lệnh Remote.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> createCommand(
            @Valid @RequestBody CreateRemoteCommandRequest request) {

        try {
            Authentication authentication =
                    SecurityContextHolder.getContext()
                            .getAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication.getName() == null
                    || "anonymousUser".equals(authentication.getName())) {

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "message",
                                "Chưa xác thực tài khoản."
                        ));
            }

            Account account = accountRepository
                    .findByUsername(authentication.getName())
                    .orElse(null);

            if (account == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "message",
                                "Không tìm thấy tài khoản đăng nhập."
                        ));
            }

            CommandType commandType = request.getCommandType();

            // Lệnh chia sẻ màn hình bắt buộc đi qua API phiên
            // (/api/screen-sharing/sessions) để server tạo và kiểm tra
            // phiên, sessionCode, quyền sở hữu. Không cho tạo trực tiếp
            // tại đây vì sẽ vượt qua các kiểm tra đó.
            if (commandType == CommandType.START_SCREEN_SHARING
                    || commandType == CommandType.STOP_SCREEN_SHARING) {

                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Lệnh chia sẻ màn hình phải dùng API "
                                        + "/api/screen-sharing/sessions."
                        ));
            }

            // Kiểm tra và dựng lại parameters theo từng loại lệnh
            String parameters = buildSafeParameters(
                    commandType,
                    request.getParameters()
            );

            RemoteCommand command =
                    remoteCommandService.createCommand(
                            account.getAccountId(),
                            request.getClientId(),
                            request.getCommandType(),
                            parameters
                    );

            Map<String, Object> response =
                    toSafeCommandResponse(command);

            response.put(
                    "message",
                    "Tạo lệnh Remote thành công."
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể tạo lệnh Remote."
            ));
        }
    }

    /**
     * Danh sách lệnh của TOÀN hệ thống (mới nhất trước, có phân trang).
     *
     * GET /api/remote/commands
     *     ?page=0&size=20&clientId=...&status=FAILED&commandType=SHUTDOWN
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('REMOTE_COMMAND','HISTORY_VIEW')")
    public ResponseEntity<?> getAllCommands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) CommandStatus status,
            @RequestParam(required = false) CommandType commandType) {

        try {
            return ResponseEntity.ok(
                    PageResponse.of(
                            remoteCommandService.searchCommands(
                                    page, size, clientId,
                                    status, commandType
                            ),
                            this::toSafeCommandResponse
                    )
            );

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể lấy danh sách lệnh Remote."
            ));
        }
    }

    /**
     * Lấy danh sách lệnh của một Client.
     */
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> getCommandsByClient(
            @PathVariable String clientId) {

        try {
            List<RemoteCommand> commands =
                    remoteCommandService.getCommandsByClient(clientId);

            List<Map<String, Object>> response =
                    commands.stream()
                            .map(this::toSafeCommandResponse)
                            .toList();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể lấy danh sách lệnh Remote."
            ));
        }
    }

    /**
     * Lấy chi tiết một lệnh Remote.
     */
    @GetMapping("/{commandId}")
    @PreAuthorize("hasAuthority('REMOTE_COMMAND')")
    public ResponseEntity<?> getCommandById(
            @PathVariable Long commandId) {

        try {
            RemoteCommand command =
                    remoteCommandService.getCommandById(commandId);

            return ResponseEntity.ok(
                    toSafeCommandResponse(command)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể lấy chi tiết lệnh Remote."
            ));
        }
    }

    /**
     * Agent lấy các lệnh đang chờ.
     */
    @PostMapping("/agent/{agentId}/pending")
    public ResponseEntity<?> getPendingCommandsForAgent(
            @PathVariable String agentId,
            @RequestHeader("X-Agent-Credential") String credential) {

        try {
            List<RemoteCommand> commands =
                    remoteCommandService.getPendingCommandsForAgent(
                            agentId,
                            credential
                    );

            List<AgentRemoteCommandResponse> response =
                    commands.stream()
                            .map(command -> {
                                JsonNode parameters = null;

                                if (command.getParameters() != null
                                        && !command.getParameters().isBlank()) {

                                    try {
                                        parameters = objectMapper.readTree(
                                                command.getParameters()
                                        );

                                    } catch (JacksonException e) {
                                        throw new IllegalStateException(
                                                "Tham số JSON của lệnh "
                                                        + command.getCommandId()
                                                        + " không hợp lệ.",
                                                e
                                        );
                                    }
                                }

                                return new AgentRemoteCommandResponse(
                                        command.getCommandId(),
                                        command.getCommandType(),
                                        command.getStatus(),
                                        parameters
                                );
                            })
                            .toList();

            return ResponseEntity.ok(response);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Agent credential không hợp lệ hoặc đã hết hiệu lực."
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể lấy lệnh cho Agent."
            ));
        }
    }

    /**
     * Agent báo bắt đầu thực thi lệnh.
     */
    @PostMapping("/agent/{agentId}/{commandId}/running")
    public ResponseEntity<?> markCommandRunning(
            @PathVariable String agentId,
            @PathVariable Long commandId,
            @RequestHeader("X-Agent-Credential") String credential) {

        try {
            RemoteCommand command =
                    remoteCommandService.markCommandAsRunningForAgent(
                            agentId,
                            credential,
                            commandId
                    );

            return ResponseEntity.ok(Map.of(
                    "commandId", command.getCommandId(),
                    "status", command.getStatus().name(),
                    "message", "Lệnh đã chuyển sang RUNNING."
            ));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Agent credential không hợp lệ."
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể cập nhật trạng thái lệnh."
            ));
        }
    }

    /**
     * Agent báo kết quả thực thi.
     */
    @PostMapping("/agent/{agentId}/{commandId}/result")
    public ResponseEntity<?> reportCommandResult(
            @PathVariable String agentId,
            @PathVariable Long commandId,
            @RequestHeader("X-Agent-Credential") String credential,
            @RequestBody Map<String, Object> body) {

        try {
            if (body == null
                    || !(body.get("success") instanceof Boolean)) {

                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "message",
                                "Trường success phải là true hoặc false."
                        ));
            }

            boolean success = (Boolean) body.get("success");

            String result =
                    body.get("result") instanceof String
                            ? (String) body.get("result")
                            : "";

            String errorMessage =
                    body.get("errorMessage") instanceof String
                            ? (String) body.get("errorMessage")
                            : "Lệnh thực thi thất bại.";

            RemoteCommand command =
                    remoteCommandService.reportCommandResultForAgent(
                            agentId,
                            credential,
                            commandId,
                            success,
                            result,
                            errorMessage
                    );

            return ResponseEntity.ok(Map.of(
                    "commandId", command.getCommandId(),
                    "status", command.getStatus().name(),
                    "message", "Đã cập nhật kết quả thực thi."
            ));

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message",
                            "Agent credential không hợp lệ."
                    ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            safeMessage(e)
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "message",
                    "Không thể cập nhật kết quả lệnh."
            ));
        }
    }

    /**
     * Chuyển RemoteCommand thành dữ liệu an toàn cho API.
     * Không trả trực tiếp entity Account hoặc Role.
     */
    private Map<String, Object> toSafeCommandResponse(
            RemoteCommand command) {

        Map<String, Object> response = new LinkedHashMap<>();

        response.put("commandId", command.getCommandId());

        response.put(
                "clientId",
                command.getClient() != null
                        ? command.getClient().getClientId()
                        : null
        );

        response.put(
                "commandType",
                command.getCommandType() != null
                        ? command.getCommandType().name()
                        : null
        );

        response.put(
                "status",
                command.getStatus() != null
                        ? command.getStatus().name()
                        : null
        );

        response.put("parameters", command.getParameters());
        response.put("result", command.getResult());
        response.put("errorMessage", command.getErrorMessage());
        response.put(
                "requestedBy",
                command.getAccount() != null
                        ? command.getAccount().getUsername()
                        : null
        );
        response.put("createdAt", command.getCreatedAt());
        response.put("sentAt", command.getSentAt());
        response.put("startedAt", command.getStartedAt());
        response.put("completedAt", command.getCompletedAt());

        return response;
    }

    /**
     * Kiểm tra parameters theo loại lệnh và dựng JSON MỚI để lưu,
     * không lưu nguyên dữ liệu client gửi lên.
     *
     *  - KILL_PROCESS      : bắt buộc có pid là số nguyên > 0.
     *  - SHUTDOWN, RESTART : không dùng tham số, bỏ qua parameters.
     *
     * @throws IllegalArgumentException nếu parameters không hợp lệ
     */
    private String buildSafeParameters(
            CommandType commandType,
            JsonNode parametersNode) {

        boolean empty = parametersNode == null
                || parametersNode.isNull();

        if (!empty && !parametersNode.isObject()) {
            throw new IllegalArgumentException(
                    "parameters phải là một JSON object.");
        }

        switch (commandType) {

            case KILL_PROCESS: {

                if (empty) {
                    throw new IllegalArgumentException(
                            "Lệnh KILL_PROCESS yêu cầu parameters.pid.");
                }

                JsonNode pidNode = parametersNode.get("pid");

                if (pidNode == null
                        || !pidNode.isIntegralNumber()
                        || !pidNode.canConvertToLong()) {

                    throw new IllegalArgumentException(
                            "pid phải là số nguyên hợp lệ.");
                }

                long pid = pidNode.longValue();

                if (pid <= 0) {
                    throw new IllegalArgumentException(
                            "pid phải lớn hơn 0.");
                }

                return "{\"pid\":" + pid + "}";
            }

            case SHUTDOWN:
            case RESTART:
                return null;

            default:
                throw new IllegalArgumentException(
                        "Loại lệnh không được hỗ trợ ở API này.");
        }
    }

    /**
     * Lấy thông báo lỗi an toàn.
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();

        return message == null || message.isBlank()
                ? "Yêu cầu không hợp lệ."
                : message;
    }
}