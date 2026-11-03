package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

import pbl4.agent.model.ProcessData;

public class ProcessMonitorService {

    // Số tiến trình gửi lên Server mỗi lần thu thập (xếp theo CPU giảm dần).
    // Cấu hình bằng biến môi trường AGENT_PROCESS_LIMIT hoặc
    // -Dagent.process.limit=100
    private static final int DEFAULT_PROCESS_LIMIT = 50;
    private static final int MAX_PROCESS_LIMIT = 500;

    private final OperatingSystem operatingSystem;
    private final CentralProcessor processor;
    private final int processLimit;

    public ProcessMonitorService() {

        processLimit = readProcessLimit();

        SystemInfo systemInfo =
                new SystemInfo();

        operatingSystem =
                systemInfo.getOperatingSystem();

        processor =
                systemInfo.getHardware()
                        .getProcessor();
    }

    public List<ProcessData> collectProcessData() {

        List<ProcessData> processDataList =
                new ArrayList<>();

        List<OSProcess> processes =
                operatingSystem.getProcesses(
                        null,
                        OperatingSystem.ProcessSorting.CPU_DESC,
                        processLimit
                );

        int logicalProcessorCount =
                processor.getLogicalProcessorCount();

        for (OSProcess process : processes) {

            double cpuUsage =
                    process.getProcessCpuLoadCumulative()
                            * 100
                            / logicalProcessorCount;

            long memoryUsage =
                    process.getResidentSetSize();

            ProcessData processData =
                    new ProcessData(
                            process.getProcessID(),
                            process.getName(),
                            cpuUsage,
                            memoryUsage
                    );

            processDataList.add(
                    processData
            );
        }

        return processDataList;
    }

    private static int readProcessLimit() {

        String raw = System.getenv("AGENT_PROCESS_LIMIT");

        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("agent.process.limit");
        }

        if (raw == null || raw.isBlank()) {
            return DEFAULT_PROCESS_LIMIT;
        }

        try {
            int value = Integer.parseInt(raw.trim());

            if (value < 1) {
                return DEFAULT_PROCESS_LIMIT;
            }

            return Math.min(value, MAX_PROCESS_LIMIT);

        } catch (NumberFormatException e) {
            return DEFAULT_PROCESS_LIMIT;
        }
    }
}