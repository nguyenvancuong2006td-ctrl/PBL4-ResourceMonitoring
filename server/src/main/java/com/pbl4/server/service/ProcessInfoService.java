package com.pbl4.server.service;

import com.pbl4.server.model.MonitoringSnapshot;
import com.pbl4.server.model.ProcessInfo;
import com.pbl4.server.repository.ProcessInfoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProcessInfoService {

    private final ProcessInfoRepository processInfoRepository;

    public ProcessInfoService(
            ProcessInfoRepository processInfoRepository) {

        this.processInfoRepository =
                processInfoRepository;
    }

    public ProcessInfo saveProcess(
            MonitoringSnapshot snapshot,
            Long pid,
            String name,
            Double cpuUsage,
            Double memoryUsage) {

        ProcessInfo processInfo =
                new ProcessInfo();

        processInfo.setPid(pid);
        processInfo.setSnapshot(snapshot);
        processInfo.setSnapshotId(
                snapshot.getSnapshotId()
        );
        processInfo.setName(name);
        processInfo.setCpuUsage(cpuUsage);
        processInfo.setMemoryUsage(memoryUsage);

        /*
         * Process còn tồn tại tại thời điểm
         * Agent gửi monitoring data.
         */
        processInfo.setStatus(
                com.pbl4.server.enums.ProcessStatus.RUNNING
        );

        return processInfoRepository.save(
                processInfo
        );
    }

    public List<ProcessInfo> getProcessesBySnapshot(
            Long snapshotId) {

        return processInfoRepository
                .findBySnapshotSnapshotId(snapshotId);
    }
}