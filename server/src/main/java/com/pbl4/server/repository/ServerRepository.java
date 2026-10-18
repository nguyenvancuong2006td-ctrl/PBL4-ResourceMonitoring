package com.pbl4.server.repository;

import com.pbl4.server.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository
        extends JpaRepository<Server, Long> {
}