package pbl4.agent.scheduler;

import pbl4.agent.client.ServerClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatScheduler {

    private final ServerClient serverClient;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public HeartbeatScheduler(
            ServerClient serverClient) {

        this.serverClient = serverClient;
    }

    public void start() {

        scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                0,
                5,
                TimeUnit.SECONDS
        );

        System.out.println(
                "Heartbeat Scheduler started - interval: 5 seconds"
        );
    }

    private void sendHeartbeat() {

        try {

            boolean success =
                    serverClient.sendHeartbeat();

            if (success) {

                System.out.println(
                        "Heartbeat sent successfully"
                );

            } else {

                System.out.println(
                        "Heartbeat send failed"
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "Heartbeat error: "
                            + e.getMessage()
            );
        }
    }

    public void stop() {

        scheduler.shutdown();

        System.out.println(
                "Heartbeat Scheduler stopped"
        );
    }
}