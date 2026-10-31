package pbl4.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;

import pbl4.agent.client.ServerClient;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.ClientSystemInfo;

import pbl4.agent.monitoring.ClientInfoService;
import pbl4.agent.monitoring.MonitoringService;

import pbl4.agent.scheduler.HeartbeatScheduler;
import pbl4.agent.scheduler.MonitoringScheduler;
import pbl4.agent.scheduler.RemoteCommandScheduler;

import pbl4.agent.service.RemoteCommandExecutor;


@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {

        // =========================================
        // 1. START AGENT
        // =========================================

        System.out.println("========================================");
        System.out.println("          PBL4 CLIENT AGENT");
        System.out.println("========================================");


        // =========================================
        // 2. LOAD AGENT CONFIGURATION
        // =========================================

        AgentConfig config = new AgentConfig(
                "http://localhost:8080",
                "AGENT-001",
                "1.0.0",
                System.getenv("PBL4_AGENT_CREDENTIAL")
        );

        // Kiểm tra Agent Credential

        if (config.getCredential() == null
                || config.getCredential().isBlank()) {

            throw new IllegalStateException(
                    "Chưa cấu hình biến môi trường "
                            + "PBL4_AGENT_CREDENTIAL"
            );
        }

        System.out.println("[AGENT] Đã tải cấu hình thành công.");
        System.out.println("[AGENT] Agent ID: " + config.getAgentId());


        // =========================================
        // 3. START SPRING APPLICATION
        // =========================================

        SpringApplication app =
                new SpringApplication(AgentApplication.class);

        app.setWebApplicationType(
                WebApplicationType.NONE
        );

        app.run(args);

        System.out.println("[AGENT] Spring Application started.");


        // =========================================
        // 4. INITIALIZE SERVER CLIENT
        // =========================================

        ServerClient serverClient =
                new ServerClient(config);

        System.out.println("[AGENT] Server Client initialized.");


        // =========================================
        // 5. START HEARTBEAT SCHEDULER
        // =========================================

        HeartbeatScheduler heartbeatScheduler =
                new HeartbeatScheduler(serverClient);

        heartbeatScheduler.start();

        System.out.println("[AGENT] Heartbeat Scheduler started.");


        // =========================================
        // 6. GET CLIENT SYSTEM INFORMATION
        // =========================================

        ClientInfoService clientInfoService =
                new ClientInfoService();

        ClientSystemInfo info =
                clientInfoService.getClientInfo(
                        config.getAgentId()
                );

        System.out.println();
        System.out.println("========== CLIENT SYSTEM INFO ==========");

        System.out.println(
                "Client ID      : " + info.getClientId()
        );

        System.out.println(
                "Hostname       : " + info.getHostname()
        );

        System.out.println(
                "IP Address     : " + info.getIpAddress()
        );

        System.out.println(
                "OS             : " + info.getOsName()
        );

        System.out.println(
                "OS Version     : " + info.getOsVersion()
        );

        System.out.println(
                "Kernel Version : " + info.getKernelVersion()
        );

        System.out.println(
                "Boot Time      : " + info.getBootTime()
        );

        System.out.println("========================================");


        // =========================================
        // 7. INITIALIZE MONITORING SERVICE
        // =========================================

        MonitoringService monitoringService =
                new MonitoringService();

        System.out.println("[AGENT] Monitoring Service initialized.");


        // =========================================
        // 8. START MONITORING SCHEDULER
        // =========================================

        MonitoringScheduler monitoringScheduler =
                new MonitoringScheduler(
                        monitoringService,
                        serverClient,
                        config
                );

        monitoringScheduler.start();

        System.out.println("[AGENT] Monitoring Scheduler started.");


        // =========================================
        // 9. INITIALIZE REMOTE COMMAND EXECUTOR
        // =========================================

        RemoteCommandExecutor executor =
                new RemoteCommandExecutor(config);

        System.out.println("[AGENT] Remote Command Executor initialized.");


        // =========================================
        // 10. START REMOTE COMMAND SCHEDULER
        // =========================================

        RemoteCommandScheduler commandScheduler =
                new RemoteCommandScheduler(
                        serverClient,
                        executor
                );

        commandScheduler.start();

        System.out.println("[AGENT] Remote Command Scheduler started.");


        // =========================================
        // 11. AGENT READY
        // =========================================

        System.out.println();
        System.out.println("========================================");
        System.out.println("          AGENT IS RUNNING");
        System.out.println("========================================");

        System.out.println("[AGENT] Heartbeat       : RUNNING");
        System.out.println("[AGENT] Monitoring     : RUNNING");
        System.out.println("[AGENT] Remote Command : RUNNING");
        System.out.println("[AGENT] Screen Sharing : READY");
        System.out.println("========================================");
    }
}