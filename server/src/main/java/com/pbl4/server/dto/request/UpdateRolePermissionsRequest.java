package com.pbl4.server.dto.request;

import com.pbl4.server.enums.PermissionCode;

import java.util.List;

public class UpdateRolePermissionsRequest {

    private List<PermissionCode> permissionCodes;

    public UpdateRolePermissionsRequest() {
    }

    public List<PermissionCode> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(
            List<PermissionCode> permissionCodes) {

        this.permissionCodes = permissionCodes;
    }
}