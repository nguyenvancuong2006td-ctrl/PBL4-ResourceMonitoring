package pbl4.agent.monitoring;

import pbl4.agent.model.ClientSystemInfo;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
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

        // Phần cứng tĩnh: CPU + RAM. Lỗi ở đây không được làm hỏng đăng ký.
        readHardware(info);

        return info;
    }

    private void readHardware(ClientSystemInfo info) {

        try {
            CentralProcessor processor =
                    systemInfo.getHardware().getProcessor();

            CentralProcessor.ProcessorIdentifier id =
                    processor.getProcessorIdentifier();

            info.setCpuModel(clean(id.getName()));
            info.setCpuManufacturer(clean(id.getVendor()));

            int physical = processor.getPhysicalProcessorCount();
            int logical = processor.getLogicalProcessorCount();

            info.setCpuCoreCount(physical > 0 ? physical : null);
            info.setCpuThreadCount(logical > 0 ? logical : null);

            // OSHI trả Hz. getMaxFreq() có thể là -1 trên một số hệ điều hành,
            // khi đó dùng tần số danh định trong tên bộ xử lý.
            long hz = processor.getMaxFreq();
            if (hz <= 0) {
                hz = id.getVendorFreq();
            }

            info.setCpuFrequencyMhz(
                    hz > 0 ? Math.round(hz / 1_000_000.0) * 1.0 : null
            );

        } catch (Exception e) {
            System.err.println(
                    "[ClientInfoService] Không đọc được thông tin CPU: "
                            + e.getMessage());
        }

        try {
            GlobalMemory memory = systemInfo.getHardware().getMemory();
            long total = memory.getTotal();

            info.setMemoryTotalBytes(total > 0 ? total : null);

        } catch (Exception e) {
            System.err.println(
                    "[ClientInfoService] Không đọc được thông tin RAM: "
                            + e.getMessage());
        }
    }

    private String clean(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}