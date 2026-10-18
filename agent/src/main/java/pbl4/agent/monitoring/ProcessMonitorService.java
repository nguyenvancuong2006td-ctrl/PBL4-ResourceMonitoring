package pbl4.agent.monitoring;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

import pbl4.agent.model.ProcessData;

public class ProcessMonitorService {

    private final OperatingSystem operatingSystem;
    private final CentralProcessor processor;

    public ProcessMonitorService() {

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
                        10
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
}