package pbl4.agent.client;

import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.DiskData;
import pbl4.agent.model.MonitoringData;
import pbl4.agent.model.RemoteCommandData;
import pbl4.agent.model.NetworkData;
import pbl4.agent.model.ProcessData;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class ServerClient {

    private static final String AGENT_CREDENTIAL_HEADER =
            "X-Agent-Credential";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(10);

    private static final Duration MONITORING_TIMEOUT =
            Duration.ofSeconds(15);

    private final AgentConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ServerClient(AgentConfig config) {

        if (config == null) {
            throw new IllegalArgumentException(
                    "AgentConfig không được null."
            );
        }

        if (config.getServerUrl() == null
                || config.getServerUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "Server URL không được để trống."
            );
        }

        if (config.getAgentId() == null
                || config.getAgentId().isBlank()) {
            throw new IllegalArgumentException(
                    "Agent ID không được để trống."
            );
        }

        if (config.getCredential() == null
                || config.getCredential().isBlank()) {
            throw new IllegalArgumentException(
                    "Agent Credential không được để trống."
            );
        }

        this.config = config;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();

        this.objectMapper = new ObjectMapper();

        this.objectMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false
        );
    }

    // ==========================================
    // BUILD API URL
    // ==========================================

    private String buildUrl(String path) {

        String baseUrl = config.getServerUrl().trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        if (path == null || path.isBlank()) {
            return baseUrl;
        }

        String normalizedPath = path.startsWith("/")
                ? path
                : "/" + path;

        return baseUrl + normalizedPath;
    }

    // ==========================================
    // CHECK HTTP SUCCESS
    // ==========================================

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    // ==========================================
    // CREATE COMMON REQUEST BUILDER
    // ==========================================

    private HttpRequest.Builder createRequest(
            String url,
            Duration timeout) {

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header(
                        AGENT_CREDENTIAL_HEADER,
                        config.getCredential()
                )
                .header("Accept", "application/json");
    }

    // ==========================================
    // SEND HEARTBEAT
    // ==========================================

    public boolean sendHeartbeat() {

        String url = buildUrl(
                "/api/client-agents/"
                        + config.getAgentId()
                        + "/heartbeat"
        );

        try {
            HttpRequest request = createRequest(
                    url,
                    REQUEST_TIMEOUT
            )
                    .POST(
                            HttpRequest.BodyPublishers.noBody()
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (!isSuccessful(response.statusCode())) {
                System.err.println(
                        "[AGENT] Heartbeat thất bại. HTTP "
                                + response.statusCode()
                );

                return false;
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            System.err.println(
                    "[AGENT] Heartbeat bị gián đoạn."
            );

            return false;

        } catch (IOException e) {
            System.err.println(
                    "[AGENT] Lỗi I/O khi gửi heartbeat: "
                            + safeMessage(e)
            );

            return false;

        } catch (Exception e) {
            System.err.println(
                    "[AGENT] Không thể gửi heartbeat: "
                            + safeMessage(e)
            );

            return false;
        }
    }

    // ==========================================
    // SEND MONITORING DATA
    // ==========================================

    public boolean sendMonitoringData(
            MonitoringData monitoringData) {

        if (monitoringData == null) {
            System.err.println(
                    "[AGENT] MonitoringData không được null."
            );

            return false;
        }

        String url = buildUrl(
                "/api/monitoring/"
                        + config.getAgentId()
        );

        try {
            String json = buildMonitoringJson(
                    monitoringData
            );

            HttpRequest request = createRequest(
                    url,
                    MONITORING_TIMEOUT
            )
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    json,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (!isSuccessful(response.statusCode())) {
                System.err.println(
                        "[AGENT] Gửi Monitoring Data thất bại. HTTP "
                                + response.statusCode()
                                + describeBody(response.body())
                );

                return false;
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            System.err.println(
                    "[AGENT] Gửi Monitoring Data bị gián đoạn."
            );

            return false;

        } catch (IOException e) {
            System.err.println(
                    "[AGENT] Lỗi I/O khi gửi Monitoring Data: "
                            + safeMessage(e)
            );

            return false;

        } catch (Exception e) {
            System.err.println(
                    "[AGENT] Không thể gửi Monitoring Data: "
                            + safeMessage(e)
            );

            return false;
        }
    }

    // ==========================================
    // GET PENDING REMOTE COMMANDS
    // ==========================================

    /**
     * Lấy danh sách lệnh đang chờ từ Server.
     *
     * IOException được chuyển thành UncheckedIOException.
     * InterruptedException được giữ nguyên trạng thái
     * interrupt và chuyển thành CancellationException.
     *
     * Khi xảy ra lỗi, không trả danh sách rỗng giả tạo.
     */
    public List<RemoteCommandData> getPendingCommands() {

        String url = buildUrl(
                "/api/remote/commands/agent/"
                        + config.getAgentId()
                        + "/pending"
        );

        try {
            HttpRequest request = createRequest(
                    url,
                    REQUEST_TIMEOUT
            )
                    .POST(
                            HttpRequest.BodyPublishers.noBody()
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (!isSuccessful(response.statusCode())) {
                throw new IOException(
                        "Lấy lệnh thất bại. HTTP "
                                + response.statusCode()
                );
            }

            String responseBody = response.body();

            if (responseBody == null
                    || responseBody.isBlank()) {
                return new ArrayList<>();
            }

            List<RemoteCommandData> commands;

            try {
                commands = objectMapper.readValue(
                        responseBody,
                        new TypeReference<List<RemoteCommandData>>() {}
                );

            } catch (Exception e) {
                throw new IOException(
                        "Không thể phân tích danh sách lệnh từ Server.",
                        e
                );
            }

            if (commands == null) {
                throw new IOException(
                        "Server trả về danh sách lệnh không hợp lệ."
                );
            }

            System.out.println(
                    "[REMOTE] Đã nhận "
                            + commands.size()
                            + " lệnh từ Server."
            );

            return commands;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new CancellationException(
                    "Yêu cầu lấy lệnh từ Server bị gián đoạn."
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Không thể lấy lệnh từ Server.",
                    e
            );
        }
    }

    // ==========================================
    // MARK COMMAND AS RUNNING
    // ==========================================

    public boolean markCommandRunning(Long commandId) {

        if (!isValidCommandId(commandId)) {
            System.err.println(
                    "[REMOTE] Command ID không hợp lệ."
            );

            return false;
        }

        String url = buildUrl(
                "/api/remote/commands/agent/"
                        + config.getAgentId()
                        + "/"
                        + commandId
                        + "/running"
        );

        return sendAgentPost(
                url,
                null,
                "Chuyển lệnh sang RUNNING"
        );
    }

    // ==========================================
    // REPORT COMMAND RESULT
    // ==========================================

    public boolean reportCommandResult(
            Long commandId,
            boolean success,
            String result,
            String errorMessage) {

        if (!isValidCommandId(commandId)) {
            System.err.println(
                    "[REMOTE] Command ID không hợp lệ."
            );

            return false;
        }

        String url = buildUrl(
                "/api/remote/commands/agent/"
                        + config.getAgentId()
                        + "/"
                        + commandId
                        + "/result"
        );

        try {
            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put("success", success);
            body.put(
                    "result",
                    result == null ? "" : result
            );
            body.put(
                    "errorMessage",
                    errorMessage == null ? "" : errorMessage
            );

            String json = objectMapper.writeValueAsString(
                    body
            );

            return sendAgentPost(
                    url,
                    json,
                    "Gửi kết quả lệnh"
            );

        } catch (Exception e) {
            System.err.println(
                    "[REMOTE] Không thể tạo JSON kết quả lệnh: "
                            + safeMessage(e)
            );

            return false;
        }
    }

    // ==========================================
    // VALIDATE COMMAND ID
    // ==========================================

    private boolean isValidCommandId(Long commandId) {
        return commandId != null && commandId > 0;
    }

    // ==========================================
    // COMMON AGENT POST REQUEST
    // ==========================================

    private boolean sendAgentPost(
            String url,
            String jsonBody,
            String operation) {

        try {
            HttpRequest.Builder builder = createRequest(
                    url,
                    REQUEST_TIMEOUT
            );

            if (jsonBody == null) {
                builder.POST(
                        HttpRequest.BodyPublishers.noBody()
                );

            } else {
                builder.header(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                builder.POST(
                        HttpRequest.BodyPublishers.ofString(
                                jsonBody,
                                StandardCharsets.UTF_8
                        )
                );
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (!isSuccessful(response.statusCode())) {
                System.err.println(
                        "[REMOTE] "
                                + operation
                                + " thất bại. HTTP "
                                + response.statusCode()
                );

                return false;
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            System.err.println(
                    "[REMOTE] "
                            + operation
                            + " bị gián đoạn."
            );

            return false;

        } catch (IOException e) {
            System.err.println(
                    "[REMOTE] Lỗi I/O khi "
                            + operation
                            + ": "
                            + safeMessage(e)
            );

            return false;

        } catch (Exception e) {
            System.err.println(
                    "[REMOTE] "
                            + operation
                            + " thất bại: "
                            + safeMessage(e)
            );

            return false;
        }
    }

    // ==========================================
    // BUILD MONITORING JSON
    // ==========================================

    private String buildMonitoringJson(
            MonitoringData data) {

        try {
            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put("agentId", data.getAgentId());

            payload.put(
                    "collectedAt",
                    data.getCollectedAt() == null
                            ? null
                            : data.getCollectedAt().toString()
            );

            payload.put("cpuUsage", data.getCpuUsage());
            payload.put(
                    "loadAverage1m",
                    data.getLoadAverage1m()
            );

            payload.put("memoryUsage", data.getMemoryUsage());
            payload.put("totalMemory", data.getTotalMemory());
            payload.put("usedMemory", data.getUsedMemory());

            payload.put(
                    "availableMemory",
                    data.getAvailableMemory()
            );

            payload.put("diskUsage", data.getDiskUsage());
            payload.put("totalDisk", data.getTotalDisk());
            payload.put("usedDisk", data.getUsedDisk());

            payload.put(
                    "availableDisk",
                    data.getAvailableDisk()
            );

            payload.put(
                    "diskData",
                    buildDiskPayload(
                            data.getDiskDataList()
                    )
            );

            payload.put(
                    "networkData",
                    buildNetworkPayload(
                            data.getNetworkDataList()
                    )
            );

            payload.put(
                    "processData",
                    buildProcessPayload(
                            data.getProcessDataList()
                    )
            );

            return objectMapper.writeValueAsString(payload);

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Không thể tạo JSON Monitoring Data.",
                    e
            );
        }
    }

    // ==========================================
    // DISK JSON
    // ==========================================

    // Giới hạn cột trong database (disk_info)
    private static final int MAX_DEVICE_NAME_LENGTH = 50;
    private static final int MAX_MOUNT_POINT_LENGTH = 100;

    /**
     * Server bắt buộc mỗi ổ đĩa phải có tên thiết bị và mount point,
     * dung lượng hợp lệ, không trùng nhau. Dữ liệu từ OSHI đôi khi
     * không đáp ứng, nên làm sạch tại đây để một ổ đĩa lạ không
     * làm hỏng cả lần gửi.
     */
    private List<Map<String, Object>> buildDiskPayload(
            List<DiskData> disks) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        if (disks == null) {
            return result;
        }

        java.util.Set<String> seen = new java.util.HashSet<>();

        for (DiskData disk : disks) {

            if (disk == null || disk.getTotalSpace() <= 0) {
                continue;
            }

            String mountPoint = disk.getMountPoint() == null
                    ? ""
                    : disk.getMountPoint().trim();

            if (mountPoint.isEmpty()) {
                continue;
            }

            String name = disk.getName() == null
                    ? ""
                    : disk.getName().trim();

            // Không có tên thiết bị thì dùng mount point thay thế
            if (name.isEmpty()) {
                name = mountPoint;
            }

            if (name.length() > MAX_DEVICE_NAME_LENGTH) {
                name = name.substring(0, MAX_DEVICE_NAME_LENGTH);
            }

            if (mountPoint.length() > MAX_MOUNT_POINT_LENGTH) {
                mountPoint = mountPoint.substring(
                        0,
                        MAX_MOUNT_POINT_LENGTH
                );
            }

            // Server từ chối nếu trùng (tên thiết bị + mount point)
            if (!seen.add(name + "\0" + mountPoint)) {
                continue;
            }

            long total = disk.getTotalSpace();

            long used = Math.min(
                    Math.max(0L, disk.getUsedSpace()),
                    total
            );

            long usable = Math.min(
                    Math.max(0L, disk.getUsableSpace()),
                    total
            );

            double percent = disk.getUsagePercent();

            if (!Double.isFinite(percent)) {
                percent = 0.0;
            }

            percent = Math.min(100.0, Math.max(0.0, percent));

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put("name", name);
            item.put("mountPoint", mountPoint);
            item.put("totalSpace", total);
            item.put("usedSpace", used);
            item.put("usableSpace", usable);
            item.put("usagePercent", percent);

            result.add(item);
        }

        return result;
    }

    // ==========================================
    // NETWORK JSON
    // ==========================================

    private List<Map<String, Object>> buildNetworkPayload(
            List<NetworkData> networks) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        if (networks == null) {
            return result;
        }

        for (NetworkData network : networks) {

            if (network == null) {
                continue;
            }

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "interfaceName",
                    network.getInterfaceName()
            );

            item.put(
                    "displayName",
                    network.getDisplayName()
            );

            item.put(
                    "bytesReceived",
                    network.getBytesReceived()
            );

            item.put(
                    "bytesSent",
                    network.getBytesSent()
            );

            result.add(item);
        }

        return result;
    }

    // ==========================================
    // PROCESS JSON
    // ==========================================

    private List<Map<String, Object>> buildProcessPayload(
            List<ProcessData> processes) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        if (processes == null) {
            return result;
        }

        for (ProcessData process : processes) {

            if (process == null) {
                continue;
            }

            Map<String, Object> item =
                    new LinkedHashMap<>();

            item.put(
                    "processId",
                    process.getProcessId()
            );

            item.put(
                    "processName",
                    process.getProcessName()
            );

            item.put(
                    "cpuUsage",
                    process.getCpuUsage()
            );

            item.put(
                    "memoryUsage",
                    process.getMemoryUsage()
            );

            result.add(item);
        }

        return result;
    }

    // ==========================================
    // LÝ DO LỖI TỪ SERVER
    // ==========================================

    private String describeBody(String body) {

        if (body == null || body.isBlank()) {
            return "";
        }

        String text = body.trim();

        if (text.length() > 200) {
            text = text.substring(0, 200);
        }

        return " - " + text;
    }

    // ==========================================
    // SAFE ERROR MESSAGE
    // ==========================================

    private String safeMessage(Exception e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }
}