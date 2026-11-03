package com.pbl4.server.repository;

import com.pbl4.server.enums.CommandStatus;
import com.pbl4.server.enums.CommandType;
import com.pbl4.server.model.RemoteCommand;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * Lệnh PENDING đã chờ quá lâu mà Agent chưa nhận.
     * Lệnh cũ chưa có created_at (tạo trước khi thêm cột) được coi là quá hạn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rc
            FROM RemoteCommand rc
            WHERE rc.status = com.pbl4.server.enums.CommandStatus.PENDING
              AND (rc.createdAt IS NULL OR rc.createdAt < :cutoff)
            ORDER BY rc.commandId ASC
            """)
    List<RemoteCommand> findStalePendingForUpdate(
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Lệnh SENT (Agent đã lấy) nhưng không chuyển sang RUNNING.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rc
            FROM RemoteCommand rc
            WHERE rc.status = com.pbl4.server.enums.CommandStatus.SENT
              AND (rc.sentAt IS NULL OR rc.sentAt < :cutoff)
            ORDER BY rc.commandId ASC
            """)
    List<RemoteCommand> findStaleSentForUpdate(
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Lệnh RUNNING nhưng Agent không báo kết quả
     * (ví dụ SHUTDOWN/RESTART làm Agent tắt trước khi kịp báo).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rc
            FROM RemoteCommand rc
            WHERE rc.status = com.pbl4.server.enums.CommandStatus.RUNNING
              AND (rc.startedAt IS NULL OR rc.startedAt < :cutoff)
            ORDER BY rc.commandId ASC
            """)
    List<RemoteCommand> findStaleRunningForUpdate(
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Danh sách lệnh toàn hệ thống (có phân trang).
     * Tham số nào null thì không lọc theo tham số đó.
     */
    @EntityGraph(attributePaths = {"account", "client"})
    @Query("""
            SELECT rc
            FROM RemoteCommand rc
            WHERE (:clientId IS NULL OR rc.client.clientId = :clientId)
              AND (:status IS NULL OR rc.status = :status)
              AND (:commandType IS NULL OR rc.commandType = :commandType)
            """)
    Page<RemoteCommand> search(
            @Param("clientId") String clientId,
            @Param("status") CommandStatus status,
            @Param("commandType") CommandType commandType,
            Pageable pageable
    );
}