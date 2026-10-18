package com.pbl4.server.service;

import com.pbl4.server.model.Client;
import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.repository.ClientRepository;
import com.pbl4.server.repository.MonitoringSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MonitoringSnapshotService {

    private final MonitoringSnapshotRepository monitoringSnapshotRepository;
    private final ClientRepository clientRepository;

    public MonitoringSnapshotService(
            MonitoringSnapshotRepository monitoringSnapshotRepository,
            ClientRepository clientRepository) {

        this.monitoringSnapshotRepository =
                monitoringSnapshotRepository;

        this.clientRepository =
                clientRepository;
    }

    public MonitoringSnapshot saveSnapshot(
            String clientId,
            LocalDateTime collectedAt) {

        Client client =
                clientRepository.findById(clientId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy Client: "
                                                + clientId
                                )
                        );

        MonitoringSnapshot snapshot =
                new MonitoringSnapshot();

        snapshot.setClient(client);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setReceivedAt(LocalDateTime.now());

        return monitoringSnapshotRepository.save(snapshot);
    }

    public List<MonitoringSnapshot> getHistory(
            String clientId) {

        return monitoringSnapshotRepository
                .findByClientClientIdOrderByCollectedAtDesc(
                        clientId
                );
    }

    public MonitoringSnapshot getLatest(
            String clientId) {

        return monitoringSnapshotRepository
                .findTopByClientClientIdOrderByCollectedAtDesc(
                        clientId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Client chưa có dữ liệu monitoring"
                        )
                );
    }
}