package pbl4.agent;

import pbl4.agent.client.ServerClient;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.ClientSystemInfo;

import pbl4.agent.monitoring.ClientInfoService;
import pbl4.agent.monitoring.MonitoringService;

import pbl4.agent.scheduler.HeartbeatScheduler;
import pbl4.agent.scheduler.MonitoringScheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {

        // ==============================
        // START AGENT
        // ==============================

        System.out.println("================================");
        System.out.println("       PBL4 CLIENT AGENT");
        System.out.println("================================");

        SpringApplication app =
                new SpringApplication(AgentApplication.class);

        app.setWebApplicationType(
                WebApplicationType.NONE
        );

        app.run(args);

        // ==============================
        // AGENT CONFIGURATION
        // ==============================

        AgentConfig config =
                new AgentConfig(
                        "http://localhost:8080",
                        "AGENT-001",
                        "1.0.0",
                        "d4d68bf8-9cf7-4283-9469-56477c3b505c"
                );

        // ==============================
        // CONNECT TO SERVER
        // ==============================

        ServerClient serverClient =
                new ServerClient(config);

        // ==============================
        // START HEARTBEAT SCHEDULER
        // ==============================

        HeartbeatScheduler heartbeatScheduler =
                new HeartbeatScheduler(
                        serverClient
                );

        heartbeatScheduler.start();

        // ==============================
        // CLIENT SYSTEM INFORMATION
        // ==============================

        ClientInfoService clientInfoService =
                new ClientInfoService();

        ClientSystemInfo info =
                clientInfoService.getClientInfo(
                        config.getAgentId()
                );

        System.out.println();
        System.out.println(
                "========== CLIENT INFO =========="
        );

        System.out.println(
                "Client ID      : "
                        + info.getClientId()
        );

        System.out.println(
                "Hostname       : "
                        + info.getHostname()
        );

        System.out.println(
                "IP Address     : "
                        + info.getIpAddress()
        );

        System.out.println(
                "OS             : "
                        + info.getOsName()
        );

        System.out.println(
                "OS Version     : "
                        + info.getOsVersion()
        );

        System.out.println(
                "Kernel Version : "
                        + info.getKernelVersion()
        );

        System.out.println(
                "Boot Time      : "
                        + info.getBootTime()
        );

        System.out.println(
                "================================"
        );

        // ==============================
        // CREATE MONITORING SERVICE
        // ==============================

        MonitoringService monitoringService =
                new MonitoringService();

        // ==============================
        // START MONITORING SCHEDULER
        // ==============================

        MonitoringScheduler monitoringScheduler =
                new MonitoringScheduler(
                        monitoringService,
                        serverClient,
                        config
                );

        monitoringScheduler.start();
    }
}