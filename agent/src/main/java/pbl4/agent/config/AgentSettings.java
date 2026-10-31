package pbl4.agent.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

/**
 * Cấu hình tĩnh của Agent, đọc từ application.properties
 * hoặc biến môi trường (ví dụ AGENT_SERVER_URL).
 *
 * Khác với AgentConfig: AgentSettings tồn tại TRƯỚC khi Agent đăng ký,
 * không chứa Agent ID hay Credential (hai giá trị này do Server cấp
 * trong quá trình đăng ký).
 */
public class AgentSettings {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8080";
    public static final String DEFAULT_VERSION = "1.0.0";
    public static final long DEFAULT_POLL_INTERVAL_SECONDS = 10;

    private static final String KEY_SERVER_URL = "agent.server-url";
    private static final String KEY_VERSION = "agent.version";
    private static final String KEY_DATA_DIR = "agent.data-dir";
    private static final String KEY_POLL_INTERVAL =
            "agent.registration.poll-interval-seconds";

    private final String serverUrl;
    private final String version;
    private final Path dataDir;
    private final long pollIntervalSeconds;

    public AgentSettings(
            String serverUrl,
            String version,
            Path dataDir,
            long pollIntervalSeconds) {

        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Server URL không được để trống."
            );
        }

        String normalizedUrl = serverUrl.trim();

        if (!normalizedUrl.startsWith("http://")
                && !normalizedUrl.startsWith("https://")) {

            throw new IllegalArgumentException(
                    "Server URL phải bắt đầu bằng http:// hoặc https://"
            );
        }

        while (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(
                    0,
                    normalizedUrl.length() - 1
            );
        }

        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(
                    "Agent version không được để trống."
            );
        }

        // Server giới hạn agentVersion tối đa 20 ký tự
        if (version.trim().length() > 20) {
            throw new IllegalArgumentException(
                    "Agent version tối đa 20 ký tự."
            );
        }

        if (dataDir == null) {
            throw new IllegalArgumentException(
                    "Thư mục dữ liệu Agent không được null."
            );
        }

        if (pollIntervalSeconds < 1) {
            throw new IllegalArgumentException(
                    "Chu kỳ poll đăng ký phải >= 1 giây."
            );
        }

        this.serverUrl = normalizedUrl;
        this.version = version.trim();
        this.dataDir = dataDir;
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    /**
     * Đọc cấu hình thông qua một hàm tra cứu thuộc tính.
     *
     * Ví dụ: AgentSettings.load(context.getEnvironment()::getProperty)
     * Cách này giúp class không phụ thuộc trực tiếp vào Spring.
     */
    public static AgentSettings load(
            Function<String, String> lookup) {

        String serverUrl = valueOrDefault(
                lookup.apply(KEY_SERVER_URL),
                DEFAULT_SERVER_URL
        );

        String version = valueOrDefault(
                lookup.apply(KEY_VERSION),
                DEFAULT_VERSION
        );

        Path dataDir = resolveDataDir(
                lookup.apply(KEY_DATA_DIR)
        );

        long pollInterval = parsePollInterval(
                lookup.apply(KEY_POLL_INTERVAL)
        );

        return new AgentSettings(
                serverUrl,
                version,
                dataDir,
                pollInterval
        );
    }

    private static String valueOrDefault(
            String value,
            String defaultValue) {

        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static Path resolveDataDir(String configured) {

        try {
            if (configured != null && !configured.isBlank()) {
                return Paths.get(configured.trim())
                        .toAbsolutePath()
                        .normalize();
            }

            return Paths.get(
                    System.getProperty("user.home"),
                    ".pbl4-agent"
            );

        } catch (InvalidPathException e) {
            throw new IllegalArgumentException(
                    "Đường dẫn " + KEY_DATA_DIR + " không hợp lệ: "
                            + configured,
                    e
            );
        }
    }

    private static long parsePollInterval(String configured) {

        if (configured == null || configured.isBlank()) {
            return DEFAULT_POLL_INTERVAL_SECONDS;
        }

        try {
            return Long.parseLong(configured.trim());

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    KEY_POLL_INTERVAL + " phải là số nguyên: "
                            + configured,
                    e
            );
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getVersion() {
        return version;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public long getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }
}