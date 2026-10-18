package com.pbl4.server.service;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.enums.RoleName;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Role;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;

    public AccountService(
            AccountRepository accountRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            VerificationCodeService verificationCodeService) {

        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
    }

    // =========================================================
    // ĐĂNG KÝ TÀI KHOẢN
    // =========================================================

    public Account register(
            String username,
            String email,
            String password) {

        // 1. Kiểm tra username
        if (accountRepository.existsByUsername(username)) {
            throw new RuntimeException(
                    "Username đã tồn tại"
            );
        }

        // 2. Kiểm tra email
        if (accountRepository.existsByEmail(email)) {
            throw new RuntimeException(
                    "Email đã được sử dụng"
            );
        }

        // 3. Tìm role MONITOR
        Role monitorRole = roleRepository
                .findByRoleName(RoleName.MONITOR)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Role MONITOR không tồn tại"
                        )
                );

        // 4. Tạo Account
        Account account = new Account();

        account.setUsername(username);
        account.setEmail(email);

        // 5. Hash password
        account.setPasswordHash(
                passwordEncoder.encode(password)
        );

        // 6. Người đăng ký mặc định là MONITOR
        account.setRole(monitorRole);

        // 7. Chưa xác thực email
        account.setEmailVerified(false);

        // 8. Chờ xác thực
        account.setStatus(AccountStatus.PENDING);

        // 9. Thời gian
        LocalDateTime now = LocalDateTime.now();

        account.setCreateAt(now);
        account.setUpdateAt(now);

        // 10. Lưu Account
        Account savedAccount =
                accountRepository.save(account);

        // 11. Tạo và gửi OTP
        verificationCodeService
                .createVerificationCode(savedAccount);

        return savedAccount;
    }

    // =========================================================
    // LẤY DANH SÁCH TÀI KHOẢN
    // =========================================================

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
}