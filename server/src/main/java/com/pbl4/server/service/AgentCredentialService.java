
package com.pbl4.server.service;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.enums.CredentialStatus;
import com.pbl4.server.model.AgentCredential;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.AgentCredentialRepository;
import com.pbl4.server.repository.ClientAgentRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class AgentCredentialService {

    // ==========================================
    // CẤU HÌNH
    // ==========================================

    private static final int CREDENTIAL_VALIDITY_DAYS = 30;

    private static final long LAST_USED_UPDATE_INTERVAL_SECONDS = 60;

    // 32 byte = 256 bit
    private static final int CREDENTIAL_BYTE_LENGTH = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AgentCredentialRepository agentCredentialRepository;
    private final ClientAgentRepository clientAgentRepository;
    private final PasswordEncoder passwordEncoder;

    public AgentCredentialService(
            AgentCredentialRepository agentCredentialRepository,
            ClientAgentRepository clientAgentRepository,
            PasswordEncoder passwordEncoder) {

        this.agentCredentialRepository = agentCredentialRepository;
        this.clientAgentRepository = clientAgentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==========================================
    // KIỂM TRA AGENT
    // ==========================================

    /**
     * Chỉ Agent đã được phê duyệt mới được cấp
     * hoặc sử dụng Credential.
     *
     * Trạng thái hợp lệ:
     * - ONLINE
     * - OFFLINE
     */
    private boolean isAgentApproved(ClientAgent agent) {

        return agent != null
                && (agent.getStatus() == AgentStatus.ONLINE
                || agent.getStatus() == AgentStatus.OFFLINE);
    }

    /**
     * Kiểm tra Agent ID đầu vào.
     */
    private String normalizeAgentId(String agentId) {

        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        return agentId.trim();
    }

    /**
     * Lấy Agent đã được phê duyệt.
     *
     * Phương thức này chỉ đọc, không khóa bản ghi.
     */
    private ClientAgent getApprovedAgent(String agentId) {

        String normalizedAgentId = normalizeAgentId(agentId);

        ClientAgent agent = clientAgentRepository
                .findById(normalizedAgentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Client Agent: " + normalizedAgentId
                ));

        if (!isAgentApproved(agent)) {
            throw new IllegalStateException(
                    "Agent chưa được phê duyệt hoặc đã bị vô hiệu hóa."
            );
        }

        return agent;
    }

    /**
     * Lấy và khóa Agent trong transaction.
     *
     * Dùng khi cấp Credential để đảm bảo các yêu cầu
     * cấp Credential cho cùng một Agent được thực hiện tuần tự.
     */
    private ClientAgent getApprovedAgentForUpdate(String agentId) {

        String normalizedAgentId = normalizeAgentId(agentId);

        ClientAgent agent = clientAgentRepository
                .findByAgentIdForUpdate(normalizedAgentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Client Agent: " + normalizedAgentId
                ));

        if (!isAgentApproved(agent)) {
            throw new IllegalStateException(
                    "Agent chưa được phê duyệt hoặc đã bị vô hiệu hóa."
            );
        }

        return agent;
    }

    // ==========================================
    // SINH CREDENTIAL
    // ==========================================

    /**
     * Sinh Credential ngẫu nhiên có độ dài 256 bit.
     *
     * Sử dụng Base64 URL-safe để thuận tiện truyền
     * qua HTTP Header.
     */
    private String generateCredential() {

        byte[] randomBytes = new byte[CREDENTIAL_BYTE_LENGTH];

        SECURE_RANDOM.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    // ==========================================
    // KIỂM TRA CREDENTIAL HẾT HẠN
    // ==========================================

    /**
     * Credential được xem là hết hạn khi:
     * - expiresAt bằng null.
     * - Thời gian hiện tại bằng hoặc lớn hơn expiresAt.
     */
    private boolean isCredentialExpired(
            AgentCredential credential,
            LocalDateTime now) {

        return credential.getExpiresAt() == null
                || !now.isBefore(credential.getExpiresAt());
    }

    // ==========================================
    // TẠO CREDENTIAL
    // ==========================================

    /**
     * Tạo Credential mới cho Agent.
     *
     * Điều kiện:
     * - Agent phải tồn tại.
     * - Agent phải được Admin phê duyệt.
     * - Không được cấp lại nếu Credential ACTIVE và còn hạn.
     *
     * Nếu Credential cũ:
     * - ACTIVE và còn hạn: từ chối cấp mới.
     * - ACTIVE nhưng hết hạn: chuyển EXPIRED và cấp mới.
     * - EXPIRED: cấp mới.
     * - REVOKED: cấp mới.
     * - DISABLED: cấp mới.
     *
     * Credential gốc chỉ trả về cho bên gọi.
     * Database chỉ lưu BCrypt hash.
     *
     * Transaction khóa Agent trước, sau đó khóa Credential.
     */
    @Transactional
    public String createCredential(String agentId) {

        // 1. Khóa Agent trước khi xử lý
        ClientAgent agent = getApprovedAgentForUpdate(agentId);

        String normalizedAgentId = agent.getAgentId();

        LocalDateTime now = LocalDateTime.now();

        // 2. Tìm và khóa Credential hiện tại
        AgentCredential credential = agentCredentialRepository
                .findByAgentAgentIdForUpdate(normalizedAgentId)
                .orElse(null);

        if (credential != null) {

            boolean expired = isCredentialExpired(credential, now);

            // 3. Không cấp mới nếu Credential ACTIVE và còn hạn
            if (credential.getStatus() == CredentialStatus.ACTIVE
                    && !expired) {

                throw new IllegalStateException(
                        "Agent đang có Credential ACTIVE và còn hạn. "
                                + "Không thể cấp thêm Credential."
                );
            }

            // 4. Nếu Credential ACTIVE đã hết hạn,
            // chuyển trạng thái thành EXPIRED trước khi cấp mới
            if (credential.getStatus() == CredentialStatus.ACTIVE
                    && expired) {

                credential.setStatus(CredentialStatus.EXPIRED);
            }

        } else {

            // 5. Nếu chưa có Credential, tạo bản ghi mới
            credential = new AgentCredential();
            credential.setAgent(agent);
        }

        // 6. Sinh Credential gốc mới
        String rawCredential = generateCredential();

        // 7. Tạo BCrypt hash
        String credentialHash = passwordEncoder.encode(rawCredential);

        // 8. Cập nhật thông tin Credential
        credential.setAgent(agent);
        credential.setCredentialHash(credentialHash);
        credential.setStatus(CredentialStatus.ACTIVE);
        credential.setIssuedAt(now);
        credential.setExpiresAt(
                now.plusDays(CREDENTIAL_VALIDITY_DAYS)
        );
        credential.setLastUsedAt(null);

        // 9. Lưu database
        agentCredentialRepository.save(credential);

        // 10. Trả Credential gốc cho bên gọi
        return rawCredential;
    }

    // ==========================================
    // XÁC THỰC CREDENTIAL
    // ==========================================

    /**
     * Xác thực Credential của Agent.
     *
     * Kiểm tra:
     * 1. Agent ID và Credential không được để trống.
     * 2. Agent phải tồn tại.
     * 3. Agent phải được phê duyệt.
     * 4. Credential phải tồn tại.
     * 5. Credential phải ACTIVE.
     * 6. Credential chưa hết hạn.
     * 7. BCrypt hash phải khớp.
     *
     * Nếu hợp lệ:
     * - Cập nhật lastUsedAt tối đa mỗi 60 giây.
     */
    @Transactional
    public boolean validateCredential(
            String agentId,
            String rawCredential) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (agentId == null || agentId.isBlank()
                || rawCredential == null || rawCredential.isBlank()) {

            return false;
        }

        String normalizedAgentId = agentId.trim();

        // 2. Kiểm tra Agent
        ClientAgent agent = clientAgentRepository
                .findById(normalizedAgentId)
                .orElse(null);

        if (!isAgentApproved(agent)) {
            return false;
        }

        // 3. Tìm và khóa Credential
        AgentCredential credential = agentCredentialRepository
                .findByAgentAgentIdForUpdate(normalizedAgentId)
                .orElse(null);

        if (credential == null) {
            return false;
        }

        // 4. Kiểm tra trạng thái Credential
        if (credential.getStatus() != CredentialStatus.ACTIVE) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // 5. Kiểm tra thời hạn
        if (isCredentialExpired(credential, now)) {

            credential.setStatus(CredentialStatus.EXPIRED);

            agentCredentialRepository.save(credential);

            return false;
        }

        // 6. Kiểm tra BCrypt hash
        String credentialHash = credential.getCredentialHash();

        if (credentialHash == null || credentialHash.isBlank()) {
            return false;
        }

        boolean matches = passwordEncoder.matches(
                rawCredential,
                credentialHash
        );

        if (!matches) {
            return false;
        }

        // 7. Cập nhật lastUsedAt tối đa mỗi 60 giây
        LocalDateTime lastUsedAt = credential.getLastUsedAt();

        boolean shouldUpdateLastUsed =
                lastUsedAt == null
                        || !lastUsedAt.isAfter(
                                now.minusSeconds(
                                        LAST_USED_UPDATE_INTERVAL_SECONDS
                                )
                        );

        if (shouldUpdateLastUsed) {

            credential.setLastUsedAt(now);

            agentCredentialRepository.save(credential);
        }

        return true;
    }

    // ==========================================
    // LẤY TẤT CẢ CREDENTIAL
    // ==========================================

    /**
     * Lấy danh sách Credential.
     *
     * Lưu ý:
     * Không trả trực tiếp Entity qua API.
     * Nên sử dụng DTO riêng để tránh lộ credentialHash.
     */
    @Transactional(readOnly = true)
    public List<AgentCredential> getAllCredentials() {

        return agentCredentialRepository.findAll();
    }

    // ==========================================
    // LẤY CREDENTIAL THEO ID
    // ==========================================

    @Transactional(readOnly = true)
    public AgentCredential getCredentialById(Long credentialId) {

        if (credentialId == null) {
            throw new IllegalArgumentException(
                    "Credential ID không được để trống."
            );
        }

        return agentCredentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy Credential: " + credentialId
                ));
    }

    // ==========================================
    // LẤY CREDENTIAL THEO AGENT ID
    // ==========================================

    @Transactional(readOnly = true)
    public AgentCredential getCredentialByAgentId(String agentId) {

        String normalizedAgentId = normalizeAgentId(agentId);

        return agentCredentialRepository
                .findByAgentAgentId(normalizedAgentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Agent chưa có Credential: " + normalizedAgentId
                ));
    }

    // ==========================================
    // THU HỒI CREDENTIAL
    // ==========================================

    /**
     * Thu hồi Credential.
     *
     * Credential REVOKED không thể kích hoạt lại.
     */
    @Transactional
    public void revokeCredential(Long credentialId) {

        AgentCredential credential = getCredentialById(credentialId);

        if (credential.getStatus() == CredentialStatus.REVOKED) {
            throw new IllegalStateException(
                    "Credential đã được thu hồi trước đó."
            );
        }

        credential.setStatus(CredentialStatus.REVOKED);

        agentCredentialRepository.save(credential);
    }

    // ==========================================
    // VÔ HIỆU HÓA CREDENTIAL
    // ==========================================

    /**
     * Vô hiệu hóa Credential tạm thời.
     */
    @Transactional
    public void disableCredential(Long credentialId) {

        AgentCredential credential = getCredentialById(credentialId);

        if (credential.getStatus() == CredentialStatus.REVOKED) {
            throw new IllegalStateException(
                    "Không thể vô hiệu hóa Credential đã bị thu hồi."
            );
        }

        if (credential.getStatus() == CredentialStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Credential đã hết hạn. Hãy cấp Credential mới."
            );
        }

        if (credential.getStatus() == CredentialStatus.DISABLED) {
            throw new IllegalStateException(
                    "Credential đã được vô hiệu hóa trước đó."
            );
        }

        credential.setStatus(CredentialStatus.DISABLED);

        agentCredentialRepository.save(credential);
    }

    // ==========================================
    // KÍCH HOẠT LẠI CREDENTIAL
    // ==========================================

    /**
     * Chỉ kích hoạt lại Credential:
     * - Đang DISABLED.
     * - Chưa hết hạn.
     * - Thuộc Agent đã được phê duyệt.
     *
     * Không kích hoạt lại Credential REVOKED hoặc EXPIRED.
     */
    @Transactional
    public void activateCredential(Long credentialId) {

        AgentCredential credential = getCredentialById(credentialId);

        // 1. Kiểm tra Agent
        ClientAgent agent = credential.getAgent();

        if (!isAgentApproved(agent)) {
            throw new IllegalStateException(
                    "Không thể kích hoạt Credential "
                            + "của Agent chưa được phê duyệt "
                            + "hoặc đã bị vô hiệu hóa."
            );
        }

        // 2. Kiểm tra trạng thái Credential
        if (credential.getStatus() == CredentialStatus.REVOKED) {
            throw new IllegalStateException(
                    "Credential đã bị thu hồi và không thể kích hoạt lại."
            );
        }

        if (credential.getStatus() == CredentialStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Credential đã hết hạn. Hãy cấp Credential mới."
            );
        }

        if (credential.getStatus() == CredentialStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Credential hiện đang ACTIVE."
            );
        }

        if (credential.getStatus() != CredentialStatus.DISABLED) {
            throw new IllegalStateException(
                    "Trạng thái Credential không hợp lệ để kích hoạt."
            );
        }

        // 3. Kiểm tra thời hạn
        LocalDateTime now = LocalDateTime.now();

        if (isCredentialExpired(credential, now)) {

            credential.setStatus(CredentialStatus.EXPIRED);

            agentCredentialRepository.save(credential);

            throw new IllegalStateException(
                    "Credential đã hết hạn. Hãy cấp Credential mới."
            );
        }

        // 4. Kích hoạt Credential
        credential.setStatus(CredentialStatus.ACTIVE);

        agentCredentialRepository.save(credential);
    }
}
