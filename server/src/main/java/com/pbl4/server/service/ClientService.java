package com.pbl4.server.service;

import com.pbl4.server.model.Client;
import com.pbl4.server.repository.ClientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    // =========================
    // THÊM CLIENT
    // =========================
    public Client createClient(Client client) {

        if (client.getClientId() == null ||
                client.getClientId().isBlank()) {

            throw new RuntimeException(
                    "Client ID không được để trống"
            );
        }

        if (clientRepository.existsById(
                client.getClientId())) {

            throw new RuntimeException(
                    "Client ID đã tồn tại"
            );
        }

        // Kiểm tra IP trùng
        if (client.getIpAddress() != null &&
                clientRepository
                        .findByIpAddress(
                                client.getIpAddress()
                        )
                        .isPresent()) {

            throw new RuntimeException(
                    "IP address đã tồn tại"
            );
        }

        LocalDateTime now =
                LocalDateTime.now();

        client.setCreatedAt(now);
        client.setUpdatedAt(now);

        return clientRepository.save(client);
    }

    // =========================
    // LẤY TẤT CẢ CLIENT
    // =========================
    public List<Client> getAllClients() {

        return clientRepository.findAll();
    }

    // =========================
    // LẤY CLIENT THEO ID
    // =========================
    public Client getClientById(
            String clientId) {

        return clientRepository
                .findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Client: "
                                        + clientId
                        )
                );
    }

    // =========================
    // LẤY CLIENT THEO IP
    // =========================
    public Client getClientByIp(
            String ipAddress) {

        return clientRepository
                .findByIpAddress(ipAddress)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Client với IP: "
                                        + ipAddress
                        )
                );
    }

    // =========================
    // CẬP NHẬT CLIENT
    // =========================
    public Client updateClient(
            String clientId,
            Client clientData) {

        Client client = clientRepository
                .findById(clientId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Client: "
                                        + clientId
                        )
                );

        // Kiểm tra IP mới có trùng Client khác không
        if (clientData.getIpAddress() != null) {

            clientRepository
                    .findByIpAddress(
                            clientData.getIpAddress()
                    )
                    .ifPresent(existingClient -> {

                        if (!existingClient
                                .getClientId()
                                .equals(clientId)) {

                            throw new RuntimeException(
                                    "IP address đã tồn tại"
                            );
                        }
                    });
        }

        client.setHostname(
                clientData.getHostname()
        );

        client.setIpAddress(
                clientData.getIpAddress()
        );

        client.setOsName(
                clientData.getOsName()
        );

        client.setOsVersion(
                clientData.getOsVersion()
        );

        client.setKernelVersion(
                clientData.getKernelVersion()
        );

        client.setBootTime(
                clientData.getBootTime()
        );

        client.setManagementNote(
                clientData.getManagementNote()
        );

        /*
         * Không cập nhật status và lastSeenAt
         * từ API quản lý.
         *
         * Hai trường này được cập nhật
         * bởi heartbeat.
         */

        client.setUpdatedAt(
                LocalDateTime.now()
        );

        return clientRepository.save(client);
    }

    // =========================
    // XÓA CLIENT
    // =========================
    public void deleteClient(
            String clientId) {

        if (!clientRepository.existsById(clientId)) {

            throw new RuntimeException(
                    "Không tìm thấy Client: "
                            + clientId
            );
        }
        
        clientRepository.deleteById(clientId);
    }
}