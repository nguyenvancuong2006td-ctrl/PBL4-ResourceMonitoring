package com.pbl4.server.repository;

import com.pbl4.server.model.RemoteCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemoteCommandRepository
        extends JpaRepository<RemoteCommand, Long> {

    List<RemoteCommand> findByClientClientId(String clientId);
}