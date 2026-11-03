package pbl4.agent;

import org.springframework.context.ConfigurableApplicationContext;

import pbl4.agent.client.ServerClient;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.ClientSystemInfo;
import pbl4.agent.monitoring.ClientInfoService;
import pbl4.agent.monitoring.MonitoringService;
import pbl4.agent.registration.CredentialStorage;
import pbl4.agent.registration.RegistrationFailedException;
import pbl4.agent.registration.RegistrationManager;
import pbl4.agent.scheduler.HeartbeatScheduler;
import pbl4.agent.scheduler.MonitoringScheduler;
import pbl4.agent.scheduler.RemoteCommandScheduler;
import pbl4.agent.service.RemoteCommandExecutor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quản lý vòng đời các thành phần chạy nền của Agent
 * (Heartbeat, Monitoring, Remote Command) và việc tự phục hồi khi
 * Server từ chối Credential.
 *
 * Luồng phục hồi:
 *
 *   ServerClient nhận HTTP 401 liên tiếp
 *     -> onCredentialRejected()  (chạy trên luồng đang gửi request)
 *     -> mở luồng "credential-recovery" riêng (không chặn Scheduler)
 *          1. Dừng Heartbeat / Monitoring / Remote Command,
 *             dừng chia sẻ màn hình đang chạy
 *          2. Xóa trạng thái đăng ký cục bộ (Credential cũ)
 *          3. RegistrationManager.ensureRegistered(): gửi yêu cầu đăng
 *             ký mới và chờ Admin duyệt
 *          4. Nhận Credential mới -> khởi động lại các thành phần
 *
 * Nếu đăng ký lại thất bại dứt khoát (Admin từ chối...), Agent thoát
 * giống như khi đăng ký thất bại lúc khởi động.
 */
public class AgentRuntime {

    private final ConfigurableApplicationContext context;
    private final ClientInfoService clientInfoService;
    private final CredentialStorage credentialStorage;
    private final RegistrationManager registrationManager;

    // Chỉ cho phép một tiến trình phục hồi tại một thời điểm
    private final AtomicBoolean recovering = new AtomicBoolean(false);

    private MonitoringService monitoringService;

    private HeartbeatScheduler heartbeatScheduler;
    private MonitoringScheduler monitoringScheduler;
    private RemoteCommandScheduler commandScheduler;
    private RemoteCommandExecutor commandExecutor;

    public AgentRuntime(
            ConfigurableApplicationContext context,
            ClientInfoService clientInfoService,
            CredentialStorage credentialStorage,
            RegistrationManager registrationManager) {

        if (context == null
                || clientInfoService == null
                || credentialStorage == null
                || registrationManager == null) {

            throw new IllegalArgumentException(
                    "Các thành phần của AgentRuntime không được null."
            );
        }

        this.context = context;
        this.clientInfoService = clientInfoService;
        this.credentialStorage = credentialStorage;
        this.registrationManager = registrationManager;
    }

    // ==========================================
    // KHỞI ĐỘNG CÁC THÀNH PHẦN CHẠY NỀN
    // ==========================================

    public synchronized void start(AgentConfig config) {

        // Server Client
        ServerClient serverClient = new ServerClient(config);

        serverClient.setCredentialRejectedListener(
                this::onCredentialRejected
        );

        System.out.println("[AGENT] Server Client initialized.");

        // Heartbeat
        heartbeatScheduler = new HeartbeatScheduler(serverClient);
        heartbeatScheduler.start();

        System.out.println("[AGENT] Heartbeat Scheduler started.");

        // Thông tin máy Client
        printClientInfo(config);

        // Monitoring
        if (monitoringService == null) {
            monitoringService = new MonitoringService();
        }

        System.out.println("[AGENT] Monitoring Service initialized.");

        monitoringScheduler = new MonitoringScheduler(
                monitoringService,
                serverClient,
                config
        );

        monitoringScheduler.start();

        System.out.println("[AGENT] Monitoring Scheduler started.");

        // Remote Command
        commandExecutor = new RemoteCommandExecutor(config);

        System.out.println("[AGENT] Remote Command Executor initialized.");

        commandScheduler = new RemoteCommandScheduler(
                serverClient,
                commandExecutor
        );

        commandScheduler.start();

        System.out.println("[AGENT] Remote Command Scheduler started.");

        // Sẵn sàng
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

    // ==========================================
    // DỪNG CÁC THÀNH PHẦN CHẠY NỀN
    // ==========================================

    public synchronized void stop() {

        // Dừng nhận lệnh mới trước, rồi mới dừng phiên đang chạy
        if (commandScheduler != null) {
            commandScheduler.stop();
            commandScheduler = null;
        }

        if (commandExecutor != null) {
            commandExecutor.close();
            commandExecutor = null;
        }

        if (monitoringScheduler != null) {
            monitoringScheduler.stop();
            monitoringScheduler = null;
        }

        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
            heartbeatScheduler = null;
        }
    }

