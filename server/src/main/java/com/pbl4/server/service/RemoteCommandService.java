package com.pbl4.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.pbl4.server.enums.CommandStatus;
import com.pbl4.server.enums.CommandType;
import com.pbl4.server.enums.ScreenSharingStatus;

import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.model.RemoteCommand;
import com.pbl4.server.model.ScreenSharingSession;

import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.RemoteCommandRepository;
import com.pbl4.server.repository.ScreenSharingSessionRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RemoteCommandService {

    private final RemoteCommandRepository remoteCommandRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final ClientAgentRepository clientAgentRepository;
    private final AgentCredentialService agentCredentialService;

    private final ScreenSharingSessionRepository screenSharingSessionRepository;
    private final ObjectMapper objectMapper;

    // Lệnh PENDING quá thời gian này mà Agent chưa nhận thì bị TIMEOUT.
    // Agent poll mỗi 5 giây nên 60 giây là đủ rộng; Agent offline thì lệnh
    // không được giữ lại để thực thi muộn (ví dụ SHUTDOWN/RESTART cũ).
    private static final long PENDING_TIMEOUT_SECONDS = 60;

    // Lệnh SENT quá thời gian này mà Agent chưa báo RUNNING thì bị TIMEOUT.
    private static final long SENT_TIMEOUT_SECONDS = 60;

    // Lệnh RUNNING quá thời gian này mà Agent chưa báo kết quả thì bị TIMEOUT.
    private static final long RUNNING_TIMEOUT_SECONDS = 300;

    public RemoteCommandService(
            RemoteCommandRepository remoteCommandRepository,
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            ClientAgentRepository clientAgentRepository,
            AgentCredentialService agentCredentialService,
            ScreenSharingSessionRepository screenSharingSessionRepository,
            ObjectMapper objectMapper) {

        this.remoteCommandRepository = remoteCommandRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.clientAgentRepository = clientAgentRepository;
        this.agentCredentialService = agentCredentialService;
        this.screenSharingSessionRepository = screenSharingSessionRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Tạo lệnh Remote mới, có thể kèm tham số JSON.
     */
    @Transactional
    public RemoteCommand createCommand(
            Long accountId,
            String clientId,
            CommandType commandType,
            String parameters) {

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID không được để trống.");
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống.");
        }

        if (commandType == null) {
            throw new IllegalArgumentException(
                    "Loại lệnh không được để trống.");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy tài khoản: " + accountId));

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Client: " + clientId));

        RemoteCommand command = new RemoteCommand();

        command.setAccount(account);
        command.setClient(client);
        command.setCommandType(commandType);
        command.setParameters(parameters);
        command.setStatus(CommandStatus.PENDING);

        command.setResult(null);
        command.setErrorMessage(null);
        command.setSentAt(null);
        command.setStartedAt(null);
        command.setCompletedAt(null);

        return remoteCommandRepository.save(command);
    }

    /**
     * Lấy danh sách lệnh của một Client.
     */
    @Transactional(readOnly = true)
    public List<RemoteCommand> getCommandsByClient(String clientId) {

        validateClientId(clientId);

        if (!clientRepository.existsById(clientId)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy Client: " + clientId);
        }

        return remoteCommandRepository
                .findByClientClientId(clientId);
    }

    /**
     * Danh sách lệnh của TOÀN hệ thống (mới nhất trước, có phân trang).
     */
    @Transactional(readOnly = true)
    public Page<RemoteCommand> searchCommands(
            int page,
            int size,
            String clientId,
            CommandStatus status,
            CommandType commandType) {

        String client = (clientId == null || clientId.isBlank())
                ? null
                : clientId.trim();

        return remoteCommandRepository.search(
                client,
                status,
                commandType,
                PageResponse.pageable(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "commandId")
                )
        );
    }

    /**
     * Lấy chi tiết một lệnh Remote.
     */
    @Transactional(readOnly = true)
    public RemoteCommand getCommandById(Long commandId) {

        if (commandId == null) {
            throw new IllegalArgumentException(
                    "Command ID không được để trống.");
        }

        return remoteCommandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lệnh Remote: " + commandId));
    }

    /**
     * Chuyển lệnh PENDING sang SENT.
     */
    @Transactional
    public RemoteCommand markCommandAsSent(Long commandId) {

        RemoteCommand command = getCommandById(commandId);

        if (command.getStatus() != CommandStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ lệnh PENDING mới được chuyển sang SENT.");
        }

        command.setStatus(CommandStatus.SENT);
        command.setSentAt(LocalDateTime.now());

        return remoteCommandRepository.save(command);
    }

    /**
     * Chuyển lệnh SENT sang RUNNING.
     */
    @Transactional
    public RemoteCommand markCommandAsRunning(Long commandId) {

        RemoteCommand command = getCommandById(commandId);

        if (command.getStatus() != CommandStatus.SENT) {
            throw new IllegalStateException(
                    "Chỉ lệnh SENT mới được chuyển sang RUNNING.");
        }

        command.setStatus(CommandStatus.RUNNING);
        command.setStartedAt(LocalDateTime.now());

        return remoteCommandRepository.save(command);
    }

    /**
     * Đánh dấu lệnh thực thi thành công.
     */
    @Transactional
    public RemoteCommand completeCommand(
            Long commandId,
            String result) {

        RemoteCommand command = getCommandById(commandId);

        if (command.getStatus() != CommandStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ lệnh RUNNING mới được hoàn thành.");
        }

        command.setStatus(CommandStatus.COMPLETED);
        command.setResult(result);
        command.setErrorMessage(null);
        command.setCompletedAt(LocalDateTime.now());

        return remoteCommandRepository.save(command);
    }

    /**
     * Đánh dấu lệnh thực thi thất bại.
     */
    @Transactional
    public RemoteCommand failCommand(
            Long commandId,
            String errorMessage) {

        RemoteCommand command = getCommandById(commandId);

        if (command.getStatus() != CommandStatus.SENT
                && command.getStatus() != CommandStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ lệnh SENT hoặc RUNNING mới được đánh dấu FAILED.");
        }

        command.setStatus(CommandStatus.FAILED);
        command.setResult(null);
        command.setErrorMessage(
                errorMessage == null || errorMessage.isBlank()
                        ? "Lệnh thực thi thất bại."
                        : errorMessage);
        command.setCompletedAt(LocalDateTime.now());

        return remoteCommandRepository.save(command);
    }

    /**
     * Đánh dấu lệnh hết thời gian chờ.
     */
    @Transactional
    public RemoteCommand timeoutCommand(Long commandId) {

        RemoteCommand command = getCommandById(commandId);

        if (command.getStatus() != CommandStatus.PENDING
                && command.getStatus() != CommandStatus.SENT
                && command.getStatus() != CommandStatus.RUNNING) {
            throw new IllegalStateException(
                    "Không thể timeout lệnh ở trạng thái hiện tại.");
        }

        command.setStatus(CommandStatus.TIMEOUT);
        command.setErrorMessage(
                "Lệnh đã hết thời gian chờ thực thi.");
        command.setCompletedAt(LocalDateTime.now());

        return remoteCommandRepository.save(command);
    }

    /**
     * Lấy các lệnh PENDING của Client.
     */
    @Transactional(readOnly = true)
    public List<RemoteCommand> getPendingCommands(String clientId) {

        validateClientId(clientId);

        if (!clientRepository.existsById(clientId)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy Client: " + clientId);
        }

        return remoteCommandRepository
                .findByClientClientIdAndStatusOrderBySentAtAsc(
                        clientId,
                        CommandStatus.PENDING);
    }

    /**
     * Agent lấy các lệnh đang chờ.
     *
     * Sau khi lấy được danh sách, các lệnh chuyển từ PENDING
     * sang SENT để không tiếp tục xuất hiện ở các lần polling sau.
     */
    @Transactional
    public List<RemoteCommand> getPendingCommandsForAgent(
            String agentId,
            String rawCredential) {

        ClientAgent agent = validateAgent(
                agentId,
                rawCredential);

        String clientId = agent.getClient().getClientId();

        List<RemoteCommand> commands =
                remoteCommandRepository.findPendingCommandsForUpdate(
                        clientId,
                        CommandStatus.PENDING
                );

        if (commands.isEmpty()) {
            return commands;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pendingCutoff =
                now.minusSeconds(PENDING_TIMEOUT_SECONDS);

        List<RemoteCommand> deliverable = new ArrayList<>();

        for (RemoteCommand command : commands) {

            // Lệnh đã chờ quá lâu (Agent vừa online lại sau thời gian dài):
            // không giao nữa, đánh dấu TIMEOUT.
            if (command.getCreatedAt() == null
                    || command.getCreatedAt().isBefore(pendingCutoff)) {

                markAsTimedOut(
                        command,
                        now,
                        "Lệnh đã quá hạn chờ Agent nhận ("
                                + PENDING_TIMEOUT_SECONDS + " giây)."
                );

                continue;
            }

            command.setStatus(CommandStatus.SENT);
            command.setSentAt(now);

            deliverable.add(command);
        }

        // Lưu cả lệnh được giao (SENT) lẫn lệnh quá hạn (TIMEOUT)
        remoteCommandRepository.saveAll(commands);

        return deliverable;
    }

    /**
     * Đánh dấu TIMEOUT cho các lệnh bị treo.
     *
     * Được gọi định kỳ bởi CommandTimeoutScheduler.
     *
     *  - PENDING: Agent không nhận lệnh (thường do Agent offline).
     *  - SENT   : Agent đã lấy lệnh nhưng không báo RUNNING.
     *  - RUNNING: Agent không báo kết quả.
     *
     * @return số lệnh đã chuyển sang TIMEOUT
     */
    @Transactional
    public int expireStaleCommands() {

        LocalDateTime now = LocalDateTime.now();
        int expired = 0;

        for (RemoteCommand command
                : remoteCommandRepository.findStalePendingForUpdate(
                        now.minusSeconds(PENDING_TIMEOUT_SECONDS))) {

            markAsTimedOut(
                    command,
                    now,
                    "Agent không nhận lệnh trong "
                            + PENDING_TIMEOUT_SECONDS
                            + " giây (Agent có thể đang offline)."
            );

            remoteCommandRepository.save(command);
            expired++;
        }

        for (RemoteCommand command
                : remoteCommandRepository.findStaleSentForUpdate(
                        now.minusSeconds(SENT_TIMEOUT_SECONDS))) {

            markAsTimedOut(
                    command,
                    now,
                    "Agent đã nhận lệnh nhưng không bắt đầu thực thi trong "
                            + SENT_TIMEOUT_SECONDS + " giây."
            );

            remoteCommandRepository.save(command);
            expired++;
        }

        for (RemoteCommand command
                : remoteCommandRepository.findStaleRunningForUpdate(
                        now.minusSeconds(RUNNING_TIMEOUT_SECONDS))) {

            markAsTimedOut(
                    command,
                    now,
                    "Agent không báo kết quả thực thi trong "
                            + RUNNING_TIMEOUT_SECONDS + " giây."
            );

            remoteCommandRepository.save(command);
            expired++;
        }

        return expired;
    }

    /**
     * Chuyển lệnh sang TIMEOUT (chưa lưu, người gọi tự lưu) và đồng bộ
     * phiên chia sẻ màn hình nếu đó là lệnh bắt đầu chia sẻ.
     */
    private void markAsTimedOut(
            RemoteCommand command,
            LocalDateTime now,
            String message) {

        command.setStatus(CommandStatus.TIMEOUT);
        command.setResult(null);
        command.setErrorMessage(message);
        command.setCompletedAt(now);

        failSessionOnStartTimeout(command, now);
    }

    /**
     * Lệnh START_SCREEN_SHARING hết hạn thì phiên tương ứng không thể bắt đầu:
     * chuyển phiên đang REQUESTED/STARTING sang FAILED ngay thay vì chờ
     * ScreenSharingSessionScheduler.
     *
     * Lệnh STOP_SCREEN_SHARING không xử lý ở đây: phiên STOPPING tự kết thúc
     * sau STOP_TIMEOUT_SECONDS bởi ScreenSharingSessionScheduler.
     */
    private void failSessionOnStartTimeout(
            RemoteCommand command,
            LocalDateTime now) {

        if (command.getCommandType()
                != CommandType.START_SCREEN_SHARING) {
            return;
        }

        try {
            ScreenSharingSession session = getSessionFromCommand(command);

            if (session.getStatus() == ScreenSharingStatus.REQUESTED
                    || session.getStatus()
                            == ScreenSharingStatus.STARTING) {

                session.setStatus(ScreenSharingStatus.FAILED);
                session.setEndedAt(now);
                session.setEndReason("START_TIMEOUT");
                session.setErrorMessage(
                        "Agent không thực thi lệnh bắt đầu chia sẻ "
                        + "màn hình trong thời gian cho phép.");

                screenSharingSessionRepository.save(session);
            }

        } catch (RuntimeException e) {
            // Lệnh tạo thủ công không có sessionCode hợp lệ, hoặc phiên
            // đã bị xóa: không có phiên để đồng bộ.
        }
    }

    /**
     * Agent báo bắt đầu thực thi lệnh.
     */
    @Transactional
    public RemoteCommand markCommandAsRunningForAgent(
            String agentId,
            String rawCredential,
            Long commandId) {

        ClientAgent agent = validateAgent(
                agentId,
                rawCredential);

        RemoteCommand command = getCommandById(commandId);

        validateCommandBelongsToAgent(command, agent);

        if (command.getStatus() != CommandStatus.SENT) {
            throw new IllegalStateException(
                    "Chỉ lệnh SENT mới được chuyển sang RUNNING.");
        }

        command.setStatus(CommandStatus.RUNNING);
        command.setStartedAt(LocalDateTime.now());

        // Đồng bộ trạng thái phiên chia sẻ màn hình.
        if (command.getCommandType()
                == CommandType.START_SCREEN_SHARING) {

            ScreenSharingSession session =
                    getSessionFromCommand(command);

            if (session.getStatus()
                    == ScreenSharingStatus.REQUESTED) {

                session.setStatus(ScreenSharingStatus.STARTING);

                screenSharingSessionRepository.save(session);
            }
        }

        return remoteCommandRepository.save(command);
    }

    /**
     * Agent gửi kết quả sau khi thực thi lệnh.
     */
    @Transactional
    public RemoteCommand reportCommandResultForAgent(
            String agentId,
            String rawCredential,
            Long commandId,
            boolean success,
            String result,
            String errorMessage) {

        ClientAgent agent = validateAgent(
                agentId,
                rawCredential);

        RemoteCommand command = getCommandById(commandId);

        validateCommandBelongsToAgent(command, agent);

        if (command.getStatus() != CommandStatus.RUNNING) {
            throw new IllegalStateException(
                    "Chỉ lệnh RUNNING mới được cập nhật kết quả.");
        }

        command.setCompletedAt(LocalDateTime.now());

        if (success) {
            command.setStatus(CommandStatus.COMPLETED);
            command.setResult(result == null ? "" : result);
            command.setErrorMessage(null);
        } else {
            command.setStatus(CommandStatus.FAILED);
            command.setResult(null);
            command.setErrorMessage(
                    errorMessage == null || errorMessage.isBlank()
                            ? "Lệnh thực thi thất bại."
                            : errorMessage);
        }

        // Đồng bộ trạng thái phiên chia sẻ màn hình.
        updateScreenSharingSession(
                command,
                success,
                errorMessage
        );

        return remoteCommandRepository.save(command);
    }

    /**
     * Lấy phiên chia sẻ từ sessionCode trong parameters của lệnh.
     */
    private ScreenSharingSession getSessionFromCommand(
            RemoteCommand command) {

        try {
            if (command.getParameters() == null
                    || command.getParameters().isBlank()) {

                throw new IllegalStateException(
                        "Lệnh chia sẻ không có parameters.");
            }

            JsonNode jsonNode =
                    objectMapper.readTree(command.getParameters());

            JsonNode sessionCodeNode = jsonNode.get("sessionCode");

            if (sessionCodeNode == null
                    || sessionCodeNode.asText().isBlank()) {

                throw new IllegalStateException(
                        "Không tìm thấy sessionCode trong lệnh.");
            }

            String sessionCode = sessionCodeNode.asText();

            ScreenSharingSession session =
                    screenSharingSessionRepository
                            .findBySessionCode(sessionCode)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Không tìm thấy phiên chia sẻ: "
                                            + sessionCode));

            if (session.getClient() == null
                    || command.getClient() == null
                    || !session.getClient().getClientId()
                            .equals(command.getClient().getClientId())) {

                throw new SecurityException(
                        "Phiên chia sẻ không thuộc Client của lệnh.");
            }

            return session;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Không thể đọc parameters của lệnh chia sẻ.", e);
        }
    }

    /**
     * Cập nhật trạng thái phiên sau khi Agent báo kết quả.
     */
    private void updateScreenSharingSession(
            RemoteCommand command,
            boolean success,
            String errorMessage) {

        CommandType commandType = command.getCommandType();

        // Chỉ xử lý hai lệnh chia sẻ màn hình.
        if (commandType != CommandType.START_SCREEN_SHARING
                && commandType != CommandType.STOP_SCREEN_SHARING) {

            return;
        }

        ScreenSharingSession session =
                getSessionFromCommand(command);

        LocalDateTime now = LocalDateTime.now();

        if (commandType == CommandType.START_SCREEN_SHARING) {

            if (success) {

                // Chỉ chuyển sang ACTIVE khi phiên còn đang khởi động
                // (không ghi đè STOPPING, ENDED hoặc FAILED).
                if (session.getStatus() == ScreenSharingStatus.REQUESTED
                        || session.getStatus()
                                == ScreenSharingStatus.STARTING) {

                    session.setStatus(ScreenSharingStatus.ACTIVE);
                    session.setStartedAt(now);
                    session.setLastHeartbeatAt(now);
                    session.setErrorMessage(null);
                }

            } else {

                // Chỉ đánh dấu FAILED khi phiên còn đang khởi động
                // (không ghi đè STOPPING, ACTIVE, ENDED hoặc FAILED).
                if (session.getStatus() == ScreenSharingStatus.REQUESTED
                        || session.getStatus()
                                == ScreenSharingStatus.STARTING) {

                    session.setStatus(ScreenSharingStatus.FAILED);
                    session.setEndedAt(now);
                    session.setErrorMessage(errorMessage);
                }
            }

        } else if (commandType == CommandType.STOP_SCREEN_SHARING) {

            if (success) {

                // Phiên đã kết thúc trước đó (Agent ngắt kết nối, hết hạn
                // bởi scheduler...): giữ nguyên lý do kết thúc ban đầu.
                if (session.getStatus() != ScreenSharingStatus.ENDED
                        && session.getStatus()
                                != ScreenSharingStatus.FAILED) {

                    session.setStatus(ScreenSharingStatus.ENDED);
                    session.setEndedAt(now);
                    session.setEndReason("ADMIN_STOPPED");
                    session.setErrorMessage(null);
                }

            } else {

                // Dừng thất bại: đưa phiên đang STOPPING về ACTIVE để Admin
                // có thể thử lại. Phiên đã ENDED/FAILED không được hồi sinh,
                // nếu không sẽ chặn Admin tạo phiên mới.
                if (session.getStatus()
                        == ScreenSharingStatus.STOPPING) {

                    session.setStatus(ScreenSharingStatus.ACTIVE);

                    session.setErrorMessage(
                            errorMessage == null || errorMessage.isBlank()
                                    ? "Agent không thể dừng chia sẻ màn hình."
                                    : errorMessage
                    );
                }
            }
        }

        screenSharingSessionRepository.save(session);
    }

    /**
     * Xác thực Agent bằng ID và credential,
     * đồng thời kiểm tra Agent đã liên kết với Client.
     */
    private ClientAgent validateAgent(
            String agentId,
            String rawCredential) {

        if (agentId == null || agentId.isBlank()
                || rawCredential == null || rawCredential.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID và Credential không được để trống.");
        }

        ClientAgent agent = clientAgentRepository.findById(agentId)
                .orElse(null);

        // Agent không tồn tại (ví dụ đã bị Admin xóa) được xử lý giống
        // Credential sai: Controller trả HTTP 401. Điều này BẮT BUỘC để
        // Agent nhận ra mình đã bị loại bỏ và tự đăng ký lại - Agent chỉ
        // coi là bị từ chối khi nhận 401 liên tiếp, một mã 400/404 xen
        // giữa sẽ đặt lại bộ đếm và Agent không bao giờ phục hồi.
        if (agent == null
                || !agentCredentialService.validateCredential(
                        agentId,
                        rawCredential)) {
            throw new SecurityException(
                    "Credential không hợp lệ hoặc đã hết hiệu lực.");
        }

        if (agent.getClient() == null
                || agent.getClient().getClientId() == null) {
            throw new IllegalStateException(
                    "Agent chưa được liên kết với Client.");
        }

        return agent;
    }

    /**
     * Kiểm tra lệnh có thuộc Client liên kết với Agent hay không.
     */
    private void validateCommandBelongsToAgent(
            RemoteCommand command,
            ClientAgent agent) {

        String commandClientId = command.getClient() != null
                ? command.getClient().getClientId()
                : null;

        String agentClientId = agent.getClient() != null
                ? agent.getClient().getClientId()
                : null;

        if (commandClientId == null
                || !commandClientId.equals(agentClientId)) {
            throw new SecurityException(
                    "Lệnh không thuộc Client được liên kết với Agent.");
        }
    }

    /**
     * Kiểm tra Client ID.
     */
    private void validateClientId(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống.");
        }
    }
}