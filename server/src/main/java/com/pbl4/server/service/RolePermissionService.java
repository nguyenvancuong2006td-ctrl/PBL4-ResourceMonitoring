package com.pbl4.server.service;

import com.pbl4.server.dto.request.UpdateRolePermissionsRequest;
import com.pbl4.server.model.Permission;
import com.pbl4.server.model.Role;
import com.pbl4.server.repository.PermissionRepository;
import com.pbl4.server.repository.RoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RolePermissionService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository) {

        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    // Lấy danh sách quyền của Role
    public Set<Permission> getPermissions(Long roleId) {

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Role"
                        )
                );

        return role.getPermissions();
    }

    // Cập nhật quyền cho Role
    @Transactional
    public void updatePermissions(
            Long roleId,
            UpdateRolePermissionsRequest request) {

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy Role"
                        )
                );

        Set<Permission> permissions = new HashSet<>();

        if (request.getPermissionCodes() != null) {

            for (var code : request.getPermissionCodes()) {

                Permission permission =
                        permissionRepository
                                .findByPermissionCode(code)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Không tìm thấy Permission: "
                                                        + code
                                        )
                                );

                permissions.add(permission);
            }
        }

        role.setPermissions(permissions);

        roleRepository.save(role);
    }
}