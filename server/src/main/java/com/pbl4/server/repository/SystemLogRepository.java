package com.pbl4.server.repository;

import com.pbl4.server.model.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemLogRepository
        extends JpaRepository<SystemLog, Long> {
}