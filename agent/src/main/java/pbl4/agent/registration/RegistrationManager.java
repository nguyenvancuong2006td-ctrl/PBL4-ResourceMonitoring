package pbl4.agent.registration;

import pbl4.agent.client.RegistrationClient;
import pbl4.agent.client.RegistrationClient.CredentialCheck;
import pbl4.agent.config.AgentConfig;
import pbl4.agent.config.AgentSettings;
import pbl4.agent.model.AgentRegistrationData;
import pbl4.agent.model.ClientSystemInfo;
import pbl4.agent.model.CredentialPollResponse;
import pbl4.agent.model.RegistrationResponse;
import pbl4.agent.scheduler.RegistrationScheduler;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Điều phối toàn bộ luồng đăng ký Agent:
 *
 *   1. Đọc trạng thái lưu cục bộ (CredentialStorage).
 *   2. Đã đăng ký và Credential còn hợp lệ  -> dùng luôn, không cần đăng ký lại.
 *   3. Chưa đăng ký                         -> gửi yêu cầu qua RegistrationClient.
 *   4. RegistrationScheduler poll trạng thái yêu cầu đăng ký.
 *   5. Admin duyệt -> nhận Credential -> LƯU XUỐNG ĐĨA -> gửi ACK.
 *   6. Trả về AgentConfig hoàn chỉnh để AgentApplication khởi chạy Agent.
 *
 * Mọi bước đều có thể bị ngắt giữa chừng (mất điện, tắt Agent): trạng thái
 * được ghi xuống đĩa sau từng bước, lần khởi động sau sẽ chạy tiếp đúng chỗ.
 */
public class RegistrationManager {

    /**
     * Kết quả của một lần xử lý (một nhịp poll).
     */
    public enum StepResult {

        // Chưa xong, đợi nhịp poll kế tiếp
        CONTINUE,

        // Đăng ký hoàn tất, đã có Credential hợp lệ
        COMPLETED,

        // Thất bại dứt khoát, Agent không tự khắc phục được
        FAILED
    }

    // Số lần tối đa tự đăng ký lại khi Server không còn nhận yêu cầu cũ
    private static final int MAX_REREGISTRATIONS = 3;

    // Chỉ ghi log "đang chờ duyệt" mỗi N lần poll để không tràn log
    private static final int PENDING_LOG_EVERY_N_POLLS = 6;

    private static final int CREDENTIAL_EXPIRY_WARNING_DAYS = 3;

    // Giới hạn độ dài do Server quy định
    private static final int MAX_HOSTNAME_LENGTH = 100;
    private static final int MAX_IP_LENGTH = 50;
    private static final int MAX_OS_FIELD_LENGTH = 50;
    private static final int MAX_VERSION_LENGTH = 20;
    private static final int MAX_CPU_MODEL_LENGTH = 100;
    private static final int MAX_CPU_VENDOR_LENGTH = 50;

    private static final int TOKEN_BYTE_LENGTH = 32;

    private static final String UNKNOWN = "UNKNOWN";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AgentSettings settings;
    private final CredentialStorage storage;
    private final RegistrationClient client;
    private final Supplier<ClientSystemInfo> systemInfoSupplier;

    private volatile RegistrationInfo info;

    private volatile String failureReason;

    private int reRegistrations = 0;

    private int pendingPollCount = 0;

    public RegistrationManager(
            AgentSettings settings,
            CredentialStorage storage,
            RegistrationClient client,
            Supplier<ClientSystemInfo> systemInfoSupplier) {

        if (settings == null
                || storage == null
                || client == null
                || systemInfoSupplier == null) {

            throw new IllegalArgumentException(
                    "Các thành phần của RegistrationManager "
                            + "không được null."
            );
        }

        this.settings = settings;
        this.storage = storage;
        this.client = client;
        this.systemInfoSupplier = systemInfoSupplier;
        this.info = newInfo();
    }

    // ==========================================
    // ĐIỂM VÀO: ĐẢM BẢO AGENT ĐÃ ĐĂNG KÝ
    // ==========================================

