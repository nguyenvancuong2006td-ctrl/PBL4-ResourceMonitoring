package com.pbl4.server.repository;

import com.pbl4.server.enums.PermissionCode;
import com.pbl4.server.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository
        extends JpaRepository<Permission, Long> {

    Optional<Permission> findByPermissionCode(
            PermissionCode permissionCode
    );

    boolean existsByPermissionCode(
            PermissionCode permissionCode
    );
}