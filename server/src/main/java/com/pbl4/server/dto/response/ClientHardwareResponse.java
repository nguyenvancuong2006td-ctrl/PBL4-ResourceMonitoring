package com.pbl4.server.dto.response;

import com.pbl4.server.model.CpuInfo;
import com.pbl4.server.model.MemoryInfo;

/**
 * Thông tin phần cứng tĩnh của một máy (CPU, RAM).
 *
 * cpu / memory là null nếu máy chưa có dữ liệu - ví dụ Agent đăng ký
 * bằng phiên bản cũ chưa gửi thông tin phần cứng.
 */
public class ClientHardwareResponse {

    private String clientId;
    private Cpu cpu;
    private Memory memory;

    public ClientHardwareResponse(
            String clientId, CpuInfo cpuInfo, MemoryInfo memoryInfo) {

        this.clientId = clientId;
        this.cpu = cpuInfo == null ? null : new Cpu(cpuInfo);
        this.memory = memoryInfo == null ? null : new Memory(memoryInfo);
    }

    public String getClientId() {
        return clientId;
    }

    public Cpu getCpu() {
        return cpu;
    }

    public Memory getMemory() {
        return memory;
    }

    public static class Cpu {

        private final String modelName;
        private final String manufacturer;
        private final Integer coreCount;
        private final Integer threadCount;
        private final Double frequencyMhz;

        Cpu(CpuInfo info) {
            this.modelName = info.getModelName();
            this.manufacturer = info.getManufacturer();
            this.coreCount = info.getCoreCount();
            this.threadCount = info.getThreadCount();
            this.frequencyMhz = info.getFrequencyMhz();
        }

        public String getModelName() {
            return modelName;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public Integer getCoreCount() {
            return coreCount;
        }

        public Integer getThreadCount() {
            return threadCount;
        }

        public Double getFrequencyMhz() {
            return frequencyMhz;
        }
    }

    public static class Memory {

        private final Long totalBytes;

        Memory(MemoryInfo info) {
            this.totalBytes = info.getTotalBytes();
        }

        public Long getTotalBytes() {
            return totalBytes;
        }
    }
}