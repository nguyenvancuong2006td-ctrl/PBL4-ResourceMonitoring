package com.pbl4.server.dto.response;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;

import java.time.LocalDateTime;

/**
 * Thông tin tài khoản trả về cho client.
 * Cố ý KHÔNG có passwordHash.
 */
public class AccountResponse {

    private Long accountId;
    private String username;
    private String email;
    private String role;
    private AccountStatus status;
    private Boolean emailVerified;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private LocalDateTime lastLoginAt;

    public AccountResponse() {
    }

    public static AccountResponse from(Account account) {

        AccountResponse response = new AccountResponse();

        response.accountId = account.getAccountId();
        response.username = account.getUsername();
        response.email = account.getEmail();

        response.role = (account.getRole() != null
                && account.getRole().getRoleName() != null)
                ? account.getRole().getRoleName().name()
                : null;

        response.status = account.getStatus();
        response.emailVerified = account.getEmailVerified();
        response.createAt = account.getCreateAt();
        response.updateAt = account.getUpdateAt();
        response.lastLoginAt = account.getLastLoginAt();

        return response;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
}