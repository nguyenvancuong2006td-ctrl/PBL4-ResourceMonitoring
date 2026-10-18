package pbl4.agent.monitoring;

import pbl4.agent.model.ClientSystemInfo;
import oshi.SystemInfo;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class ClientInfoService {

    private final SystemInfo systemInfo;

    public ClientInfoService() {
        this.systemInfo = new SystemInfo();
    }

    public ClientSystemInfo getClientInfo(
            String clientId) {

        OperatingSystem os =
                systemInfo.getOperatingSystem();

        ClientSystemInfo info =
                new ClientSystemInfo();

        info.setClientId(clientId);

        // Hostname
        try {

            String hostname =
                    InetAddress
                            .getLocalHost()
                            .getHostName();

            info.setHostname(hostname);

        } catch (Exception e) {

            info.setHostname("UNKNOWN");
        }

        // IP
        try {

            String ipAddress =
                    InetAddress
                            .getLocalHost()
                            .getHostAddress();

            info.setIpAddress(ipAddress);

        } catch (Exception e) {

            info.setIpAddress("UNKNOWN");
        }

        // Operating System
        info.setOsName(
                os.getFamily()
        );

        info.setOsVersion(
                os.getVersionInfo()
                        .getVersion()
        );

        // Kernel
        info.setKernelVersion(
                os.getVersionInfo()
                        .getBuildNumber()
        );

        // Boot time
        long bootTime =
                os.getSystemBootTime();

        info.setBootTime(
                LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(
                                bootTime
                        ),
                        ZoneId.systemDefault()
                )
        );

        return info;
    }
}