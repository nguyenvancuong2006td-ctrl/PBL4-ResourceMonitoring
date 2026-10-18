package com.pbl4.server.repository;

import com.pbl4.server.model.CpuInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CpuInfoRepository
        extends JpaRepository<CpuInfo, Long> {

    Optional<CpuInfo> findByClientClientId(String clientId);
} 