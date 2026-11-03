package com.pbl4.server.dto.request;

import com.pbl4.server.enums.RoleName;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu đổi vai trò của một tài khoản (chỉ ADMIN).
 */
public class UpdateAccountRoleRequest {

    @NotNull(message = "Vai trò không được để trống")
    private RoleName roleName;

    public UpdateAccountRoleRequest() {
    }

    public RoleName getRoleName() {
        return roleName;
    }

    public void setRoleName(RoleName roleName) {
        this.roleName = roleName;
    }
}