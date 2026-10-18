package com.pbl4.server.service;

import com.pbl4.server.dto.response.LoginResponse;
import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil) {

        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(
            String username,
            String password) {

        // 1. Tìm tài khoản
        Account account = accountRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Username không tồn tại"
                        )
                );

        // 2. Kiểm tra trạng thái tài khoản
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException(
                    "Tài khoản chưa được kích hoạt hoặc đã bị khóa"
            );
        }

        // 3. Kiểm tra email đã xác thực chưa
        if (!Boolean.TRUE.equals(
                account.getEmailVerified())) {

            throw new RuntimeException(
                    "Email chưa được xác thực"
            );
        }

        // 4. Kiểm tra password
        if (!passwordEncoder.matches(
                password,
                account.getPasswordHash())) {

            throw new RuntimeException(
                    "Mật khẩu không đúng"
            );
        }

        // 5. Cập nhật thời gian đăng nhập
        account.setLastLoginAt(
                LocalDateTime.now()
        );

        account.setUpdateAt(
                LocalDateTime.now()
        );

        accountRepository.save(account);

        // 6. Tạo JWT
        String token =
                jwtUtil.generateToken(account);

        // 7. Trả thông tin đăng nhập
        return new LoginResponse(
                token,
                account.getUsername(),
                account.getRole().getRoleName().name()
        );
    }
}
