package com.pbl4.server.service;

import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkInterfaceInfo;
import com.pbl4.server.model.NetworkMetric;
import com.pbl4.server.repository.NetworkMetricRepository;
import org.springframework.stereotype.Service;

@Service
public class NetworkMetricService {

    private final NetworkMetricRepository networkMetricRepository;

    public NetworkMetricService(
            NetworkMetricRepository networkMetricRepository) {

        this.networkMetricRepository =
                networkMetricRepository;
    }

    public NetworkMetric saveNetworkMetric(
            MonitoringSnapshot snapshot,
            NetworkInterfaceInfo networkInterface,
            long receivedBytes,
            long transmittedBytes,
            double downloadSpeed,
            double uploadSpeed) {

        NetworkMetric metric =
                new NetworkMetric();

        metric.setSnapshot(snapshot);

        metric.setNetworkInterface(
                networkInterface
        );

        metric.setReceivedBytes(
                receivedBytes
        );

        metric.setTransmittedBytes(
                transmittedBytes
        );

        metric.setDownloadSpeed(
                downloadSpeed
        );

        metric.setUploadSpeed(
                uploadSpeed
        );

        return networkMetricRepository.save(
                metric
        );
    }
}