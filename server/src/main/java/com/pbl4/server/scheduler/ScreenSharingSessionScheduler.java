package com.pbl4.server.scheduler;

import com.pbl4.server.service.ScreenSharingSessionService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScreenSharingSessionScheduler {

    private final ScreenSharingSessionService sessionService;

    public ScreenSharingSessionScheduler(
            ScreenSharingSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedRate = 10_000)
    public void expireStaleSessions() {
        try {
            sessionService.expireStaleSessions();
        } catch (Exception e) {
            System.err.println(
                    "[SCREEN SESSION] Lỗi dọn phiên: " + e.getMessage());
        }
    }
}