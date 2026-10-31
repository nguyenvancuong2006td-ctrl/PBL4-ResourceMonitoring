package pbl4.agent.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lưu và đọc trạng thái đăng ký (kèm Credential) trên máy Client.
 *
 * - Một file JSON duy nhất: &lt;dataDir&gt;/registration.json
 * - Ghi nguyên tử (ghi file tạm rồi đổi tên) nên mất điện giữa chừng
 *   không làm hỏng file đang dùng.
 * - Trên Linux/macOS: thư mục 700, file 600 (chỉ chủ sở hữu đọc được).
 *
 * Lưu ý: Credential được lưu dạng văn bản thuần, an toàn ở mức
 * quyền truy cập hệ điều hành. Nếu cần bảo vệ cao hơn, có thể mã hóa
 * nội dung ở đây mà không ảnh hưởng các class khác.
 */
public class CredentialStorage {

    private static final String FILE_NAME = "registration.json";
    private static final int SCHEMA_VERSION = 1;

    private final Path dataDir;
    private final Path stateFile;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Serialize các thao tác đọc/ghi/xóa từ nhiều luồng
    private final Object lock = new Object();

    public CredentialStorage(Path dataDir) {

        if (dataDir == null) {
            throw new IllegalArgumentException(
                    "Thư mục dữ liệu không được null."
            );
        }

        this.dataDir = dataDir;
        this.stateFile = dataDir.resolve(FILE_NAME);
    }

    public Path getStateFile() {
        return stateFile;
    }

    // ==========================================
    // ĐỌC
    // ==========================================

    /**
     * Đọc trạng thái đã lưu.
     *
     * @return rỗng nếu chưa có file hoặc file bị hỏng
     *         (file hỏng được đổi tên thành *.corrupt-... để điều tra).
     */
    public Optional<RegistrationInfo> load() {

        synchronized (lock) {

            if (!Files.isRegularFile(stateFile)) {
                return Optional.empty();
            }

            try {
                byte[] content = Files.readAllBytes(stateFile);

                JsonNode root = objectMapper.readTree(content);

                if (root == null || !root.isObject()) {
                    throw new IOException(
                            "Nội dung file không phải JSON object."
                    );
                }

                return Optional.of(fromJson(root));

            } catch (Exception e) {

                System.err.println(
                        "[STORAGE] File trạng thái bị hỏng, bỏ qua: "
                                + safeMessage(e)
                );

                quarantineCorruptFile();

                return Optional.empty();
            }
        }
    }

    // ==========================================
    // GHI
    // ==========================================

