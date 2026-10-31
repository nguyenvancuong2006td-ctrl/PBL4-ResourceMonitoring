package com.pbl4.server.service;

import com.pbl4.server.dto.response.ScreenSharingSessionResponse;
import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.enums.CommandType;
import com.pbl4.server.enums.ScreenSharingStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.model.RemoteCommand;
import com.pbl4.server.model.ScreenSharingSession;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.ScreenSharingSessionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ScreenSharingSessionService {

    private final ScreenSharingSessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final ClientAgentRepository clientAgentRepository;
    private final RemoteCommandService remoteCommandService;

    private static final List<ScreenSharingStatus> ACTIVE_STATUSES =
            List.of(
                    ScreenSharingStatus.REQUESTED,
                    ScreenSharingStatus.STARTING,
                    ScreenSharingStatus.ACTIVE,
                    ScreenSharingStatus.STOPPING
            );

    public ScreenSharingSessionService(
            ScreenSharingSessionRepository sessionRepository,
            AccountRepository accountRepository,
            ClientRepository clientRepository,
            ClientAgentRepository clientAgentRepository,
            RemoteCommandService remoteCommandService) {

        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.clientAgentRepository = clientAgentRepository;
        this.remoteCommandService = remoteCommandService;
    }

    /**
     * Admin yêu cầu bắt đầu chia sẻ màn hình.
     */
    @Transactional
    public ScreenSharingSessionResponse startSession(
            Long accountId,
            String clientId) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID không hợp lệ.");
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống.");
        }

        // 2. Lấy Admin
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy tài khoản."));

        // 3. Lấy Client
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Client: " + clientId));

        // 4. Kiểm tra Admin đã có phiên chia sẻ hay chưa
        ScreenSharingSession currentSession =
                sessionRepository
                        .findFirstByAccountAccountIdAndStatusInOrderByRequestedAtDesc(
                                accountId,
                                ACTIVE_STATUSES
                        )
                        .orElse(null);

        if (currentSession != null) {

            // Admin đang chia sẻ đúng Client này
            if (currentSession.getClient().getClientId()
                    .equals(clientId)) {

                if (currentSession.getStatus()
                        == ScreenSharingStatus.STOPPING) {

                    throw new IllegalStateException(
                            "Phiên chia sẻ hiện tại đang dừng. "
                            + "Vui lòng chờ Agent xác nhận.");
                }

                return toResponse(
                        currentSession,
                        null,
                        "Admin đã có phiên chia sẻ trên Client này."
                );
            }

            // Admin đang có phiên ở Client khác
            if (currentSession.getStatus()
                    != ScreenSharingStatus.STOPPING) {

                RemoteCommand stopCommand =
                        remoteCommandService.createCommand(
                                accountId,
                                currentSession.getClient().getClientId(),
                                CommandType.STOP_SCREEN_SHARING,
                                createSessionParameters(
                                        currentSession.getSessionCode()
                                )
                        );

                currentSession.setStatus(
                        ScreenSharingStatus.STOPPING
                );

                sessionRepository.save(currentSession);

                return toResponse(
                        currentSession,
                        stopCommand.getCommandId(),
                        "Đã gửi lệnh dừng phiên cũ. "
                        + "Hãy chờ Agent xác nhận rồi yêu cầu "
                        + "chia sẻ Client mới."
                );
            }

            throw new IllegalStateException(
                    "Phiên chia sẻ cũ vẫn đang dừng. "
                    + "Chưa thể mở phiên mới.");
        }

        // 5. Kiểm tra Client có đang được Admin khác chia sẻ không
        ScreenSharingSession clientSession =
                sessionRepository
                        .findFirstByClientClientIdAndStatusInOrderByRequestedAtDesc(
                                clientId,
                                ACTIVE_STATUSES
                        )
                        .orElse(null);

        if (clientSession != null) {
            throw new IllegalStateException(
                    "Client này đang được một Admin khác chia sẻ.");
        }

        // 6. Tìm Agent được liên kết với Client
        ClientAgent agent = clientAgentRepository
                .findByClientClientId(clientId)
                .orElseThrow(() -> new IllegalStateException(
                        "Client chưa được cài đặt hoặc liên kết Agent."));

        if (agent.getStatus() != AgentStatus.ONLINE) {
            throw new IllegalStateException(
                    "Agent của Client hiện không trực tuyến.");
        }

        // 7. Tạo phiên chia sẻ
        ScreenSharingSession session =
                new ScreenSharingSession();

        session.setSessionCode(
                UUID.randomUUID().toString()
        );

        session.setAccount(account);
        session.setClient(client);
        session.setAgent(agent);
        session.setStatus(ScreenSharingStatus.REQUESTED);
        session.setRequestedAt(LocalDateTime.now());

        session = sessionRepository.save(session);

        // 8. Gửi lệnh bắt đầu đến Agent
        RemoteCommand startCommand =
                remoteCommandService.createCommand(
                        accountId,
                        clientId,
                        CommandType.START_SCREEN_SHARING,
                        createSessionParameters(
                                session.getSessionCode()
                        )
                );

        return toResponse(
                session,
                startCommand.getCommandId(),
                "Đã gửi yêu cầu chia sẻ màn hình đến Agent."
        );
    }

    /**
     * Admin yêu cầu dừng phiên chia sẻ.
     */
    @Transactional
    public ScreenSharingSessionResponse stopSession(
            Long accountId,
            String sessionCode) {

        if (sessionCode == null || sessionCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Session code không được để trống.");
        }

        ScreenSharingSession session =
                sessionRepository.findBySessionCode(sessionCode)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy phiên chia sẻ."));

        // Chỉ Admin tạo phiên mới được quyền dừng phiên đó
        if (!session.getAccount().getAccountId().equals(accountId)) {
            throw new SecurityException(
                    "Bạn không có quyền dừng phiên chia sẻ này.");
        }

        // Phiên đã kết thúc
        if (session.getStatus() == ScreenSharingStatus.ENDED
                || session.getStatus() == ScreenSharingStatus.FAILED) {

            return toResponse(
                    session,
                    null,
                    "Phiên chia sẻ đã kết thúc."
            );
        }

        // Đã gửi lệnh dừng trước đó
        if (session.getStatus() == ScreenSharingStatus.STOPPING) {
            return toResponse(
                    session,
                    null,
                    "Phiên chia sẻ đang được dừng."
            );
        }

        // Gửi lệnh dừng đến Agent
        RemoteCommand stopCommand =
                remoteCommandService.createCommand(
                        accountId,
                        session.getClient().getClientId(),
                        CommandType.STOP_SCREEN_SHARING,
                        createSessionParameters(sessionCode)
                );

        session.setStatus(ScreenSharingStatus.STOPPING);
        sessionRepository.save(session);

        return toResponse(
                session,
                stopCommand.getCommandId(),
                "Đã gửi lệnh dừng chia sẻ màn hình."
        );
    }

    /**
     * Lấy phiên chia sẻ hiện tại của Admin.
     */
    @Transactional(readOnly = true)
    public ScreenSharingSessionResponse getCurrentSession(
            Long accountId) {

        return sessionRepository
                .findFirstByAccountAccountIdAndStatusInOrderByRequestedAtDesc(
                        accountId,
                        ACTIVE_STATUSES
                )
                .map(session -> toResponse(
                        session,
                        null,
                        "Phiên chia sẻ hiện tại."
                ))
                .orElse(null);
    }

    /**
     * Tạo JSON parameters chứa sessionCode.
     */
    private String createSessionParameters(String sessionCode) {
        return "{\"sessionCode\":\"" + sessionCode + "\"}";
    }

    /**
     * Chuyển Entity thành DTO an toàn.
     */
    private ScreenSharingSessionResponse toResponse(
            ScreenSharingSession session,
            Long commandId,
            String message) {

        return new ScreenSharingSessionResponse(
                session.getSessionId(),
                session.getSessionCode(),
                session.getClient().getClientId(),
                session.getAgent().getAgentId(),
                session.getStatus(),
                commandId,
                message
        );
    }
}