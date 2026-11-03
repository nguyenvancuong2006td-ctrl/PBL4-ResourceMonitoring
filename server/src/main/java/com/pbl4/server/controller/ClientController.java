package com.pbl4.server.controller;

import com.pbl4.server.dto.response.ClientHardwareResponse;
import com.pbl4.server.dto.response.PageResponse;
import com.pbl4.server.enums.ClientStatus;
import com.pbl4.server.model.Client;
import com.pbl4.server.service.ClientHardwareService;
import com.pbl4.server.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientHardwareService clientHardwareService;

    public ClientController(
            ClientService clientService,
            ClientHardwareService clientHardwareService) {
        this.clientService = clientService;
        this.clientHardwareService = clientHardwareService;
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
    // DANH SÁCH CLIENT CÓ PHÂN TRANG
    // page bắt đầu từ 0, size tối đa 100;
    // status = lọc trạng thái, q = tìm hostname / IP / clientId
    // =========================
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<PageResponse<Client>> getClientsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ClientStatus status,
            @RequestParam(required = false) String q) {

        return ResponseEntity.ok(
                clientService.getClientsPage(page, size, status, q)
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
    // LẤY THÔNG TIN PHẦN CỨNG (CPU, RAM)
    // =========================
    @GetMapping("/{clientId}/hardware")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    public ResponseEntity<ClientHardwareResponse> getClientHardware(
            @PathVariable String clientId) {

        return ResponseEntity.ok(
                clientHardwareService.getHardware(clientId)
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