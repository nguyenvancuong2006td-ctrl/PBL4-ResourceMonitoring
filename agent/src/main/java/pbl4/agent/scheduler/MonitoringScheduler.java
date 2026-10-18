package pbl4.agent.scheduler;

import pbl4.agent.client.ServerClient;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.MonitoringData;
import pbl4.agent.monitoring.MonitoringService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitoringScheduler {

    private final MonitoringService monitoringService;
    private final ServerClient serverClient;
    private final AgentConfig config;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public MonitoringScheduler(
            MonitoringService monitoringService,
            ServerClient serverClient,
            AgentConfig config) {

        this.monitoringService = monitoringService;
        this.serverClient = serverClient;
        this.config = config;
    }

    public void start() {

        scheduler.scheduleAtFixedRate(
                this::collectAndSend,
                0,
                10,
                TimeUnit.SECONDS
        );

        System.out.println(
                "Monitoring Scheduler started - interval: 10 seconds"
        );
    }

    private void collectAndSend() {

        try {

            System.out.println();
            System.out.println(
                    "========== COLLECT MONITORING =========="
            );

            MonitoringData data =
                    monitoringService.collect(
                            config.getAgentId()
                    );

            System.out.println(data);

            boolean success =
                    serverClient.sendMonitoringData(data);

            if (success) {

                System.out.println(
                        "Monitoring data sent successfully"
                );

            } else {

                System.out.println(
                        "Monitoring data send failed"
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "Monitoring error: "
                            + e.getMessage()
            );
        }
    }

    public void stop() {

        scheduler.shutdown();

        System.out.println(
                "Monitoring Scheduler stopped"
        );
    }
}