    /**
     * Chặn cho đến khi Agent có Credential hợp lệ.
     *
     * @return AgentConfig đã có Agent ID và Credential do Server cấp
     * @throws RegistrationFailedException khi Admin từ chối hoặc
     *         Server từ chối thông tin đăng ký
     * @throws InterruptedException khi luồng chờ bị ngắt
     */
    public AgentConfig ensureRegistered()
            throws RegistrationFailedException, InterruptedException {

        // Đã đăng ký từ trước, Credential còn dùng được
        if (resumeFromLocalState()) {
            return createAgentConfig();
        }

        RegistrationScheduler scheduler = new RegistrationScheduler(
                this,
                settings.getPollIntervalSeconds()
        );

        try {
            scheduler.start().get();

        } catch (ExecutionException e) {

            Throwable cause = e.getCause();

            if (cause instanceof RegistrationFailedException failed) {
                throw failed;
            }

            throw new RegistrationFailedException(
                    "Đăng ký Agent thất bại: "
                            + (cause == null
                            ? e.getMessage()
                            : cause.getMessage()),
                    cause
            );

        } finally {
            scheduler.stop();
        }

        return createAgentConfig();
    }

    // ==========================================
    // BƯỚC 1: ĐỌC TRẠNG THÁI CỤC BỘ
    // ==========================================

    /**
     * Đọc trạng thái đã lưu và quyết định bước tiếp theo.
     *
     * @return true nếu Agent đã đăng ký và có thể chạy ngay;
     *         false nếu cần chạy luồng đăng ký (đăng ký mới,
     *         hoặc tiếp tục yêu cầu đang chờ duyệt)
     */
    public boolean resumeFromLocalState() {

        RegistrationInfo loaded = storage.load().orElse(null);

        if (loaded == null) {
            log("Chưa có dữ liệu đăng ký cục bộ.");
            info = newInfo();
            return false;
        }

        if (!isSameServer(loaded.getServerUrl())) {
            log("Dữ liệu đăng ký thuộc Server khác ("
                    + loaded.getServerUrl()
                    + "). Bỏ qua và đăng ký lại.");
            resetLocalState();
            return false;
        }

        RegistrationState state = loaded.getState();

        switch (state == null
                ? RegistrationState.NOT_REGISTERED
                : state) {

            case REGISTERED:
                return resumeRegisteredAgent(loaded);

            case PENDING:
            case APPROVED:
                if (hasText(loaded.getAgentId())
                        && hasText(loaded.getRegistrationToken())) {

                    log("Tiếp tục yêu cầu đăng ký đang dở dang. "
                            + "Agent ID: " + loaded.getAgentId());

                    info = loaded;
                    return false;
                }

                log("Yêu cầu đăng ký dở dang thiếu dữ liệu. "
                        + "Đăng ký lại.");
                resetLocalState();
                return false;

            case REJECTED:
                log("Yêu cầu đăng ký lần trước đã bị từ chối. "
                        + "Gửi yêu cầu đăng ký mới.");
                resetLocalState();
                return false;

            default:
                log("Trạng thái đăng ký cục bộ là " + state
                        + ". Đăng ký lại từ đầu.");
                resetLocalState();
                return false;
        }
    }