    // ==========================================
    // SERVER TỪ CHỐI CREDENTIAL
    // ==========================================

    /**
     * Được ServerClient gọi trên luồng của Scheduler. Chỉ mở luồng
     * phục hồi rồi trả về ngay: việc dừng Scheduler từ chính luồng của
     * nó và việc chờ Admin duyệt đều không được làm ở đây.
     */
    private void onCredentialRejected() {

        if (!recovering.compareAndSet(false, true)) {
            return;
        }

        System.err.println();
        System.err.println("========================================");
        System.err.println("   CREDENTIAL BỊ SERVER TỪ CHỐI");
        System.err.println("========================================");
        System.err.println(
                "[AGENT] Credential đã bị thu hồi, vô hiệu hóa hoặc hết "
                        + "hạn. Agent sẽ dừng hoạt động và đăng ký lại."
        );

        Thread thread = new Thread(
                this::recover,
                "credential-recovery"
        );

        // Phải là luồng thường: khi các Scheduler đã dừng, đây là luồng
        // duy nhất giữ JVM sống trong lúc chờ Admin duyệt.
        // (Luồng mới kế thừa cờ daemon từ luồng tạo ra nó, nên đặt rõ.)
        thread.setDaemon(false);

        thread.start();
    }

    private void recover() {

        try {
            stop();

            credentialStorage.clear();

            System.out.println(
                    "[AGENT] Đã xóa Credential cũ. Gửi yêu cầu đăng ký mới..."
            );

            AgentConfig newConfig =
                    registrationManager.ensureRegistered();

            System.out.println(
                    "[AGENT] Đăng ký lại hoàn tất. Agent ID: "
                            + newConfig.getAgentId()
            );

            start(newConfig);

        } catch (RegistrationFailedException e) {

            System.err.println();
            System.err.println("========================================");
            System.err.println("      ĐĂNG KÝ LẠI KHÔNG THÀNH CÔNG");
            System.err.println("========================================");
            System.err.println("[AGENT] " + e.getMessage());
            System.err.println(
                    "[AGENT] Khởi động lại Agent để gửi yêu cầu đăng ký mới."
            );

            shutdown(1);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "[AGENT] Bị ngắt trong lúc đăng ký lại. Đang thoát."
            );

            shutdown(1);

        } catch (Throwable t) {

            System.err.println(
                    "[AGENT] Lỗi không mong muốn khi đăng ký lại: "
                            + t.getClass().getSimpleName()
                            + (t.getMessage() == null
                            ? ""
                            : ": " + t.getMessage())
            );

            shutdown(1);

        } finally {
            recovering.set(false);
        }
    }

    // ==========================================
    // HÀM PHỤ
    // ==========================================

    private void printClientInfo(AgentConfig config) {

        ClientSystemInfo info =
                clientInfoService.getClientInfo(config.getAgentId());

        System.out.println();
        System.out.println("========== CLIENT SYSTEM INFO ==========");
        System.out.println("Client ID      : " + info.getClientId());
        System.out.println("Hostname       : " + info.getHostname());
        System.out.println("IP Address     : " + info.getIpAddress());
        System.out.println("OS             : " + info.getOsName());
        System.out.println("OS Version     : " + info.getOsVersion());
        System.out.println("Kernel Version : " + info.getKernelVersion());
        System.out.println("Boot Time      : " + info.getBootTime());
        System.out.println("CPU            : " + info.getCpuModel());
        System.out.println("CPU Cores      : " + info.getCpuCoreCount()
                + " nhân / " + info.getCpuThreadCount() + " luồng");
        System.out.println("CPU Frequency  : " + info.getCpuFrequencyMhz() + " MHz");
        System.out.println("RAM Total      : " + info.getMemoryTotalBytes() + " bytes");
        System.out.println("========================================");
    }

    private void shutdown(int exitCode) {

        try {
            stop();
            context.close();

        } finally {
            System.exit(exitCode);
        }
    }
}