package com.pbl4.server.service;

import com.pbl4.server.dto.response.CurrentUserResponse;
import com.pbl4.server.dto.response.LoginResponse;
import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.util.JwtUtil;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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

    /**
     * Xác thực tài khoản và tạo JWT khi đăng nhập thành công.
     */
    @Transactional
    public LoginResponse login(
            String username,
            String password) {

        // 1. Kiểm tra dữ liệu đầu vào
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username không được để trống"
            );
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Mật khẩu không được để trống"
            );
        }

        // 2. Tìm tài khoản theo username, nếu không có thì thử theo email.
        //    Email lưu dạng chữ thường nên chuẩn hóa trước khi tìm.
        String identifier = username.trim();

        Account account = accountRepository
                .findByUsername(identifier)
                .or(() -> accountRepository.findByEmail(
                        identifier.toLowerCase(Locale.ROOT)))
                .orElseThrow(() ->
                        new RuntimeException(
                                "Username hoặc mật khẩu không đúng"
                        )
                );

        // 3. Kiểm tra trạng thái tài khoản
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException(
                    "Tài khoản chưa được kích hoạt hoặc đã bị khóa"
            );
        }

        // 4. Kiểm tra email đã xác thực
        if (!Boolean.TRUE.equals(
                account.getEmailVerified())) {

            throw new RuntimeException(
                    "Email chưa được xác thực"
            );
        }

        // 5. Kiểm tra mật khẩu
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(
                        password,
                        account.getPasswordHash())) {

            throw new RuntimeException(
                    "Username hoặc mật khẩu không đúng"
            );
        }

        // 6. Kiểm tra vai trò
        if (account.getRole() == null
                || account.getRole().getRoleName() == null) {

            throw new IllegalStateException(
                    "Tài khoản chưa được gán vai trò"
            );
        }

        // 7. Cập nhật thời gian đăng nhập
        LocalDateTime now = LocalDateTime.now();

        account.setLastLoginAt(now);
        account.setUpdateAt(now);

        accountRepository.save(account);

        // 8. Tạo JWT
        String token = jwtUtil.generateToken(account);

        // 9. Trả thông tin đăng nhập (kèm danh sách quyền)
        return new LoginResponse(
                token,
                account.getUsername(),
                account.getRole().getRoleName().name(),
                extractPermissions(account)
        );
    }

    /**
     * Lấy thông tin người dùng hiện tại (role + permissions mới nhất).
     * Frontend gọi khi tải lại trang để đồng bộ quyền,
     * phòng trường hợp quyền của vai trò đã bị thay đổi.
     */
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username không được để trống"
            );
        }

        Account account = accountRepository
                .findByUsernameWithRoleAndPermissions(username.trim())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Tài khoản không tồn tại"
                        )
                );

        if (account.getRole() == null
                || account.getRole().getRoleName() == null) {

            throw new IllegalStateException(
                    "Tài khoản chưa được gán vai trò"
            );
        }

        return new CurrentUserResponse(
                account.getUsername(),
                account.getEmail(),
                account.getRole().getRoleName().name(),
                extractPermissions(account)
        );
    }

    /**
     * Lấy danh sách mã quyền (đã sắp xếp) của vai trò trong tài khoản.
     */
    private List<String> extractPermissions(Account account) {

        if (account.getRole() == null
                || account.getRole().getPermissions() == null) {

            return List.of();
        }

        return account.getRole().getPermissions().stream()
                .filter(permission -> permission != null
                        && permission.getPermissionCode() != null)
                .map(permission ->
                        permission.getPermissionCode().name())
                .sorted()
                .toList();
    }
}