    /**
     * Agent ghi nhận đã đăng ký: kiểm tra Credential còn dùng được không.
     */
    private boolean resumeRegisteredAgent(RegistrationInfo loaded) {

        if (!hasText(loaded.getAgentId()) || !loaded.hasCredential()) {
            warn("Dữ liệu đăng ký thiếu Agent ID hoặc Credential. "
                    + "Đăng ký lại.");
            resetLocalState();
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // Kiểm tra hạn ngay trên máy, không cần hỏi Server
        if (loaded.isCredentialExpired(now)) {
            warn("Credential đã hết hạn lúc "
                    + loaded.getCredentialExpiresAt()
                    + ". Đăng ký lại.");
            resetLocalState();
            return false;
        }

        // Hỏi Server Credential có còn được chấp nhận không
        CredentialCheck check = client.verifyCredential(
                loaded.getAgentId(),
                loaded.getCredential()
        );

        if (check == CredentialCheck.INVALID) {
            warn("Server không còn chấp nhận Credential "
                    + "(bị thu hồi hoặc vô hiệu hóa). Đăng ký lại.");
            resetLocalState();
            return false;
        }

        if (check == CredentialCheck.UNREACHABLE) {
            warn("Chưa kết nối được Server để xác minh Credential. "
                    + "Vẫn khởi chạy Agent bằng Credential đã lưu.");
        }

        LocalDateTime warnFrom = loaded.getCredentialExpiresAt()
                .minusDays(CREDENTIAL_EXPIRY_WARNING_DAYS);

        if (!now.isBefore(warnFrom)) {
            warn("Credential sắp hết hạn lúc "
                    + loaded.getCredentialExpiresAt()
                    + ". Cần đăng ký lại sau thời điểm này.");
        }

        info = loaded;

        log("Agent đã đăng ký. Agent ID: " + loaded.getAgentId());

        return true;
    }

    // ==========================================
    // BƯỚC 2: MỘT NHỊP XỬ LÝ (do Scheduler gọi)
    // ==========================================

    /**
     * Thực hiện đúng một bước tùy theo trạng thái hiện tại:
     * chưa gửi yêu cầu thì gửi, đã gửi thì poll.
     */
    public StepResult processStep() {

        try {
            switch (info.getState()) {

                case PENDING:
                case APPROVED:
                    return pollCredential();

                case REGISTERED:
                    return StepResult.COMPLETED;

                default:
                    return submitRegistrationRequest();
            }

        } catch (RuntimeException e) {
            warn("Lỗi không mong đợi khi xử lý đăng ký, sẽ thử lại: "
                    + safeMessage(e));

            return StepResult.CONTINUE;
        }
    }

    // ==========================================
    // BƯỚC 3: GỬI YÊU CẦU ĐĂNG KÝ
    // ==========================================

    private StepResult submitRegistrationRequest() {

        ClientSystemInfo system = systemInfoSupplier.get();

        // Mỗi lần gửi dùng một token mới. Nếu lần trước Server đã nhận
        // nhưng phản hồi bị mất thì token cũ không dùng lại được.
        String token = generateRegistrationToken();

        AgentRegistrationData data = new AgentRegistrationData(
                sanitize(system.getHostname(), MAX_HOSTNAME_LENGTH),
                sanitize(system.getIpAddress(), MAX_IP_LENGTH),
                sanitize(system.getOsName(), MAX_OS_FIELD_LENGTH),
                sanitize(system.getOsVersion(), MAX_OS_FIELD_LENGTH),
                sanitize(system.getKernelVersion(), MAX_OS_FIELD_LENGTH),
                system.getBootTime() == null
                        ? LocalDateTime.now()
                        : system.getBootTime(),
                sanitize(settings.getVersion(), MAX_VERSION_LENGTH),
                token
        );

        // Phần cứng là thông tin bổ sung: thiếu thì để null, Server vẫn nhận.
        data.setCpuModel(truncate(system.getCpuModel(), MAX_CPU_MODEL_LENGTH));
        data.setCpuManufacturer(
                truncate(system.getCpuManufacturer(), MAX_CPU_VENDOR_LENGTH));
        data.setCpuCoreCount(system.getCpuCoreCount());
        data.setCpuThreadCount(system.getCpuThreadCount());
        data.setCpuFrequencyMhz(system.getCpuFrequencyMhz());
        data.setMemoryTotalBytes(system.getMemoryTotalBytes());

        RegistrationResponse response;

        try {
            response = client.register(data);

        } catch (RegistrationException e) {

            if (e.isRetryable()) {
                warn("Chưa gửi được yêu cầu đăng ký, sẽ thử lại: "
                        + e.getMessage());

                return StepResult.CONTINUE;
            }

            return fail(
                    RegistrationState.ERROR,
                    "Server từ chối yêu cầu đăng ký: "
                            + e.getMessage()
            );
        }

        if (!hasText(response.getAgentId())) {
            warn("Server không trả Agent ID. Sẽ thử đăng ký lại.");
            return StepResult.CONTINUE;
        }

        RegistrationInfo registered = newInfo();

        registered.setAgentId(response.getAgentId());
        registered.setClientId(response.getClientId());
        registered.setRegistrationToken(token);
        registered.setState(RegistrationState.PENDING);

        info = registered;

        pendingPollCount = 0;

        // Lưu ngay để nếu Agent tắt đột ngột vẫn poll tiếp được
        persistBestEffort();

        log("Đã gửi yêu cầu đăng ký. Agent ID: "
                + response.getAgentId()
                + ". Đang chờ quản trị viên phê duyệt...");

        return StepResult.CONTINUE;
    }

    // ==========================================
    // BƯỚC 4: POLL TRẠNG THÁI YÊU CẦU
    // ==========================================

    private StepResult pollCredential() {

        CredentialPollResponse response;

        try {
            response = client.pollCredential(
                    info.getAgentId(),
                    info.getRegistrationToken()
            );

        } catch (RegistrationException e) {

            if (e.isRetryable()) {
                warn("Chưa poll được trạng thái đăng ký, sẽ thử lại: "
                        + e.getMessage());

                return StepResult.CONTINUE;
            }

            return handleInvalidRegistration(e.getMessage());
        }

        String status = response.getStatus() == null
                ? ""
                : response.getStatus().trim().toUpperCase(Locale.ROOT);

        switch (status) {

            case "PENDING":
                logPending();
                return StepResult.CONTINUE;

            case "APPROVED":
                return receiveCredential(response);

            case "DELIVERED":
                return handleAlreadyDelivered();

            case "REJECTED":
                return fail(
                        RegistrationState.REJECTED,
                        "Yêu cầu đăng ký đã bị quản trị viên từ chối."
                );

            case "EXPIRED":
                return handleInvalidRegistration(
                        "Yêu cầu đăng ký đã hết hạn."
                );

            default:
                warn("Trạng thái đăng ký không nhận diện được: '"
                        + response.getStatus() + "'. Sẽ thử lại.");

                return StepResult.CONTINUE;
        }
    }

    private void logPending() {

        if (pendingPollCount % PENDING_LOG_EVERY_N_POLLS == 0) {
            log("Yêu cầu đăng ký đang chờ phê duyệt...");
        }

        pendingPollCount++;
    }

    // ==========================================
    // BƯỚC 5: NHẬN CREDENTIAL -> LƯU -> ACK
    // ==========================================

    private StepResult receiveCredential(
            CredentialPollResponse response) {

        if (!hasText(response.getCredential())) {
            warn("Server báo APPROVED nhưng không kèm Credential. "
                    + "Sẽ thử lại.");

            return StepResult.CONTINUE;
        }

        log("Yêu cầu đăng ký đã được phê duyệt. "
                + "Đang nhận Credential...");

        info.setState(RegistrationState.APPROVED);
        info.setCredential(response.getCredential());
        info.setCredentialExpiresAt(response.getExpiresAt());

        // QUY TẮC QUAN TRỌNG: lưu thành công rồi mới ACK.
        // Nếu chưa lưu được mà ACK, Server sẽ xóa Credential
        // và Agent mất Credential vĩnh viễn.
        try {
            storage.save(info);

        } catch (IOException e) {
            warn("Không thể lưu Credential xuống đĩa (" + safeMessage(e)
                    + "). Chưa gửi ACK để Server còn giữ Credential.");

            return StepResult.CONTINUE;
        }

        log("Đã lưu Credential tại: " + storage.getStateFile());

        try {
            client.acknowledgeCredential(
                    info.getAgentId(),
                    info.getRegistrationToken()
            );

        } catch (RegistrationException e) {

            if (e.isRetryable()) {
                // Credential đã an toàn trên đĩa. Lần poll sau Server
                // vẫn trả đúng Credential này (chưa ACK) rồi ACK lại.
                warn("Chưa gửi được ACK, sẽ thử lại: " + e.getMessage());

                return StepResult.CONTINUE;
            }

            return handleInvalidRegistration(
                    "Server từ chối ACK: " + e.getMessage()
            );
        }

        info.setState(RegistrationState.REGISTERED);

        // ACK đã xong. Nếu ghi trạng thái cuối thất bại thì lần chạy
        // sau Server trả DELIVERED và Agent tự phục hồi từ file đã lưu.
        persistBestEffort();

        log("Đăng ký hoàn tất. Agent ID: " + info.getAgentId());

        return StepResult.COMPLETED;
    }

    /**
     * Server báo Credential đã được giao (đã nhận ACK trước đó).
     * Xảy ra khi Agent tắt ngay sau khi ACK, trước khi kịp ghi
     * trạng thái REGISTERED.
     */
    private StepResult handleAlreadyDelivered() {

        if (info.hasCredential()
                && !info.isCredentialExpired(LocalDateTime.now())) {

            log("Server xác nhận đã giao Credential. "
                    + "Khôi phục từ bản đã lưu.");

            info.setState(RegistrationState.REGISTERED);

            persistBestEffort();

            return StepResult.COMPLETED;
        }

        return handleInvalidRegistration(
                "Server đã giao Credential nhưng máy không còn "
                        + "bản lưu để khôi phục."
        );
    }

    // ==========================================
    // XỬ LÝ YÊU CẦU KHÔNG CÒN HIỆU LỰC
    // ==========================================

    /**
     * Server không còn nhận yêu cầu hiện tại (hết hạn, token không khớp,
     * Credential bị mất...). Xóa dữ liệu cục bộ để đăng ký lại từ đầu,
     * nhưng giới hạn số lần để tránh vòng lặp vô hạn.
     */
    private StepResult handleInvalidRegistration(String reason) {

        reRegistrations++;

        if (reRegistrations > MAX_REREGISTRATIONS) {
            return fail(
                    RegistrationState.ERROR,
                    "Đã đăng ký lại " + MAX_REREGISTRATIONS
                            + " lần nhưng vẫn không thành công. "
                            + "Lỗi gần nhất: " + reason
            );
        }

        warn(reason + " Sẽ đăng ký lại ("
                + reRegistrations + "/" + MAX_REREGISTRATIONS + ").");

        resetLocalState();

        return StepResult.CONTINUE;
    }

    private StepResult fail(
            RegistrationState finalState,
            String reason) {

        failureReason = reason;

        info.setState(finalState);

        persistBestEffort();

        err(reason);

        return StepResult.FAILED;
    }

    // ==========================================
    // TẠO AGENT CONFIG
    // ==========================================

    /**
     * Tạo cấu hình cho các thành phần còn lại của Agent
     * (heartbeat, monitoring, remote command...).
     */
    public AgentConfig createAgentConfig() {

        RegistrationInfo current = info;

        if (current.getState() != RegistrationState.REGISTERED
                || !hasText(current.getAgentId())
                || !current.hasCredential()) {

            throw new IllegalStateException(
                    "Agent chưa đăng ký xong, chưa thể tạo AgentConfig."
            );
        }

        return new AgentConfig(
                settings.getServerUrl(),
                current.getAgentId(),
                settings.getVersion(),
                current.getCredential()
        );
    }

    public RegistrationInfo getInfo() {
        return info;
    }

    public String getFailureReason() {
        return failureReason;
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private RegistrationInfo newInfo() {

        RegistrationInfo fresh = new RegistrationInfo();

        fresh.setServerUrl(settings.getServerUrl());

        return fresh;
    }

    private void resetLocalState() {

        storage.clear();

        info = newInfo();

        pendingPollCount = 0;
    }

    private void persistBestEffort() {

        try {
            storage.save(info);

        } catch (IOException e) {
            warn("Không thể ghi trạng thái đăng ký xuống đĩa: "
                    + safeMessage(e));
        }
    }

    private boolean isSameServer(String storedUrl) {

        if (!hasText(storedUrl)) {
            return true;
        }

        return normalizeUrl(storedUrl)
                .equalsIgnoreCase(normalizeUrl(settings.getServerUrl()));
    }

    private String normalizeUrl(String url) {

        String result = url.trim();

        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }

        return result;
    }

    /**
     * Server yêu cầu các trường không rỗng và có giới hạn độ dài.
     */
    private String truncate(String value, int maxLength) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String result = value.trim();

        return result.length() > maxLength
                ? result.substring(0, maxLength)
                : result;
    }

    private String sanitize(String value, int maxLength) {

        String result = value == null ? "" : value.trim();

        if (result.isEmpty()) {
            return UNKNOWN;
        }

        if (result.length() > maxLength) {
            result = result.substring(0, maxLength);
        }

        return result;
    }

    /**
     * 256 bit ngẫu nhiên, mã hóa Base64 URL-safe (43 ký tự).
     * Server chỉ lưu bản băm SHA-256 của token này.
     */
    private String generateRegistrationToken() {

        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];

        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeMessage(Exception e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }

    private void log(String message) {
        System.out.println("[REGISTRATION] " + message);
    }

    private void warn(String message) {
        System.err.println("[REGISTRATION] " + message);
    }

    private void err(String message) {
        System.err.println("[REGISTRATION] LỖI: " + message);
    }
}