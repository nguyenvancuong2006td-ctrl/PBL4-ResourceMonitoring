package pbl4.agent.service;

import com.fasterxml.jackson.databind.JsonNode;

import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.RemoteCommandData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RemoteCommandExecutor implements AutoCloseable {

    private static final long TERMINATION_TIMEOUT_MS = 3000;

    private static final long CHECK_INTERVAL_MS = 100;

    private static final long POWER_COMMAND_TIMEOUT_SECONDS = 5;

    /*
     * Đường dẫn lệnh shutdown trên Linux.
     * Cần kiểm tra đường dẫn này khi triển khai trên máy khác.
     */
    private static final String SHUTDOWN_PATH =
            "/usr/sbin/shutdown";

    private final ScreenSharingService screenSharingService;

    /**
     * Khởi tạo RemoteCommandExecutor.
     *
     * @param config cấu hình Agent
     */
    public RemoteCommandExecutor(AgentConfig config) {

        if (config == null) {
            throw new IllegalArgumentException(
                    "AgentConfig không được null."
            );
        }

        this.screenSharingService =
                new ScreenSharingService(config);
    }

    /**
     * Thực thi lệnh nhận được từ Server.
     */
    public String execute(RemoteCommandData command)
            throws Exception {

        if (command == null
                || command.getCommandType() == null
                || command.getCommandType().isBlank()) {

            throw new IllegalArgumentException(
                    "Thông tin lệnh không hợp lệ."
            );
        }

        String commandType = command.getCommandType()
                .trim()
                .toUpperCase(Locale.ROOT);

        switch (commandType) {

            case "KILL_PROCESS":
                return killProcess(command.getParameters());

            case "SHUTDOWN":
                return shutdown();

            case "RESTART":
                return restart();

            case "START_SCREEN_SHARING":
                return screenSharingService.startSharing(
                        getSessionCode(command.getParameters())
                );

            case "STOP_SCREEN_SHARING":
                return screenSharingService.stopSharing(
                        getSessionCode(command.getParameters())
                );

            default:
                throw new UnsupportedOperationException(
                        "Loại lệnh chưa được hỗ trợ: "
                                + commandType
                );
        }
    }

    /**
     * Lấy sessionCode từ parameters của lệnh chia sẻ màn hình.
     */
    private String getSessionCode(JsonNode parameters) {

        if (parameters == null || !parameters.isObject()) {
            throw new IllegalArgumentException(
                    "Parameters của lệnh chia sẻ không hợp lệ."
            );
        }

        JsonNode sessionCodeNode =
                parameters.get("sessionCode");

        if (sessionCodeNode == null
                || !sessionCodeNode.isTextual()
                || sessionCodeNode.asText().isBlank()) {

            throw new IllegalArgumentException(
                    "Không tìm thấy sessionCode hợp lệ."
            );
        }

        return sessionCodeNode.asText().trim();
    }

    /**
     * Yêu cầu dừng tiến trình và xác minh kết quả.
     */
    private String killProcess(JsonNode parameters)
            throws InterruptedException {

        if (parameters == null || !parameters.isObject()) {
            throw new IllegalArgumentException(
                    "Tham số lệnh không hợp lệ."
            );
        }

        JsonNode pidNode = parameters.get("pid");

        if (pidNode == null
                || !pidNode.isIntegralNumber()
                || !pidNode.canConvertToLong()) {

            throw new IllegalArgumentException(
                    "PID phải là số nguyên hợp lệ."
            );
        }

        long pid = pidNode.longValue();

        if (pid <= 0) {
            throw new IllegalArgumentException(
                    "PID phải lớn hơn 0."
            );
        }

        long agentPid = ProcessHandle.current().pid();

        if (pid == agentPid) {
            throw new IllegalArgumentException(
                    "Không thể dừng chính Agent."
            );
        }

        Optional<ProcessHandle> processOptional =
                ProcessHandle.of(pid);

        if (processOptional.isEmpty()) {
            throw new IllegalArgumentException(
                    "Không tìm thấy tiến trình PID " + pid
            );
        }

        ProcessHandle process = processOptional.get();

        if (!process.isAlive()) {
            throw new IllegalArgumentException(
                    "Tiến trình PID " + pid
                            + " không còn hoạt động."
            );
        }

        final boolean accepted;

        try {
            accepted = process.destroy();

        } catch (SecurityException e) {
            throw new IllegalStateException(
                    "Agent không có quyền dừng tiến trình PID "
                            + pid,
                    e
            );
        }

        if (!accepted && process.isAlive()) {
            throw new IllegalStateException(
                    "Không thể gửi yêu cầu dừng tiến trình PID "
                            + pid
            );
        }

        long deadline = System.nanoTime()
                + Duration.ofMillis(
                        TERMINATION_TIMEOUT_MS
                ).toNanos();

        while (process.isAlive()
                && System.nanoTime() < deadline) {

            Thread.sleep(CHECK_INTERVAL_MS);
        }

        if (!process.isAlive()) {
            return "Đã dừng tiến trình PID " + pid
                    + " và xác minh tiến trình không còn hoạt động.";
        }

        throw new IllegalStateException(
                "Đã gửi yêu cầu dừng tiến trình PID " + pid
                        + " nhưng tiến trình vẫn hoạt động sau "
                        + TERMINATION_TIMEOUT_MS + " ms."
        );
    }

    /**
     * Yêu cầu hệ điều hành tắt máy Client.
     */
    private String shutdown() throws Exception {

        return executePowerCommand(
                "SHUTDOWN",
                List.of(
                        "sudo",
                        "-n",
                        SHUTDOWN_PATH,
                        "-h",
                        "now"
                )
        );
    }

    /**
     * Yêu cầu hệ điều hành khởi động lại Client.
     */
    private String restart() throws Exception {

        return executePowerCommand(
                "RESTART",
                List.of(
                        "sudo",
                        "-n",
                        SHUTDOWN_PATH,
                        "-r",
                        "now"
                )
        );
    }

    /**
     * Thực thi lệnh nguồn điện trên Linux.
     *
     * sudo -n không yêu cầu nhập mật khẩu tương tác.
     * Nếu không được cấp quyền, lệnh sẽ thất bại.
     *
     * Mã thoát 0 không chứng minh máy đã tắt hoặc
     * khởi động lại hoàn toàn.
     */
    private String executePowerCommand(
            String commandType,
            List<String> command) throws Exception {

        String osName = System.getProperty(
                "os.name",
                ""
        ).toLowerCase(Locale.ROOT);

        if (!osName.contains("linux")) {
            throw new UnsupportedOperationException(
                    commandType + " hiện chỉ hỗ trợ Linux."
            );
        }

        Process process;

        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể khởi chạy lệnh "
                            + commandType
                            + ". Hãy kiểm tra đường dẫn shutdown "
                            + "và cấu hình hệ thống.",
                    e
            );
        }

        final boolean finished;

        try {
            finished = process.waitFor(
                    POWER_COMMAND_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

        } catch (InterruptedException e) {

            process.destroyForcibly();

            Thread.currentThread().interrupt();

            throw e;
        }

        if (!finished) {

            process.destroyForcibly();

            try {
                process.waitFor(1, TimeUnit.SECONDS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            throw new IllegalStateException(
                    "Lệnh " + commandType
                            + " không kết thúc trong thời gian cho phép. "
                            + "Trạng thái thực tế chưa xác định."
            );
        }

        final String output;

        try (var inputStream = process.getInputStream()) {

            output = new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Lệnh " + commandType
                            + " thất bại. Mã thoát: "
                            + exitCode
                            + (output.isBlank()
                                    ? ""
                                    : ". Chi tiết: " + output)
            );
        }

        return "Lệnh " + commandType
                + " đã được hệ điều hành chấp nhận. "
                + "Chưa xác minh máy đã hoàn tất thao tác.";
    }

    /**
     * Lấy ScreenSharingService.
     *
     * Có thể sử dụng khi cần kiểm tra trạng thái
     * chia sẻ màn hình từ Agent.
     */
    public ScreenSharingService getScreenSharingService() {
        return screenSharingService;
    }

    /**
     * Giải phóng tài nguyên của Executor: dừng phiên chia sẻ màn hình
     * đang chạy (nếu có) để đóng WebSocket, tiến trình thu màn hình
     * và bộ điều khiển chuột/bàn phím.
     *
     * Được gọi khi Agent bị Server từ chối Credential và phải đăng ký
     * lại: không thể để phiên cũ chạy tiếp với Credential đã mất hiệu lực.
     */
    @Override
    public void close() {

        try {
            if (screenSharingService.isSharing()) {

                String sessionCode =
                        screenSharingService.getCurrentSessionCode();

                if (sessionCode != null && !sessionCode.isBlank()) {

                    screenSharingService.stopSharing(sessionCode);
                }
            }

        } catch (Exception e) {

            String message = e.getMessage();

            System.err.println(
                    "[REMOTE] Không thể dừng chia sẻ màn hình khi đóng "
                            + "Executor: "
                            + (message == null || message.isBlank()
                            ? e.getClass().getSimpleName()
                            : message)
            );
        }
    }
}