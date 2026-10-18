package com.pbl4.server.controller;

import com.pbl4.server.dto.request.UpdateRolePermissionsRequest;
import com.pbl4.server.model.Permission;
import com.pbl4.server.service.RolePermissionService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/roles/{roleId}/permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(
            RolePermissionService rolePermissionService) {

        this.rolePermissionService = rolePermissionService;
    }

    // Xem quyền của Role
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Set<Permission>> getPermissions(
            @PathVariable Long roleId) {

        return ResponseEntity.ok(
                rolePermissionService.getPermissions(roleId)
        );
    }

    // Cập nhật quyền của Role
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updatePermissions(
            @PathVariable Long roleId,
            @RequestBody UpdateRolePermissionsRequest request) {

        rolePermissionService.updatePermissions(
                roleId,
                request
        );

        return ResponseEntity.ok(
                "Cập nhật quyền thành công"
        );
    }
}