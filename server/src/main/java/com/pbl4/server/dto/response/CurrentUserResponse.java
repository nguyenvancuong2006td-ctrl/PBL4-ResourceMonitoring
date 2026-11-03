package com.pbl4.server.dto.response;

import java.util.List;

/**
 * Thông tin người dùng đang đăng nhập.
 * Trả về bởi GET /api/auth/me (không chứa token).
 */
public class CurrentUserResponse {

    private String username;
    private String email;
    private String role;
    private List<String> permissions;

    public CurrentUserResponse() {
    }

    public CurrentUserResponse(
            String username,
            String email,
            String role,
            List<String> permissions) {

        this.username = username;
        this.email = email;
        this.role = role;
        this.permissions = permissions;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}