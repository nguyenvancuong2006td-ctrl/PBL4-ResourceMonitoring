package com.pbl4.server.dto.response;

import java.util.List;

public class LoginResponse {

    private String token;
    private String username;
    private String role;

    // Danh sách mã quyền của vai trò, ví dụ: CLIENT_VIEW, REMOTE_COMMAND
    private List<String> permissions;

    public LoginResponse() {
    }

    public LoginResponse(
            String token,
            String username,
            String role,
            List<String> permissions) {

        this.token = token;
        this.username = username;
        this.role = role;
        this.permissions = permissions;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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