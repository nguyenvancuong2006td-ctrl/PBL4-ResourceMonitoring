package com.pbl4.server.controller;

import com.pbl4.server.model.Client;
import com.pbl4.server.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // =========================
    // THÊM CLIENT
    // =========================
    @PostMapping
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public ResponseEntity<Client> createClient(
            @RequestBody Client client) {

        Client createdClient =
                clientService.createClient(client);

        return ResponseEntity.ok(createdClient);
    }

    // =========================
    // LẤY TẤT CẢ CLIENT
    // =========================
    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<List<Client>> getAllClients() {

        return ResponseEntity.ok(
                clientService.getAllClients()
        );
    }

    // =========================
    // LẤY CLIENT THEO ID
    // =========================
    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<Client> getClientById(
            @PathVariable String clientId) {

        return ResponseEntity.ok(
                clientService.getClientById(clientId)
        );
    }

    // =========================
    // LẤY CLIENT THEO IP
    // =========================
    @GetMapping("/ip/{ipAddress}")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<Client> getClientByIp(
            @PathVariable String ipAddress) {

        return ResponseEntity.ok(
                clientService.getClientByIp(ipAddress)
        );
    }

    // =========================
    // CẬP NHẬT CLIENT
    // =========================
    @PutMapping("/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public ResponseEntity<Client> updateClient(
            @PathVariable String clientId,
            @RequestBody Client client) {

        return ResponseEntity.ok(
                clientService.updateClient(
                        clientId,
                        client
                )
        );
    }

    // =========================
    // XÓA CLIENT
    // =========================
    @DeleteMapping("/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_MANAGE')")
    public ResponseEntity<String> deleteClient(
            @PathVariable String clientId) {

        clientService.deleteClient(clientId);

        return ResponseEntity.ok(
                "Xóa Client thành công"
        );
    }
}