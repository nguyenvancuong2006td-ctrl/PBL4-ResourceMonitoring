package com.pbl4.server.service;

import com.pbl4.server.dto.response.ClientHardwareResponse;
import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.CpuInfoRepository;
import com.pbl4.server.repository.MemoryInfoRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientHardwareService {

    private final ClientRepository clientRepository;
    private final CpuInfoRepository cpuInfoRepository;
    private final MemoryInfoRepository memoryInfoRepository;

    public ClientHardwareService(
            ClientRepository clientRepository,
            CpuInfoRepository cpuInfoRepository,
            MemoryInfoRepository memoryInfoRepository) {

        this.clientRepository = clientRepository;
        this.cpuInfoRepository = cpuInfoRepository;
        this.memoryInfoRepository = memoryInfoRepository;
    }

    @Transactional(readOnly = true)
    public ClientHardwareResponse getHardware(String clientId) {

        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID không được để trống."
            );
        }

        String id = clientId.trim();

        if (!clientRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy Client: " + id
            );
        }

        return new ClientHardwareResponse(
                id,
                cpuInfoRepository.findByClientClientId(id).orElse(null),
                memoryInfoRepository.findByClientClientId(id).orElse(null)
        );
    }
}