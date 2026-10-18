package com.pbl4.server.controller;

import com.pbl4.server.model.Account;
import com.pbl4.server.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(
            AccountService accountService) {

        this.accountService = accountService;
    }

    // =========================================================
    // ĐĂNG KÝ TÀI KHOẢN
    // Không cần đăng nhập
    // =========================================================

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password) {

        accountService.register(
                username,
                email,
                password
        );

        return ResponseEntity.ok(
                "Đăng ký thành công. "
                + "Vui lòng kiểm tra email để xác thực."
        );
    }

    // =========================================================
    // XEM DANH SÁCH TÀI KHOẢN
    // Cần quyền ACCOUNT_VIEW
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public ResponseEntity<List<Account>> getAllAccounts() {

        return ResponseEntity.ok(
                accountService.getAllAccounts()
        );
    }
}