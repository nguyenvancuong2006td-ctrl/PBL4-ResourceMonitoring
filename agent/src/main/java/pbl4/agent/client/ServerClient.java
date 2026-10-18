package pbl4.agent.client;

import pbl4.agent.config.AgentConfig;
import pbl4.agent.model.MonitoringData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ServerClient {

    private final AgentConfig config;
    private final HttpClient httpClient;

    public ServerClient(AgentConfig config) {

        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ==============================
    // SEND HEARTBEAT
    // ==============================

    public boolean sendHeartbeat() {

        String url =
                config.getServerUrl()
                        + "/api/client-agents/"
                        + config.getAgentId()
                        + "/heartbeat";

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "X-Agent-Credential",
                                    config.getCredential()
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.noBody()
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Heartbeat response: "
                            + response.statusCode()
            );

            System.out.println(
                    "Server response: "
                            + response.body()
            );

            return response.statusCode() == 200;

        } catch (Exception e) {

            System.out.println(
                    "Không thể kết nối Server: "
                            + e.getMessage()
            );

            return false;
        }
    }

    // ==============================
    // SEND MONITORING DATA
    // ==============================

    public boolean sendMonitoringData(
            MonitoringData monitoringData) {

        String url =
                config.getServerUrl()
                        + "/api/monitoring/"
                        + config.getAgentId();

        // ==============================
        // NETWORK JSON
        // ==============================

        StringBuilder networkJson =
                new StringBuilder("[");

        if (monitoringData.getNetworkDataList() != null) {

            for (int i = 0;
                 i < monitoringData.getNetworkDataList().size();
                 i++) {

                var network =
                        monitoringData
                                .getNetworkDataList()
                                .get(i);

                networkJson.append("{");

                networkJson.append(
                        "\"interfaceName\":\""
                );

                networkJson.append(
                        network.getInterfaceName()
                );

                networkJson.append("\",");

                networkJson.append(
                        "\"displayName\":\""
                );

                networkJson.append(
                        network.getDisplayName()
                );

                networkJson.append("\",");

                networkJson.append(
                        "\"bytesReceived\":"
                );

                networkJson.append(
                        network.getBytesReceived()
                );

                networkJson.append(",");

                networkJson.append(
                        "\"bytesSent\":"
                );

                networkJson.append(
                        network.getBytesSent()
                );

                networkJson.append("}");

                if (i <
                        monitoringData
                                .getNetworkDataList()
                                .size() - 1) {

                    networkJson.append(",");
                }
            }
        }

        networkJson.append("]");

        // ==============================
        // PROCESS JSON
        // ==============================

        StringBuilder processJson =
                new StringBuilder("[");

        if (monitoringData.getProcessDataList() != null) {

            for (int i = 0;
                 i < monitoringData
                         .getProcessDataList()
                         .size();
                 i++) {

                var process =
                        monitoringData
                                .getProcessDataList()
                                .get(i);

                processJson.append("{");

                processJson.append(
                        "\"processId\":"
                );

                processJson.append(
                        process.getProcessId()
                );

                processJson.append(",");

                processJson.append(
                        "\"processName\":\""
                );

                processJson.append(
                        process.getProcessName()
                );

                processJson.append("\",");

                processJson.append(
                        "\"cpuUsage\":"
                );

                processJson.append(
                        process.getCpuUsage()
                );

                processJson.append(",");

                processJson.append(
                        "\"memoryUsage\":"
                );

                processJson.append(
                        process.getMemoryUsage()
                );

                processJson.append("}");

                if (i <
                        monitoringData
                                .getProcessDataList()
                                .size() - 1) {

                    processJson.append(",");
                }
            }
        }

        processJson.append("]");

        // ==============================
        // CREATE JSON
        // ==============================

        String json =
                "{"

                        // ==============================
                        // AGENT
                        // ==============================

                        + "\"agentId\":\""
                        + monitoringData.getAgentId()
                        + "\","

                        // ==============================
                        // COLLECTED AT
                        // ==============================

                        + "\"collectedAt\":\""
                        + monitoringData.getCollectedAt()
                        + "\","

                        // ==============================
                        // CPU
                        // ==============================

                        + "\"cpuUsage\":"
                        + monitoringData.getCpuUsage()
                        + ","

                        // ==============================
                        // MEMORY
                        // ==============================

                        + "\"memoryUsage\":"
                        + monitoringData.getMemoryUsage()
                        + ","

                        + "\"totalMemory\":"
                        + monitoringData.getTotalMemory()
                        + ","

                        + "\"usedMemory\":"
                        + monitoringData.getUsedMemory()
                        + ","

                        + "\"availableMemory\":"
                        + monitoringData.getAvailableMemory()
                        + ","

                        // ==============================
                        // DISK
                        // ==============================

                        + "\"diskUsage\":"
                        + monitoringData.getDiskUsage()
                        + ","

                        + "\"totalDisk\":"
                        + monitoringData.getTotalDisk()
                        + ","

                        + "\"usedDisk\":"
                        + monitoringData.getUsedDisk()
                        + ","

                        + "\"availableDisk\":"
                        + monitoringData.getAvailableDisk()
                        + ","

                        // ==============================
                        // NETWORK
                        // ==============================

                        + "\"networkData\":"
                        + networkJson
                        + ","

                        // ==============================
                        // PROCESS
                        // ==============================

                        + "\"processData\":"
                        + processJson

                        + "}";

        // ==============================
        // SEND REQUEST
        // ==============================

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "X-Agent-Credential",
                                    config.getCredential()
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(json)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Monitoring response: "
                            + response.statusCode()
            );

            System.out.println(
                    "Server response: "
                            + response.body()
            );

            return response.statusCode() == 200;

        } catch (Exception e) {

            System.out.println(
                    "Không thể gửi Monitoring Data: "
                            + e.getMessage()
            );

            return false;
        }
    }
}                                                               