    /**
     * Ghi trạng thái xuống đĩa. Phương thức chỉ trả về khi dữ liệu
     * đã nằm an toàn trong file.
     *
     * Quy tắc bắt buộc của luồng đăng ký: phải ghi thành công Credential
     * TRƯỚC khi gửi ACK cho Server.
     */
    public void save(RegistrationInfo info) throws IOException {

        if (info == null) {
            throw new IllegalArgumentException(
                    "RegistrationInfo không được null."
            );
        }

        synchronized (lock) {

            ensureDataDir();

            byte[] content = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(toJson(info));

            Path tempFile = Files.createTempFile(
                    dataDir,
                    "registration-",
                    ".tmp"
            );

            try {
                restrictPermissions(tempFile, false);

                Files.write(tempFile, content);

                moveIntoPlace(tempFile);

                restrictPermissions(stateFile, false);

            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    // ==========================================
    // XÓA
    // ==========================================

    /**
     * Xóa trạng thái đăng ký cục bộ (dùng khi cần đăng ký lại từ đầu).
     */
    public void clear() {

        synchronized (lock) {
            try {
                Files.deleteIfExists(stateFile);

            } catch (IOException e) {
                System.err.println(
                        "[STORAGE] Không thể xóa file trạng thái: "
                                + safeMessage(e)
                );
            }
        }
    }

    // ==========================================
    // JSON <-> RegistrationInfo
    // ==========================================

    private Map<String, Object> toJson(RegistrationInfo info) {

        Map<String, Object> map = new LinkedHashMap<>();

        map.put("schemaVersion", SCHEMA_VERSION);
        map.put("serverUrl", info.getServerUrl());
        map.put("agentId", info.getAgentId());
        map.put("clientId", info.getClientId());
        map.put("registrationToken", info.getRegistrationToken());
        map.put(
                "state",
                info.getState() == null
                        ? RegistrationState.NOT_REGISTERED.name()
                        : info.getState().name()
        );
        map.put("registeredAt", format(info.getRegisteredAt()));
        map.put("lastUpdatedAt", format(info.getLastUpdatedAt()));
        map.put("credential", info.getCredential());
        map.put(
                "credentialExpiresAt",
                format(info.getCredentialExpiresAt())
        );

        return map;
    }

    private RegistrationInfo fromJson(JsonNode root) {

        RegistrationInfo info = new RegistrationInfo();

        info.setServerUrl(text(root, "serverUrl"));
        info.setAgentId(text(root, "agentId"));
        info.setClientId(text(root, "clientId"));
        info.setRegistrationToken(text(root, "registrationToken"));
        info.setCredential(text(root, "credential"));
        info.setCredentialExpiresAt(
                parseTime(text(root, "credentialExpiresAt"))
        );

        info.setState(parseState(text(root, "state")));

        // setState(REGISTERED) đặt registeredAt = now,
        // nên phải khôi phục lại giá trị gốc sau cùng.
        info.setRegisteredAt(parseTime(text(root, "registeredAt")));
        info.setLastUpdatedAt(parseTime(text(root, "lastUpdatedAt")));

        return info;
    }

    private String text(JsonNode root, String field) {

        JsonNode node = root.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        String value = node.asText();

        return value == null || value.isBlank() ? null : value;
    }

    private RegistrationState parseState(String value) {

        if (value == null) {
            return RegistrationState.NOT_REGISTERED;
        }

        try {
            return RegistrationState.valueOf(value);

        } catch (IllegalArgumentException e) {
            return RegistrationState.NOT_REGISTERED;
        }
    }

    private LocalDateTime parseTime(String value) {

        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);

        } catch (Exception e) {
            return null;
        }
    }

    private String format(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    // ==========================================
    // FILE HELPERS
    // ==========================================

    private void ensureDataDir() throws IOException {

        boolean existed = Files.isDirectory(dataDir);

        Files.createDirectories(dataDir);

        if (!existed) {
            restrictPermissions(dataDir, true);
        }
    }

    private void moveIntoPlace(Path tempFile) throws IOException {

        try {
            Files.move(
                    tempFile,
                    stateFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (AtomicMoveNotSupportedException e) {

            Files.move(
                    tempFile,
                    stateFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    /**
     * Chỉ chủ sở hữu được đọc/ghi.
     * Thất bại chỉ cảnh báo, không làm hỏng luồng đăng ký.
     */
    private void restrictPermissions(Path path, boolean directory) {

        try {
            if (FileSystems.getDefault()
                    .supportedFileAttributeViews()
                    .contains("posix")) {

                Files.setPosixFilePermissions(
                        path,
                        PosixFilePermissions.fromString(
                                directory ? "rwx------" : "rw-------"
                        )
                );

            } else {
                // Windows: cách làm tốt nhất có thể bằng API cũ
                File file = path.toFile();

                file.setReadable(false, false);
                file.setReadable(true, true);
                file.setWritable(false, false);
                file.setWritable(true, true);

                if (directory) {
                    file.setExecutable(false, false);
                    file.setExecutable(true, true);
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[STORAGE] Không thể giới hạn quyền truy cập "
                            + path
                            + ": "
                            + safeMessage(e)
            );
        }
    }

    private void quarantineCorruptFile() {

        Path quarantined = stateFile.resolveSibling(
                FILE_NAME + ".corrupt-" + System.currentTimeMillis()
        );

        try {
            Files.move(
                    stateFile,
                    quarantined,
                    StandardCopyOption.REPLACE_EXISTING
            );

            System.err.println(
                    "[STORAGE] Đã chuyển file hỏng sang: "
                            + quarantined
            );

        } catch (IOException e) {
            System.err.println(
                    "[STORAGE] Không thể chuyển file hỏng: "
                            + safeMessage(e)
            );
        }
    }

    private String safeMessage(Exception e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        // Thông báo của Jackson có nhiều dòng, chỉ lấy dòng đầu
        int newLine = message.indexOf('\n');

        return newLine < 0 ? message : message.substring(0, newLine);
    }
}