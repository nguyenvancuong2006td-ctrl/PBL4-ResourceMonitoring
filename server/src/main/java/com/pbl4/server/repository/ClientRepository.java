package com.pbl4.server.repository;

import com.pbl4.server.model.Client;
import com.pbl4.server.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, String> {

    Optional<Client> findByIpAddress(String ipAddress);

    // Danh sách có phân trang: lọc theo trạng thái (null = tất cả)
    // và tìm theo hostname / IP / clientId (q rỗng = không tìm).
    @Query("""
        SELECT c
        FROM Client c
        WHERE (:status IS NULL OR c.status = :status)
          AND (
                LOWER(c.hostname) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(c.clientId) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(c.ipAddress) LIKE LOWER(CONCAT('%', :q, '%'))
          )
    """)
    Page<Client> search(
            @Param("status") ClientStatus status,
            @Param("q") String q,
            Pageable pageable
    );
}