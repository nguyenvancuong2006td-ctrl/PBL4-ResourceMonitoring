
package com.pbl4.server.service;

import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.repository.ClientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientAgentRepository clientAgentRepository;

    public ClientService(
            ClientRepository clientRepository,
            ClientAgentRepository clientAgentRepository) {

        this.clientRepository = clientRepository;
        this.clientAgentRepository = clientAgentRepository;
    }

    // ==========================================
    // THÊM CLIENT
    // ==========================================
    @Transactional
    public Client createClient(Client client) {

        if (client == null) {
            throw new IllegalArgumentException(
                    "Thông tin Client không được null."
            );
        }

        String clientId = client.getClientId();

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        final String normalizedClientId = clientId.trim();

        if (clientRepository.existsById(normalizedClientId)) {
            throw new IllegalArgumentException(
                    "Client ID đã tồn tại."
            );
        }

        // Kiểm tra IP trùng
        String ipAddress = client.getIpAddress();

        if (ipAddress != null && !ipAddress.isBlank()) {

            ipAddress = ipAddress.trim();

            if (clientRepository.findByIpAddress(ipAddress).isPresent()) {
                throw new IllegalArgumentException(
                        "IP address đã tồn tại."
                );
            }

            client.setIpAddress(ipAddress);
        }

        LocalDateTime now = LocalDateTime.now();

        client.setClientId(normalizedClientId);

        // Client mới chưa có Agent hoạt động nên mặc định OFFLINE
        client.setStatus(ClientStatus.OFFLINE);
        client.setLastSeenAt(null);

        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        return clientRepository.save(client);
    }

    // ==========================================
    // LẤY TẤT CẢ CLIENT
    // ==========================================
    @Transactional(readOnly = true)
    public List<Client> getAllClients() {

        return clientRepository.findAll();
    }

    // ==========================================
    // LẤY CLIENT THEO ID
    // ==========================================
    @Transactional(readOnly = true)
    public Client getClientById(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        final String normalizedClientId = clientId.trim();

        return clientRepository.findById(normalizedClientId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Client: "
                                + normalizedClientId
                        )
                );
    }

    // ==========================================
    // LẤY CLIENT THEO IP
    // ==========================================
    @Transactional(readOnly = true)
    public Client getClientByIp(String ipAddress) {

        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "IP address không được để trống."
            );
        }

        final String normalizedIpAddress = ipAddress.trim();

        return clientRepository.findByIpAddress(normalizedIpAddress)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Client với IP: "
                                + normalizedIpAddress
                        )
                );
    }

    // ==========================================
    // CẬP NHẬT CLIENT
    // ==========================================
    @Transactional
    public Client updateClient(
            String clientId,
            Client clientData) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        if (clientData == null) {
            throw new IllegalArgumentException(
                    "Thông tin cập nhật không được null."
            );
        }

        final String normalizedClientId = clientId.trim();

        Client client = clientRepository.findById(normalizedClientId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Client: "
                                + normalizedClientId
                        )
                );

        // Kiểm tra IP mới có trùng Client khác không
        String newIpAddress = clientData.getIpAddress();

        if (newIpAddress != null && !newIpAddress.isBlank()) {

            newIpAddress = newIpAddress.trim();

            final String checkedIpAddress = newIpAddress;

            clientRepository.findByIpAddress(checkedIpAddress)
                    .ifPresent(existingClient -> {

                        if (!existingClient.getClientId()
                                .equals(normalizedClientId)) {

                            throw new IllegalArgumentException(
                                    "IP address đã tồn tại."
                            );
                        }
                    });
        }

        // Cập nhật thông tin Client
        client.setHostname(clientData.getHostname());
        client.setIpAddress(newIpAddress);
        client.setOsName(clientData.getOsName());
        client.setOsVersion(clientData.getOsVersion());
        client.setKernelVersion(clientData.getKernelVersion());
        client.setBootTime(clientData.getBootTime());
        client.setManagementNote(clientData.getManagementNote());

        /*
         * Không cập nhật status và lastSeenAt tại đây.
         * Hai trường này được quản lý bởi heartbeat
         * và OfflineDetectionScheduler.
         */

        client.setUpdatedAt(LocalDateTime.now());

        return clientRepository.save(client);
    }

    // ==========================================
    // XÓA CLIENT
    // ==========================================
    @Transactional
    public void deleteClient(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException(
                    "Client ID không được để trống."
            );
        }

        final String normalizedClientId = clientId.trim();

        Client client = clientRepository.findById(normalizedClientId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy Client: "
                                + normalizedClientId
                        )
                );

        // Không xóa Client khi vẫn còn Agent liên kết
        if (clientAgentRepository
                .findByClientClientId(normalizedClientId)
                .isPresent()) {

            throw new IllegalStateException(
                    "Không thể xóa Client vì vẫn còn Agent đang liên kết. "
                    + "Hãy xử lý Agent trước."
            );
        }

        clientRepository.delete(client);
    }
}
