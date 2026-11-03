package com.pbl4.server.scheduler;

import com.pbl4.server.service.RemoteCommandService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Định kỳ đánh dấu TIMEOUT cho các lệnh Remote bị treo
 * (PENDING / SENT / RUNNING quá thời gian cho phép).
 *
 * Nhờ vậy lệnh gửi cho Agent đang offline sẽ không bị giữ lại và
 * thực thi muộn khi Agent online trở lại.
 */
@Component
public class CommandTimeoutScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(CommandTimeoutScheduler.class);

    private final RemoteCommandService remoteCommandService;

    public CommandTimeoutScheduler(
            RemoteCommandService remoteCommandService) {

        this.remoteCommandService = remoteCommandService;
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    public void expireStaleCommands() {

        try {
            int expired = remoteCommandService.expireStaleCommands();

            if (expired > 0) {
                logger.warn(
                        "Đã chuyển {} lệnh Remote sang TIMEOUT.",
                        expired
                );
            }

        } catch (Exception e) {
            logger.error(
                    "Lỗi khi xử lý lệnh Remote hết hạn: {}",
                    e.getMessage(),
                    e
            );
        }
    }
}