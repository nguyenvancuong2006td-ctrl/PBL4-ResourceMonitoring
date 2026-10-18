package com.pbl4.server.repository;

import com.pbl4.server.model.DiskInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiskInfoRepository
        extends JpaRepository<DiskInfo, Long> {

    List<DiskInfo> findByClientClientId(String clientId);
}