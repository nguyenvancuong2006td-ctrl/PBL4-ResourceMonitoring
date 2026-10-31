
package com.pbl4.server.service;

import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountDetailsService {

    private final AccountRepository accountRepository;

    public AccountDetailsService(
            AccountRepository accountRepository) {

        this.accountRepository = accountRepository;
    }

    // ==================================================
    // LẤY THÔNG TIN TÀI KHOẢN THEO USERNAME
    // ==================================================

    public Account getAccountByUsername(String username) {

        // 1. Kiểm tra username
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username không được để trống"
            );
        }

        // 2. Chuẩn hóa username
        String normalizedUsername = username.trim();

        // 3. Truy vấn Account cùng Role và Permissions
        return accountRepository
                .findByUsernameWithRoleAndPermissions(normalizedUsername)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy tài khoản: "
                                        + normalizedUsername
                        )
                );
    }
}
