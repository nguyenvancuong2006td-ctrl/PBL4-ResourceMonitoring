package com.pbl4.server.repository;

import com.pbl4.server.model.ServerServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerServiceStatusRepository
        extends JpaRepository<ServerServiceStatus, Long> {

    List<ServerServiceStatus>
    findByServerServerId(Long serverId);
}