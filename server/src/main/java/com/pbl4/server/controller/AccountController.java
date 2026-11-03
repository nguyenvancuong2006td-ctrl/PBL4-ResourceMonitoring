package com.pbl4.server.controller;

import com.pbl4.server.dto.request.ChangePasswordRequest;
import com.pbl4.server.dto.request.CreateAccountRequest;
import com.pbl4.server.dto.request.RegisterRequest;
import com.pbl4.server.dto.request.UpdateAccountRequest;
import com.pbl4.server.dto.request.UpdateAccountRoleRequest;
import com.pbl4.server.dto.response.AccountResponse;
import com.pbl4.server.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // =========================================================
    // ĐĂNG KÝ TÀI KHOẢN
    // Role mặc định là MONITOR
    // Không cần đăng nhập
    //
    // POST /api/accounts/register
    // =========================================================

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {

        accountService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "Đăng ký thành công. Vui lòng kiểm tra email để lấy mã xác thực."
        ));
    }

    // =========================================================
    // XEM DANH SÁCH TÀI KHOẢN
    // Cần quyền ACCOUNT_VIEW
    //
    // GET /api/accounts
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {

        List<AccountResponse> accounts = accountService
                .getAllAccounts()
                .stream()
                .map(AccountResponse::from)
                .toList();

        return ResponseEntity.ok(accounts);
    }

    // =========================================================
    // TẠO TÀI KHOẢN
    // Chỉ ADMIN
    //
    // POST /api/accounts/admin
    //
    // Body:
    // {
    //     "username": "monitor01",
    //     "email": "monitor01@gmail.com",
    //     "password": "Password123!",
    //     "roleName": "MONITOR"
    // }
    // =========================================================

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        accountService.createAccount(
                                request.getUsername(),
                                request.getEmail(),
                                request.getPassword(),
                                request.getRoleName()
                        )
                )
        );
    }

    // =========================================================
    // CẬP NHẬT TÀI KHOẢN
    // Chỉ ADMIN
    //
    // PUT /api/accounts/{accountId}
    //
    // Body:
    // {
    //     "username": "monitor01",
    //     "email": "newemail@gmail.com",
    //     "roleName": "MONITOR",
    //     "password": ""
    // }
    //
    // password để trống nếu không muốn đổi mật khẩu.
    // =========================================================

    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        accountService.updateAccount(
                                accountId,
                                request.getUsername(),
                                request.getEmail(),
                                request.getRoleName(),
                                request.getPassword(),
                                authentication.getName()
                        )
                )
        );
    }

    // =========================================================
    // ĐỔI VAI TRÒ
    // Chỉ ADMIN
    //
    // PATCH /api/accounts/{accountId}/role
    // =========================================================

    @PatchMapping("/{accountId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> changeRole(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountRoleRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        accountService.changeRole(
                                accountId,
                                request.getRoleName(),
                                authentication.getName()
                        )
                )
        );
    }

    // =========================================================
    // KHÓA TÀI KHOẢN
    // Chỉ ADMIN
    //
    // PATCH /api/accounts/{accountId}/lock
    // =========================================================

    @PatchMapping("/{accountId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> lock(
            @PathVariable Long accountId,
            Authentication authentication) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        accountService.lock(
                                accountId,
                                authentication.getName()
                        )
                )
        );
    }

    // =========================================================
    // MỞ KHÓA TÀI KHOẢN
    // Chỉ ADMIN
    //
    // PATCH /api/accounts/{accountId}/unlock
    // =========================================================

    @PatchMapping("/{accountId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountResponse> unlock(
            @PathVariable Long accountId) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        accountService.unlock(accountId)
                )
        );
    }

    // =========================================================
    // XÓA TÀI KHOẢN
    // Chỉ ADMIN
    //
    // DELETE /api/accounts/{accountId}
    // =========================================================

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable Long accountId,
            Authentication authentication) {

        accountService.delete(
                accountId,
                authentication.getName()
        );

        return ResponseEntity.ok(
                Map.of("message", "Đã xóa tài khoản")
        );
    }

    // =========================================================
    // ĐỔI MẬT KHẨU CỦA TÀI KHOẢN ĐANG ĐĂNG NHẬP
    //
    // PATCH /api/accounts/me/password
    // =========================================================

    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        accountService.changePassword(
                authentication.getName(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(
                Map.of("message", "Đổi mật khẩu thành công")
        );
    }
}