package pbl4.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import pbl4.agent.client.RegistrationClient;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.config.AgentSettings;
import pbl4.agent.monitoring.ClientInfoService;
import pbl4.agent.registration.CredentialStorage;
import pbl4.agent.registration.RegistrationFailedException;
import pbl4.agent.registration.RegistrationManager;

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
        // 2. START SPRING APPLICATION
        //    (chỉ dùng để nạp cấu hình)
        // =========================================

        SpringApplication app =
                new SpringApplication(AgentApplication.class);

        app.setWebApplicationType(
                WebApplicationType.NONE
        );

        // Spring Boot mặc định bật chế độ headless, khi đó java.awt.Robot
        // (thu màn hình trên máy X11) không hoạt động.
        app.setHeadless(false);

        ConfigurableApplicationContext context =
                app.run(args);

        System.out.println("[AGENT] Spring Application started.");


        // =========================================
        // 3. LOAD AGENT SETTINGS
        //    (Server URL, version, thư mục lưu dữ liệu...)
        // =========================================

        AgentSettings settings;

        try {
            settings = AgentSettings.load(
                    context.getEnvironment()::getProperty
            );

        } catch (IllegalArgumentException e) {

            System.err.println(
                    "[AGENT] Cấu hình không hợp lệ: "
                            + e.getMessage()
            );

            context.close();
            System.exit(1);
            return;
        }

        System.out.println("[AGENT] Đã tải cấu hình thành công.");
        System.out.println("[AGENT] Server URL : " + settings.getServerUrl());
        System.out.println("[AGENT] Data Dir   : " + settings.getDataDir());


        // =========================================
        // 4. AGENT REGISTRATION
        //
        //    RegistrationManager
        //      -> đọc trạng thái lưu cục bộ
        //      -> đã đăng ký: kiểm tra Credential còn hợp lệ
        //      -> chưa đăng ký: RegistrationClient gửi yêu cầu lên Server
        //      -> RegistrationScheduler poll trạng thái yêu cầu
        //      -> CredentialStorage lưu Credential rồi gửi ACK
        //
        //    Chặn tại đây cho đến khi Agent có Credential hợp lệ.
        // =========================================

        ClientInfoService clientInfoService =
                new ClientInfoService();

        CredentialStorage credentialStorage =
                new CredentialStorage(settings.getDataDir());

        RegistrationClient registrationClient =
                new RegistrationClient(settings.getServerUrl());

        RegistrationManager registrationManager =
                new RegistrationManager(
                        settings,
                        credentialStorage,
                        registrationClient,
                        () -> clientInfoService.getClientInfo(
                                "UNREGISTERED"
                        )
                );

        AgentConfig config;

        try {
            config = registrationManager.ensureRegistered();

        } catch (RegistrationFailedException e) {

            System.err.println();
            System.err.println("========================================");
            System.err.println("      ĐĂNG KÝ AGENT KHÔNG THÀNH CÔNG");
            System.err.println("========================================");
            System.err.println("[AGENT] " + e.getMessage());
            System.err.println(
                    "[AGENT] Khởi động lại Agent để gửi yêu cầu đăng ký mới."
            );

            context.close();
            System.exit(1);
            return;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "[AGENT] Bị ngắt trong lúc chờ đăng ký. Đang thoát."
            );

            context.close();
            System.exit(1);
            return;
        }

        System.out.println(
                "[AGENT] Đăng ký hoàn tất. Agent ID: "
                        + config.getAgentId()
        );


        // =========================================
        // 5. START AGENT SERVICES
        //
        //    AgentRuntime khởi động Heartbeat, Monitoring, Remote Command
        //    và tự dừng / đăng ký lại / khởi động lại khi Server
        //    từ chối Credential (bị thu hồi, vô hiệu hóa, hết hạn).
        // =========================================

        AgentRuntime runtime =
                new AgentRuntime(
                        context,
                        clientInfoService,
                        credentialStorage,
                        registrationManager
                );

        runtime.start(config);
    }
}