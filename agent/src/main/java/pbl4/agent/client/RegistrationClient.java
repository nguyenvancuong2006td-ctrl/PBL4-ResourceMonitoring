package pbl4.agent.client;

import pbl4.agent.model.AgentRegistrationData;
import pbl4.agent.model.CredentialPollResponse;
import pbl4.agent.model.RegistrationResponse;
import pbl4.agent.registration.RegistrationException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gọi các API đăng ký Agent trên Server.
 *
 * Các API này KHÔNG dùng Agent Credential (Agent chưa có Credential),
 * mà xác thực bằng Registration Token do chính Agent sinh ra.
 *
 *   POST /api/agent-registration
 *   POST /api/agent-registration/{agentId}/credential/poll
 *   POST /api/agent-registration/{agentId}/credential/ack
 *
 * Ngoài ra có verifyCredential() để kiểm tra Credential đã lưu
 * còn được Server chấp nhận hay không.
 */
public class RegistrationClient {

    /**
     * Kết quả kiểm tra Credential đã lưu với Server.
     */
    public enum CredentialCheck {

        // Server chấp nhận Credential
        VALID,

        // Server từ chối (bị thu hồi, vô hiệu hóa, hết hạn...)
        INVALID,

        // Không kết nối được hoặc Server lỗi, chưa thể kết luận
        UNREACHABLE
    }

    private static final String AGENT_CREDENTIAL_HEADER =
            "X-Agent-Credential";

    private static final Duration CONNECT_TIMEOUT =
            Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(10);

    private static final int MAX_ERROR_BODY_LENGTH = 200;

