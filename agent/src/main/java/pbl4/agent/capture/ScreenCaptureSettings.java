package pbl4.agent.capture;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình thu màn hình của Agent.
 *
 * Mỗi giá trị đọc theo thứ tự ưu tiên:
 *   1. System property (-Dagent.screen.fps=10)
 *   2. Biến môi trường (AGENT_SCREEN_FPS=10)
 *   3. Giá trị mặc định
 *
 * Biến / property hỗ trợ:
 *   agent.screen.fps        / AGENT_SCREEN_FPS        : số khung hình tối đa mỗi giây (mặc định 8)
 *   agent.screen.max-width  / AGENT_SCREEN_MAX_WIDTH  : chiều rộng tối đa, 0 = không thu nhỏ (mặc định 1280)
 *   agent.screen.quality    / AGENT_SCREEN_QUALITY    : chất lượng JPEG 1-100 (mặc định 60)
 *   agent.screen.backend    / AGENT_SCREEN_BACKEND    : auto | portal | x11 | test (mặc định auto)
 *   agent.python            / AGENT_PYTHON            : đường dẫn python3 hệ thống (mặc định /usr/bin/python3)
 *   agent.data-dir          / AGENT_DATA_DIR          : thư mục dữ liệu Agent (mặc định ~/.pbl4-agent)
 */
public final class ScreenCaptureSettings {

    public static final String SCRIPT_FILE_NAME = "pbl4_screen_capture.py";
    public static final String RESTORE_TOKEN_FILE_NAME = "screencast-restore-token";

    private final int fps;
    private final int maxWidth;
    private final int quality;
    private final String backend;
    private final String pythonPath;
    private final Path dataDir;

    public ScreenCaptureSettings(
            int fps,
            int maxWidth,
            int quality,
            String backend,
            String pythonPath,
            Path dataDir) {

        if (fps < 1 || fps > 60) {
            throw new IllegalArgumentException(
                    "FPS phải nằm trong khoảng 1-60.");
        }

        if (maxWidth < 0) {
            throw new IllegalArgumentException(
                    "Chiều rộng tối đa không được âm.");
        }

        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException(
                    "Chất lượng JPEG phải nằm trong khoảng 1-100.");
        }

        if (!"auto".equals(backend)
                && !"portal".equals(backend)
                && !"x11".equals(backend)
                && !"test".equals(backend)) {

            throw new IllegalArgumentException(
                    "Backend không hợp lệ: " + backend
                            + " (chỉ hỗ trợ auto, portal, x11, test).");
        }

        this.fps = fps;
        this.maxWidth = maxWidth;
        this.quality = quality;
        this.backend = backend;
        this.pythonPath = pythonPath;
        this.dataDir = dataDir;
    }

    /**
     * Đọc cấu hình từ system property / biến môi trường.
     */
    public static ScreenCaptureSettings load() {

        int fps = readInt("agent.screen.fps", "AGENT_SCREEN_FPS", 8);

        int maxWidth = readInt(
                "agent.screen.max-width",
                "AGENT_SCREEN_MAX_WIDTH",
                1280);

        int quality = readInt(
                "agent.screen.quality",
                "AGENT_SCREEN_QUALITY",
                60);

        String backend = readString(
                "agent.screen.backend",
                "AGENT_SCREEN_BACKEND",
                "auto").toLowerCase();

        String python = readString(
                "agent.python",
                "AGENT_PYTHON",
                "/usr/bin/python3");

        String dataDirValue = readString(
                "agent.data-dir",
                "AGENT_DATA_DIR",
                "");

        Path dataDir = dataDirValue.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".pbl4-agent")
                : Paths.get(dataDirValue).toAbsolutePath().normalize();

        return new ScreenCaptureSettings(
                fps,
                maxWidth,
                quality,
                backend,
                python,
                dataDir);
    }

    private static String readString(
            String property,
            String env,
            String defaultValue) {

        String value = System.getProperty(property);

        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }

        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static int readInt(
            String property,
            String env,
            int defaultValue) {

        String value = readString(property, env, "");

        if (value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    property + " phải là số nguyên: " + value, e);
        }
    }

    public int getFps() {
        return fps;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getQuality() {
        return quality;
    }

    public String getBackend() {
        return backend;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getScriptPath() {
        return dataDir.resolve(SCRIPT_FILE_NAME);
    }

    public Path getRestoreTokenPath() {
        return dataDir.resolve(RESTORE_TOKEN_FILE_NAME);
    }
}