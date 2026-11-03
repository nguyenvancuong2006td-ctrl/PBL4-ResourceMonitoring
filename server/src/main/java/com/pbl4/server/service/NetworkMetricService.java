package com.pbl4.server.service;

import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.NetworkInterfaceInfo;
import com.pbl4.server.model.NetworkMetric;
import com.pbl4.server.repository.NetworkMetricRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class NetworkMetricService {

    private final NetworkMetricRepository networkMetricRepository;

    /*
     * Lưu bộ đếm byte của lần đo gần nhất theo giao diện mạng.
     * Dữ liệu nằm trong RAM nên sẽ được tính lại sau khi
     * Server khởi động lại.
     */
    private final ConcurrentMap<Long, PreviousNetworkSample> previousSamples =
            new ConcurrentHashMap<>();

    public NetworkMetricService(
            NetworkMetricRepository networkMetricRepository) {
        this.networkMetricRepository = networkMetricRepository;
    }

    /**
     * Xóa bộ đếm byte lần đo trước của các giao diện mạng khỏi RAM.
     * Gọi khi Client bị xóa để không giữ dữ liệu của giao diện không còn.
     */
    public void evictInterfaces(Collection<Long> interfaceIds) {

        if (interfaceIds == null || interfaceIds.isEmpty()) {
            return;
        }

        previousSamples.keySet().removeAll(interfaceIds);
    }

    public NetworkMetric saveNetworkMetric(
            MonitoringSnapshot snapshot,
            NetworkInterfaceInfo networkInterface,
            long receivedBytes,
            long transmittedBytes,
            LocalDateTime collectedAt) {

        Long interfaceId = networkInterface.getInterfaceId();

        PreviousNetworkSample previous =
                previousSamples.put(
                        interfaceId,
                        new PreviousNetworkSample(
                                receivedBytes,
                                transmittedBytes,
                                collectedAt
                        )
                );

        double downloadSpeed = 0.0;
        double uploadSpeed = 0.0;

        if (previous != null) {
            long elapsedMillis = Duration.between(
                    previous.collectedAt,
                    collectedAt
            ).toMillis();

            long receivedDelta =
                    receivedBytes - previous.receivedBytes;

            long transmittedDelta =
                    transmittedBytes - previous.transmittedBytes;

            /*
             * Chỉ tính tốc độ khi thời gian hợp lệ,
             * bộ đếm không bị giảm và thời điểm đo tăng.
             */
            if (elapsedMillis > 0
                    && receivedDelta >= 0
                    && transmittedDelta >= 0) {

                double elapsedSeconds = elapsedMillis / 1000.0;

                downloadSpeed = receivedDelta / elapsedSeconds;
                uploadSpeed = transmittedDelta / elapsedSeconds;
            }
        }

        NetworkMetric metric = new NetworkMetric();

        metric.setSnapshot(snapshot);
        metric.setNetworkInterface(networkInterface);
        metric.setReceivedBytes(receivedBytes);
        metric.setTransmittedBytes(transmittedBytes);
        metric.setDownloadSpeed(downloadSpeed);
        metric.setUploadSpeed(uploadSpeed);

        return networkMetricRepository.save(metric);
    }

    private static class PreviousNetworkSample {
        private final long receivedBytes;
        private final long transmittedBytes;
        private final LocalDateTime collectedAt;

        private PreviousNetworkSample(
                long receivedBytes,
                long transmittedBytes,
                LocalDateTime collectedAt) {
            this.receivedBytes = receivedBytes;
            this.transmittedBytes = transmittedBytes;
            this.collectedAt = collectedAt;
        }
    }
}