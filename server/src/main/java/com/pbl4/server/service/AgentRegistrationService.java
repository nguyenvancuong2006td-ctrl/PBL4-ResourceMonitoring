package com.pbl4.server.service;

import com.pbl4.server.dto.request.AgentCredentialAckRequest;
import com.pbl4.server.dto.request.AgentCredentialPollRequest;
import com.pbl4.server.dto.response.AgentCredentialAckResponse;
import com.pbl4.server.dto.response.AgentCredentialPollResponse;
import com.pbl4.server.dto.response.AgentRegistrationResponse;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.enums.CredentialStatus;
import com.pbl4.server.enums.RegistrationStatus;

import com.pbl4.server.model.AgentCredential;
import com.pbl4.server.model.AgentRegistrationRequest;
import com.pbl4.server.model.Client;
import com.pbl4.server.model.ClientAgent;

import com.pbl4.server.repository.AgentRegistrationRequestRepository;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;

import com.pbl4.server.util.RegistrationTokenUtil;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AgentRegistrationService {

    private static final int REGISTRATION_VALIDITY_DAYS = 7;

    private final AgentRegistrationRequestRepository registrationRequestRepository;
    private final ClientAgentRepository clientAgentRepository;
    private final ClientRepository clientRepository;
    private final AgentCredentialService agentCredentialService;
    private final CredentialEncryptionService credentialEncryptionService;

    public AgentRegistrationService(
            AgentRegistrationRequestRepository registrationRequestRepository,
            ClientAgentRepository clientAgentRepository,
            ClientRepository clientRepository,
            AgentCredentialService agentCredentialService,
            CredentialEncryptionService credentialEncryptionService) {

        this.registrationRequestRepository = registrationRequestRepository;
        this.clientAgentRepository = clientAgentRepository;
        this.clientRepository = clientRepository;
        this.agentCredentialService = agentCredentialService;
        this.credentialEncryptionService = credentialEncryptionService;
    }

    // ==================================================
    // 1. ĐĂNG KÝ AGENT
    // ==================================================

    public AgentRegistrationResponse registerAgent(
            com.pbl4.server.dto.request.AgentRegistrationRequest request) {

        validateRegistrationRequest(request);

        String tokenHash = RegistrationTokenUtil.hash(
                request.getRegistrationToken()
        );

        if (registrationRequestRepository
                .findByTokenHash(tokenHash).isPresent()) {

            throw new IllegalArgumentException(
                    "Registration Token đã được sử dụng."
            );
        }

        String clientId = "CLI-" + UUID.randomUUID();
        String agentId = "AGT-" + UUID.randomUUID();

        LocalDateTime now = LocalDateTime.now();

        // 1. Tạo Client
        Client client = new Client();

        client.setClientId(clientId);
        client.setHostname(request.getHostname().trim());
        client.setIpAddress(request.getIpAddress().trim());
        client.setOsName(request.getOsName().trim());
        client.setOsVersion(request.getOsVersion().trim());
        client.setKernelVersion(request.getKernelVersion().trim());
        client.setBootTime(request.getBootTime());
        client.setStatus(ClientStatus.PENDING_APPROVAL);

        clientRepository.save(client);

        // 2. Tạo Client Agent
        ClientAgent agent = new ClientAgent();

        agent.setAgentId(agentId);
        agent.setClient(client);
        agent.setVersion(request.getAgentVersion().trim());
        agent.setStatus(AgentStatus.PENDING_APPROVAL);
        agent.setRegisteredAt(now);

        clientAgentRepository.save(agent);

        // 3. Tạo Registration Request
        AgentRegistrationRequest registration =
                new AgentRegistrationRequest();

        registration.setAgent(agent);
        registration.setTokenHash(tokenHash);
        registration.setStatus(RegistrationStatus.PENDING);
        registration.setExpiresAt(
                now.plusDays(REGISTRATION_VALIDITY_DAYS)
        );

        registrationRequestRepository.save(registration);

        return new AgentRegistrationResponse(
                agentId,
                clientId,
                RegistrationStatus.PENDING.name(),
                "Đăng ký Agent thành công. Vui lòng chờ quản trị viên phê duyệt."
        );
    }

    // ==================================================
    // 2. PHÊ DUYỆT AGENT
    // ==================================================

    /**
     * Phê duyệt Agent và tạo Credential.
     *
     * Credential gốc không lưu trực tiếp trong database.
     * Server chỉ lưu bản mã hóa tạm thời để Agent Poll lại
     * cho đến khi Agent gửi ACK thành công.
     */
    public void approveAgent(String agentId) {

        String normalizedAgentId = validateAgentId(agentId);

        LocalDateTime now = LocalDateTime.now();

        // 1. Khóa Registration Request
        AgentRegistrationRequest registration =
                registrationRequestRepository
                        .findByAgentAgentIdForUpdate(normalizedAgentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy yêu cầu đăng ký của Agent: "
                                        + normalizedAgentId
                        ));

        // 2. Kiểm tra trạng thái đăng ký
        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new IllegalStateException(
                    "Yêu cầu đăng ký không còn ở trạng thái PENDING."
            );
        }

        // 3. Kiểm tra thời hạn đăng ký
        if (registration.getExpiresAt() == null
                || !now.isBefore(registration.getExpiresAt())) {

            throw new IllegalStateException(
                    "Yêu cầu đăng ký Agent đã hết hạn."
            );
        }

        // 4. Khóa Agent
        ClientAgent agent = clientAgentRepository
                .findByAgentIdForUpdate(normalizedAgentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Agent: " + normalizedAgentId
                ));

        if (agent.getStatus() != AgentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Agent không ở trạng thái chờ phê duyệt."
            );
        }

        // 5. Kiểm tra Client
        Client client = agent.getClient();

        if (client == null) {
            throw new IllegalStateException(
                    "Agent không liên kết với Client."
            );
        }

        if (client.getStatus() != ClientStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Client không ở trạng thái chờ phê duyệt."
            );
        }

        // 6. Chuyển Agent và Client sang OFFLINE
        // OFFLINE cho phép AgentCredentialService cấp Credential.
        agent.setStatus(AgentStatus.OFFLINE);
        client.setStatus(ClientStatus.OFFLINE);
        client.setUpdatedAt(now);

        clientAgentRepository.save(agent);
        clientRepository.save(client);

        // 7. Tạo Credential
        String rawCredential =
                agentCredentialService.createCredential(normalizedAgentId);

        // 8. Mã hóa Credential
        String encryptedCredential =
                credentialEncryptionService.encrypt(rawCredential);

        // 9. Lấy thông tin Credential vừa tạo
        AgentCredential credential =
                agentCredentialService.getCredentialByAgentId(
                        normalizedAgentId
                );

        if (credential == null
                || credential.getStatus() != CredentialStatus.ACTIVE
                || credential.getExpiresAt() == null
                || !now.isBefore(credential.getExpiresAt())) {

            throw new IllegalStateException(
                    "Credential vừa tạo không hợp lệ."
            );
        }

        // 10. Lưu bản mã hóa tạm thời
        registration.setCredentialCiphertext(encryptedCredential);
        registration.setCredentialExpiresAt(
                credential.getExpiresAt()
        );

        registration.setCredentialDeliveredAt(null);
        registration.setApprovedAt(now);
        registration.setStatus(RegistrationStatus.APPROVED);

        registrationRequestRepository.save(registration);
    }

    // ==================================================
    // 3. POLL CREDENTIAL
    // ==================================================

    /**
     * Agent kiểm tra Credential đã được Admin cấp hay chưa.
     *
     * - PENDING: Chưa được phê duyệt.
     * - APPROVED: Trả Credential đã giải mã.
     * - DELIVERED: Credential đã được Agent xác nhận nhận.
     *
     * Nếu Agent chưa ACK, các lần Poll tiếp theo sẽ nhận
     * cùng một Credential, không tạo Credential mới.
     */
    public AgentCredentialPollResponse pollCredential(
            String agentId,
            AgentCredentialPollRequest request) {

        String normalizedAgentId = validateAgentId(agentId);

        String tokenHash = validateAndHashRegistrationToken(
                request == null ? null : request.getRegistrationToken()
        );

        // 1. Khóa Registration Request theo token hash
        AgentRegistrationRequest registration =
                registrationRequestRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Registration Token không hợp lệ."
                        ));

        // 2. Xác nhận Registration Token thuộc đúng Agent
        if (registration.getAgent() == null
                || !normalizedAgentId.equals(
                        registration.getAgent().getAgentId())) {

            throw new IllegalArgumentException(
                    "Registration Token không thuộc Agent này."
            );
        }

        // 3. Kiểm tra trạng thái đăng ký
        RegistrationStatus status = registration.getStatus();

        if (status == RegistrationStatus.PENDING) {

            LocalDateTime now = LocalDateTime.now();

            if (registration.getExpiresAt() == null
                    || !now.isBefore(registration.getExpiresAt())) {

                throw new IllegalStateException(
                        "Yêu cầu đăng ký Agent đã hết hạn."
                );
            }

            return new AgentCredentialPollResponse(
                    normalizedAgentId,
                    RegistrationStatus.PENDING.name(),
                    null,
                    null,
                    "Agent chưa được phê duyệt. Vui lòng thử lại sau."
            );
        }

        if (status == RegistrationStatus.REJECTED) {
            return new AgentCredentialPollResponse(
                    normalizedAgentId,
                    RegistrationStatus.REJECTED.name(),
                    null,
                    null,
                    "Yêu cầu đăng ký Agent đã bị từ chối."
            );
        }

        if (status != RegistrationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Trạng thái đăng ký Agent không hợp lệ."
            );
        }

        // 4. Kiểm tra Agent
        ClientAgent agent = registration.getAgent();

        if (agent.getStatus() == AgentStatus.REJECTED) {
            throw new IllegalStateException(
                    "Agent đã bị từ chối."
            );
        }

        // 5. Nếu đã ACK thì không cấp lại Credential
        if (registration.getCredentialDeliveredAt() != null) {
            return new AgentCredentialPollResponse(
                    normalizedAgentId,
                    "DELIVERED",
                    null,
                    registration.getCredentialExpiresAt(),
                    "Credential đã được Agent xác nhận nhận thành công."
            );
        }

        // 6. Kiểm tra bản mã hóa
        String encryptedCredential =
                registration.getCredentialCiphertext();

        if (encryptedCredential == null
                || encryptedCredential.isBlank()) {

            throw new IllegalStateException(
                    "Không tìm thấy Credential đang chờ giao cho Agent."
            );
        }

        // 7. Kiểm tra thời hạn Credential
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime credentialExpiresAt =
                registration.getCredentialExpiresAt();

        if (credentialExpiresAt == null
                || !now.isBefore(credentialExpiresAt)) {

            throw new IllegalStateException(
                    "Credential đã hết hạn. Cần cấp lại Credential."
            );
        }

        // 8. Kiểm tra trạng thái Credential trong database
        AgentCredential credential =
                agentCredentialService.getCredentialByAgentId(
                        normalizedAgentId
                );

        if (credential == null
                || credential.getStatus() != CredentialStatus.ACTIVE
                || credential.getExpiresAt() == null
                || !now.isBefore(credential.getExpiresAt())) {

            throw new IllegalStateException(
                    "Credential không còn hoạt động hoặc đã hết hạn."
            );
        }

        // 9. Giải mã Credential
        String rawCredential =
                credentialEncryptionService.decrypt(encryptedCredential);

        // 10. Trả Credential cho Agent
        // Chưa xóa ciphertext và chưa đánh dấu đã giao.
        return new AgentCredentialPollResponse(
                normalizedAgentId,
                RegistrationStatus.APPROVED.name(),
                rawCredential,
                credentialExpiresAt,
                "Credential đã sẵn sàng. Hãy lưu an toàn và gửi ACK."
        );
    }

    // ==================================================
    // 4. ACK CREDENTIAL
    // ==================================================

    /**
     * Agent xác nhận đã nhận và lưu Credential thành công.
     *
     * Sau khi ACK:
     * - Ghi nhận thời gian giao Credential.
     * - Xóa bản Credential đã mã hóa tạm thời.
     * - Không cho phép Poll nhận lại Credential.
     *
     * ACK có tính idempotent: gửi lại ACK vẫn thành công
     * nếu trước đó Agent đã xác nhận thành công.
     */
    public AgentCredentialAckResponse acknowledgeCredential(
            String agentId,
            AgentCredentialAckRequest request) {

        String normalizedAgentId = validateAgentId(agentId);

        String tokenHash = validateAndHashRegistrationToken(
                request == null ? null : request.getRegistrationToken()
        );

        // 1. Khóa Registration Request
        AgentRegistrationRequest registration =
                registrationRequestRepository
                        .findByTokenHashForUpdate(tokenHash)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Registration Token không hợp lệ."
                        ));

        // 2. Xác nhận token thuộc đúng Agent
        if (registration.getAgent() == null
                || !normalizedAgentId.equals(
                        registration.getAgent().getAgentId())) {

            throw new IllegalArgumentException(
                    "Registration Token không thuộc Agent này."
            );
        }

        // 3. Chỉ Agent đã được phê duyệt mới được ACK
        if (registration.getStatus() != RegistrationStatus.APPROVED) {
            throw new IllegalStateException(
                    "Agent chưa được phê duyệt hoặc yêu cầu không hợp lệ."
            );
        }

        // 4. Nếu đã ACK trước đó thì trả về thành công
        // Không cập nhật lại thời gian và không xử lý lần nữa.
        if (registration.getCredentialDeliveredAt() != null) {
            return new AgentCredentialAckResponse(
                    normalizedAgentId,
                    "DELIVERED",
                    registration.getCredentialDeliveredAt(),
                    "Credential đã được xác nhận nhận trước đó."
            );
        }

        // 5. Kiểm tra bản mã hóa đang chờ giao
        String encryptedCredential =
                registration.getCredentialCiphertext();

        if (encryptedCredential == null
                || encryptedCredential.isBlank()) {

            throw new IllegalStateException(
                    "Không có Credential đang chờ xác nhận."
            );
        }

        // 6. Kiểm tra thời hạn Credential
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime credentialExpiresAt =
                registration.getCredentialExpiresAt();

        if (credentialExpiresAt == null
                || !now.isBefore(credentialExpiresAt)) {

            throw new IllegalStateException(
                    "Credential đã hết hạn. Không thể xác nhận giao Credential."
            );
        }

        // 7. Kiểm tra Credential vẫn còn hoạt động
        AgentCredential credential =
                agentCredentialService.getCredentialByAgentId(
                        normalizedAgentId
                );

        if (credential == null
                || credential.getStatus() != CredentialStatus.ACTIVE
                || credential.getExpiresAt() == null
                || !now.isBefore(credential.getExpiresAt())) {

            throw new IllegalStateException(
                    "Credential không còn hoạt động hoặc đã hết hạn."
            );
        }

        // 8. Xác nhận đã giao Credential
        registration.setCredentialDeliveredAt(now);

        // 9. Xóa bản mã hóa tạm thời
        registration.setCredentialCiphertext(null);

        registrationRequestRepository.save(registration);

        return new AgentCredentialAckResponse(
                normalizedAgentId,
                "DELIVERED",
                now,
                "Agent đã xác nhận nhận Credential thành công."
        );
    }

    // ==================================================
    // 5. TỪ CHỐI AGENT
    // ==================================================

    /**
     * Từ chối yêu cầu đăng ký Agent.
     * Không tạo Credential cho Agent bị từ chối.
     */
    public void rejectAgent(String agentId) {

        String normalizedAgentId = validateAgentId(agentId);

        // 1. Khóa Registration Request
        AgentRegistrationRequest registration =
                registrationRequestRepository
                        .findByAgentAgentIdForUpdate(normalizedAgentId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy yêu cầu đăng ký của Agent: "
                                        + normalizedAgentId
                        ));

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ có thể từ chối yêu cầu đang ở trạng thái PENDING."
            );
        }

        // 2. Khóa Agent
        ClientAgent agent = clientAgentRepository
                .findByAgentIdForUpdate(normalizedAgentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Agent: " + normalizedAgentId
                ));

        if (agent.getStatus() != AgentStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Agent không ở trạng thái chờ phê duyệt."
            );
        }

        // 3. Cập nhật Agent
        agent.setStatus(AgentStatus.REJECTED);
        clientAgentRepository.save(agent);

        // 4. Cập nhật Client
        Client client = agent.getClient();

        if (client != null) {
            client.setStatus(ClientStatus.OFFLINE);
            clientRepository.save(client);
        }

        // 5. Cập nhật Registration Request
        registration.setStatus(RegistrationStatus.REJECTED);
        registration.setCredentialCiphertext(null);
        registration.setCredentialExpiresAt(null);
        registration.setCredentialDeliveredAt(null);

        registrationRequestRepository.save(registration);
    }

    // ==================================================
    // 6. KIỂM TRA DỮ LIỆU ĐĂNG KÝ
    // ==================================================

    private void validateRegistrationRequest(
            com.pbl4.server.dto.request.AgentRegistrationRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Thông tin đăng ký không được để trống."
            );
        }

        if (isBlank(request.getHostname())
                || isBlank(request.getIpAddress())
                || isBlank(request.getOsName())
                || isBlank(request.getOsVersion())
                || isBlank(request.getKernelVersion())
                || isBlank(request.getAgentVersion())
                || isBlank(request.getRegistrationToken())) {

            throw new IllegalArgumentException(
                    "Thông tin đăng ký Agent còn thiếu."
            );
        }

        if (request.getHostname().trim().length() > 100
                || request.getIpAddress().trim().length() > 50
                || request.getOsName().trim().length() > 50
                || request.getOsVersion().trim().length() > 50
                || request.getKernelVersion().trim().length() > 50
                || request.getAgentVersion().trim().length() > 20) {

            throw new IllegalArgumentException(
                    "Một hoặc nhiều trường thông tin vượt quá độ dài cho phép."
            );
        }

        if (request.getRegistrationToken().length() > 512) {
            throw new IllegalArgumentException(
                    "Registration Token không hợp lệ."
            );
        }
    }

    // ==================================================
    // 7. HÀM HỖ TRỢ
    // ==================================================

    private String validateAgentId(String agentId) {

        if (isBlank(agentId)) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        return agentId.trim();
    }

    private String validateAndHashRegistrationToken(String token) {

        if (isBlank(token) || token.length() > 512) {
            throw new IllegalArgumentException(
                    "Registration Token không hợp lệ."
            );
        }

        return RegistrationTokenUtil.hash(token);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}