package com.pbl4.server.repository;

import com.pbl4.server.model.MemoryInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemoryInfoRepository
        extends JpaRepository<MemoryInfo, Long> {

    Optional<MemoryInfo> findByClientClientId(String clientId);
}