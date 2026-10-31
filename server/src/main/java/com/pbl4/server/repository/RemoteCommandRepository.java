package com.pbl4.server.repository;

import com.pbl4.server.enums.CommandStatus;
import com.pbl4.server.model.RemoteCommand;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RemoteCommandRepository
        extends JpaRepository<RemoteCommand, Long> {

    List<RemoteCommand> findByClientClientId(String clientId);

    List<RemoteCommand>
    findByClientClientIdAndStatusOrderBySentAtAsc(
            String clientId,
            CommandStatus status
    );

    /**
     * Lấy các lệnh PENDING của Client và khóa các bản ghi
     * trong transaction hiện tại để tránh xử lý đồng thời.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rc
            FROM RemoteCommand rc
            WHERE rc.client.clientId = :clientId
              AND rc.status = :status
            ORDER BY rc.commandId ASC
            """)
    List<RemoteCommand> findPendingCommandsForUpdate(
            @Param("clientId") String clientId,
            @Param("status") CommandStatus status
    );
}