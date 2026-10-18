package com.pbl4.server.service;

import com.pbl4.server.enums.CredentialStatus;
import com.pbl4.server.model.AgentCredential;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.AgentCredentialRepository;
import com.pbl4.server.repository.ClientAgentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentCredentialService {

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

    // =========================
    // TẠO CREDENTIAL
    // =========================
    public String createCredential(String agentId) {

        ClientAgent agent =
                clientAgentRepository.findById(agentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy Client Agent: "
                                                + agentId
                                )
                        );

        if (agentCredentialRepository
                .findByAgentAgentId(agentId)
                .isPresent()) {

            throw new RuntimeException(
                    "Agent này đã có Credential"
            );
        }

        // Tạo credential ngẫu nhiên
        String rawCredential =
                UUID.randomUUID().toString();

        // Hash trước khi lưu database
        String credentialHash =
                passwordEncoder.encode(rawCredential);

        LocalDateTime now =
                LocalDateTime.now();

        AgentCredential credential =
                new AgentCredential();

        credential.setAgent(agent);
        credential.setCredentialHash(credentialHash);
        credential.setStatus(CredentialStatus.ACTIVE);
        credential.setIssuedAt(now);

        // Credential có hiệu lực 30 ngày
        credential.setExpiresAt(
                now.plusDays(30)
        );

        credential.setLastUsedAt(null);

        agentCredentialRepository.save(
                credential
        );

        // Chỉ trả credential gốc một lần
        return rawCredential;
    }

    // =========================
    // XÁC THỰC CREDENTIAL
    // =========================
    public boolean validateCredential(
            String agentId,
            String rawCredential) {

        AgentCredential credential =
                agentCredentialRepository
                        .findByAgentAgentId(agentId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Agent chưa có Credential"
                                )
                        );

        // Kiểm tra trạng thái
        if (credential.getStatus()
                != CredentialStatus.ACTIVE) {

            return false;
        }

        // Kiểm tra hết hạn
        if (credential.getExpiresAt() != null
                && LocalDateTime.now()
                .isAfter(credential.getExpiresAt())) {

            credential.setStatus(
                    CredentialStatus.EXPIRED
            );

            agentCredentialRepository.save(
                    credential
            );

            return false;
        }

        // Kiểm tra credential
        boolean valid =
                passwordEncoder.matches(
                        rawCredential,
                        credential.getCredentialHash()
                );

        if (!valid) {
            return false;
        }

        // Ghi nhận lần sử dụng cuối
        credential.setLastUsedAt(
                LocalDateTime.now()
        );

        agentCredentialRepository.save(
                credential
        );

        return true;
    }

    // =========================
    // LẤY TẤT CẢ CREDENTIAL
    // =========================
    public List<AgentCredential> getAllCredentials() {

        return agentCredentialRepository.findAll();
    }

    // =========================
    // LẤY CREDENTIAL THEO ID
    // =========================
    public AgentCredential getCredentialById(
            Long credentialId) {

        return agentCredentialRepository
                .findById(credentialId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Credential: "
                                        + credentialId
                        )
                );
    }

    // =========================
    // LẤY CREDENTIAL THEO AGENT
    // =========================
    public AgentCredential getCredentialByAgentId(
            String agentId) {

        return agentCredentialRepository
                .findByAgentAgentId(agentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent chưa có Credential: "
                                        + agentId
                        )
                );
    }

    // =========================
    // THU HỒI CREDENTIAL
    // =========================
    public void revokeCredential(
            Long credentialId) {

        AgentCredential credential =
                agentCredentialRepository
                        .findById(credentialId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy Credential: "
                                                + credentialId
                                )
                        );

        credential.setStatus(
                CredentialStatus.REVOKED
        );

        agentCredentialRepository.save(
                credential
        );
    }

    // =========================
    // VÔ HIỆU HÓA CREDENTIAL
    // =========================
    public void disableCredential(
            Long credentialId) {

        AgentCredential credential =
                agentCredentialRepository
                        .findById(credentialId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy Credential: "
                                                + credentialId
                                )
                        );

        credential.setStatus(
                CredentialStatus.DISABLED
        );

        agentCredentialRepository.save(
                credential
        );
    }

    // =========================
    // KÍCH HOẠT LẠI CREDENTIAL
    // =========================
    public void activateCredential(
            Long credentialId) {

        AgentCredential credential =
                agentCredentialRepository
                        .findById(credentialId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy Credential: "
                                                + credentialId
                                )
                        );

        if (credential.getExpiresAt() != null
                && LocalDateTime.now()
                .isAfter(credential.getExpiresAt())) {

            credential.setStatus(
                    CredentialStatus.EXPIRED
            );

            agentCredentialRepository.save(
                    credential
            );

            throw new RuntimeException(
                    "Credential đã hết hạn"
            );
        }

        credential.setStatus(
                CredentialStatus.ACTIVE
        );

        agentCredentialRepository.save(
                credential
        );
    }
}