    private final String serverUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RegistrationClient(String serverUrl) {

        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Server URL không được để trống."
            );
        }

        String baseUrl = serverUrl.trim();

        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        this.serverUrl = baseUrl;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    // ==========================================
    // 1. GỬI YÊU CẦU ĐĂNG KÝ
    // ==========================================

    public RegistrationResponse register(
            AgentRegistrationData data) throws RegistrationException {

        if (data == null) {
            throw new IllegalArgumentException(
                    "AgentRegistrationData không được null."
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("hostname", data.getHostname());
        body.put("ipAddress", data.getIpAddress());
        body.put("osName", data.getOsName());
        body.put("osVersion", data.getOsVersion());
        body.put("kernelVersion", data.getKernelVersion());
        body.put("bootTime", formatTime(data.getBootTime()));
        body.put("agentVersion", data.getAgentVersion());
        body.put("registrationToken", data.getRegistrationToken());

        putIfPresent(body, "cpuModel", data.getCpuModel());
        putIfPresent(body, "cpuManufacturer", data.getCpuManufacturer());
        putIfPresent(body, "cpuCoreCount", data.getCpuCoreCount());
        putIfPresent(body, "cpuThreadCount", data.getCpuThreadCount());
        putIfPresent(body, "cpuFrequencyMhz", data.getCpuFrequencyMhz());
        putIfPresent(body, "memoryTotalBytes", data.getMemoryTotalBytes());

        JsonNode json = postJson(
                "/api/agent-registration",
                body,
                "Gửi yêu cầu đăng ký"
        );

        return new RegistrationResponse(
                text(json, "agentId"),
                text(json, "clientId"),
                text(json, "status"),
                text(json, "message")
        );
    }

    // ==========================================
    // 2. POLL TRẠNG THÁI / CREDENTIAL
    // ==========================================

    public CredentialPollResponse pollCredential(
            String agentId,
            String registrationToken) throws RegistrationException {

        JsonNode json = postJson(
                "/api/agent-registration/"
                        + encodePath(agentId)
                        + "/credential/poll",
                tokenBody(registrationToken),
                "Poll Credential"
        );

        return new CredentialPollResponse(
                text(json, "agentId"),
                text(json, "status"),
                text(json, "credential"),
                parseTime(json.get("expiresAt")),
                text(json, "message")
        );
    }

    // ==========================================
    // 3. ACK ĐÃ NHẬN CREDENTIAL
    // ==========================================

    /**
     * Báo cho Server biết Agent đã lưu Credential thành công.
     * Chỉ gọi SAU KHI Credential đã được ghi xuống đĩa.
     *
     * ACK có tính idempotent ở phía Server, nên gọi lại là an toàn.
     */
    public void acknowledgeCredential(
            String agentId,
            String registrationToken) throws RegistrationException {

        postJson(
                "/api/agent-registration/"
                        + encodePath(agentId)
                        + "/credential/ack",
                tokenBody(registrationToken),
                "Gửi ACK Credential"
        );
    }

    // ==========================================
    // 4. KIỂM TRA CREDENTIAL ĐÃ LƯU
    // ==========================================

    /**
     * Dùng chính API heartbeat (đã có sẵn) để kiểm tra Credential.
     * Không ném exception: mọi lỗi mạng được quy về UNREACHABLE.
     */
    public CredentialCheck verifyCredential(
            String agentId,
            String credential) {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            serverUrl
                                    + "/api/client-agents/"
                                    + encodePath(agentId)
                                    + "/heartbeat"
                    ))
                    .timeout(REQUEST_TIMEOUT)
                    .header(AGENT_CREDENTIAL_HEADER, credential)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                return CredentialCheck.VALID;
            }

            if (status == 401 || status == 403) {
                return CredentialCheck.INVALID;
            }

            return CredentialCheck.UNREACHABLE;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            return CredentialCheck.UNREACHABLE;

        } catch (Exception e) {
            return CredentialCheck.UNREACHABLE;
        }
    }

    // ==========================================
    // HTTP CHUNG
    // ==========================================

    private Map<String, Object> tokenBody(String registrationToken) {

        Map<String, Object> body = new LinkedHashMap<>();

        body.put("registrationToken", registrationToken);

        return body;
    }

    /**
     * Gửi POST JSON, trả về JSON phản hồi (2xx).
     * Mọi lỗi đều được quy về RegistrationException có phân loại
     * retryable / không retryable.
     */
    private JsonNode postJson(
            String path,
            Map<String, Object> body,
            String operation) throws RegistrationException {

        String requestJson;

        try {
            requestJson = objectMapper.writeValueAsString(body);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Không thể tạo JSON cho: " + operation,
                    e
            );
        }

        HttpResponse<String> response;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .header("Accept", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    requestJson,
                                    StandardCharsets.UTF_8
                            )
                    )
                    .build();

            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw RegistrationException.transientError(
                    operation + " bị gián đoạn.",
                    e
            );

        } catch (IOException e) {
            throw RegistrationException.transientError(
                    operation + ": không kết nối được Server ("
                            + safeMessage(e) + ")",
                    e
            );

        } catch (IllegalArgumentException e) {
            // URI không hợp lệ: lỗi cấu hình, thử lại cũng vô ích
            throw new RegistrationException(
                    operation + ": địa chỉ Server không hợp lệ ("
                            + safeMessage(e) + ")",
                    0,
                    false,
                    e
            );
        }

        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw RegistrationException.fromHttp(
                    status,
                    operation + " thất bại. HTTP " + status
                            + extractServerMessage(response.body())
            );
        }

        return parseBody(response.body(), operation);
    }

    private JsonNode parseBody(
            String responseBody,
            String operation) throws RegistrationException {

        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode json = objectMapper.readTree(responseBody);

            if (json == null || !json.isObject()) {
                throw new IOException("Phản hồi không phải JSON object.");
            }

            return json;

        } catch (IOException e) {
            throw RegistrationException.transientError(
                    operation + ": phản hồi Server không hợp lệ ("
                            + safeMessage(e) + ")",
                    e
            );
        }
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    /**
     * Lấy thông điệp lỗi từ body {"message": "..."} nếu có.
     */
    private String extractServerMessage(String responseBody) {

        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }

        String message = null;

        try {
            JsonNode json = objectMapper.readTree(responseBody);

            if (json != null && json.hasNonNull("message")) {
                message = json.get("message").asText();
            }

        } catch (IOException ignored) {
            // Không phải JSON, bỏ qua
        }

        if (message == null || message.isBlank()) {
            return "";
        }

        if (message.length() > MAX_ERROR_BODY_LENGTH) {
            message = message.substring(0, MAX_ERROR_BODY_LENGTH);
        }

        return " - " + message;
    }

    private void putIfPresent(
            Map<String, Object> body, String key, Object value) {

        if (value != null) {
            body.put(key, value);
        }
    }

    private String text(JsonNode json, String field) {

        JsonNode node = json.get(field);

        if (node == null || node.isNull()) {
            return null;
        }

        return node.asText();
    }

    /**
     * Server trả LocalDateTime dạng chuỗi ISO (hoặc mảng số
     * nếu cấu hình Jackson khác đi); xử lý cả hai.
     */
    private LocalDateTime parseTime(JsonNode node) {

        if (node == null || node.isNull()) {
            return null;
        }

        try {
            if (node.isArray() && node.size() >= 5) {
                return LocalDateTime.of(
                        node.get(0).asInt(),
                        node.get(1).asInt(),
                        node.get(2).asInt(),
                        node.get(3).asInt(),
                        node.get(4).asInt(),
                        node.size() > 5 ? node.get(5).asInt() : 0,
                        node.size() > 6 ? node.get(6).asInt() : 0
                );
            }

            String value = node.asText();

            if (value == null || value.isBlank()) {
                return null;
            }

            return LocalDateTime.parse(value);

        } catch (Exception e) {
            return null;
        }
    }

    private String formatTime(LocalDateTime value) {

        return value == null
                ? null
                : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    private String encodePath(String segment) {

        return URLEncoder
                .encode(segment == null ? "" : segment,
                        StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String safeMessage(Exception e) {

        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }

        return message;
    }
}