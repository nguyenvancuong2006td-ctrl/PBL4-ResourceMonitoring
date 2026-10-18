package com.pbl4.server.repository;

import com.pbl4.server.model.ProcessInfo;
import com.pbl4.server.model.ProcessInfoId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessInfoRepository
        extends JpaRepository<ProcessInfo, ProcessInfoId> {

    List<ProcessInfo> findBySnapshotSnapshotId(Long snapshotId);
}