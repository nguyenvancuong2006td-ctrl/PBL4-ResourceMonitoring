package com.pbl4.server.scheduler;

import com.pbl4.server.enums.AgentStatus;
import com.pbl4.server.model.ClientAgent;
import com.pbl4.server.repository.ClientAgentRepository;
import com.pbl4.server.service.ClientAgentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OfflineDetectionScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(OfflineDetectionScheduler.class);

    // Thời gian tối đa Agent được phép không gửi Heartbeat
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 15;

    private final ClientAgentRepository clientAgentRepository;
    private final ClientAgentService clientAgentService;

    public OfflineDetectionScheduler(
            ClientAgentRepository clientAgentRepository,
            ClientAgentService clientAgentService) {

        this.clientAgentRepository = clientAgentRepository;
        this.clientAgentService = clientAgentService;
    }

    /**
     * Kiểm tra trạng thái các Agent định kỳ.
     * Nếu Agent không gửi Heartbeat quá thời gian quy định,
     * chuyển Agent và Client tương ứng sang OFFLINE.
     */
    @Scheduled(fixedRate = 5000)
    public void checkOfflineAgents() {

        LocalDateTime timeout = LocalDateTime.now()
                .minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        try {
            // Lấy danh sách Agent đang ONLINE
            List<ClientAgent> onlineAgents =
                    clientAgentRepository.findByStatus(AgentStatus.ONLINE);

            for (ClientAgent agent : onlineAgents) {

                LocalDateTime lastHeartbeat =
                        agent.getLastHeartbeatAt();

                // Bỏ qua Agent chưa có thời điểm Heartbeat
                if (lastHeartbeat == null) {
                    continue;
                }

                // Kiểm tra Agent đã quá thời gian Heartbeat hay chưa
                if (!lastHeartbeat.isAfter(timeout)) {

                    try {
                        // Giao Service xử lý chuyển trạng thái
                        clientAgentService.disconnect(agent.getAgentId());

                        logger.warn(
                                "Agent {} đã chuyển sang OFFLINE do quá thời gian Heartbeat.",
                                agent.getAgentId()
                        );

                    } catch (Exception e) {

                        logger.error(
                                "Không thể chuyển Agent {} sang OFFLINE: {}",
                                agent.getAgentId(),
                                e.getMessage(),
                                e
                        );
                    }
                }
            }

        } catch (Exception e) {

            logger.error(
                    "Lỗi khi kiểm tra trạng thái Offline của các Agent: {}",
                    e.getMessage(),
                    e
            );
        }
    }
}