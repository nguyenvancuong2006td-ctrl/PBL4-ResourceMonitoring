package com.pbl4.server.service;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.enums.RoleName;
import com.pbl4.server.exception.ApiException;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Role;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    // ĐĂNG KÝ TÀI KHOẢN MONITOR
    // =========================================================

    /**
     * Đăng ký tài khoản mới.
     *
     * - Role luôn là MONITOR, do server quyết định (client không chọn).
     * - Tài khoản ở trạng thái PENDING cho tới khi xác thực email bằng OTP.
     * - Nếu email này đã đăng ký nhưng chưa xác thực (người dùng đăng ký
     *   dở, hoặc mất mã), cập nhật lại tài khoản chờ đó và gửi OTP mới
     *   thay vì báo "email đã tồn tại" làm họ kẹt không đăng ký được.
     * - Gửi email lỗi thì cả đăng ký bị hủy (không để lại tài khoản
     *   chưa có OTP).
     */
    @Transactional
    public Account register(
            String username,
            String email,
            String password) {

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        // 1. Role MONITOR
        Role monitorRole = roleRepository
                .findByRoleName(RoleName.MONITOR)
                .orElseThrow(() ->
                        ApiException.internal(
                                "Hệ thống chưa cấu hình vai trò MONITOR"
                        )
                );

        LocalDateTime now = LocalDateTime.now();

        // 2. Tìm tài khoản theo email
        Optional<Account> byEmail =
                accountRepository.findByEmail(normalizedEmail);

        Account account;

        if (byEmail.isPresent()) {

            Account existing = byEmail.get();

            // Email đã dùng cho tài khoản đang hoạt động / đã xác thực
            // / không phải MONITOR đang chờ -> không cho đăng ký lại
            if (!isPendingMonitorRegistration(existing)) {
                throw ApiException.conflict(
                        "Email đã được sử dụng"
                );
            }

            // Username mới không được trùng tài khoản khác
            accountRepository
                    .findByUsername(normalizedUsername)
                    .filter(other -> !other.getAccountId()
                            .equals(existing.getAccountId()))
                    .ifPresent(other -> {
                        throw ApiException.conflict(
                                "Username đã tồn tại"
                        );
                    });

            account = existing;
            account.setUsername(normalizedUsername);

        } else {

            if (accountRepository.existsByUsername(normalizedUsername)) {
                throw ApiException.conflict(
                        "Username đã tồn tại"
                );
            }

            account = new Account();
            account.setUsername(normalizedUsername);
            account.setEmail(normalizedEmail);
            account.setCreateAt(now);
        }

        // 3. Thông tin tài khoản
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(monitorRole);
        account.setEmailVerified(false);
        account.setStatus(AccountStatus.PENDING);
        account.setUpdateAt(now);

        Account savedAccount = accountRepository.save(account);

        // 4. Tạo và gửi OTP xác thực email
        verificationCodeService.sendEmailVerificationCode(savedAccount);

        return savedAccount;
    }

    /**
     * Tài khoản MONITOR đăng ký dở: đang PENDING và chưa xác thực email.
     */
    private boolean isPendingMonitorRegistration(Account account) {

        return account.getStatus() == AccountStatus.PENDING
                && !Boolean.TRUE.equals(account.getEmailVerified())
                && account.getRole() != null
                && account.getRole().getRoleName() == RoleName.MONITOR;
    }

    // =========================================================
    // ADMIN TẠO TÀI KHOẢN
    // =========================================================

    /**
     * ADMIN tạo tài khoản với vai trò tùy chọn (ADMIN / MONITOR).
     *
     * - Tài khoản được kích hoạt ngay (ACTIVE, email đã xác thực), không
     *   cần OTP: ADMIN đã tin tưởng người được tạo tài khoản.
     * - Nếu email trùng với một tài khoản MONITOR đăng ký dở (PENDING,
     *   chưa xác thực) thì dùng lại tài khoản đó thay vì báo trùng,
     *   giống luồng đăng ký.
     */
    @Transactional
    public Account createAccount(
            String username,
            String email,
            String password,
            RoleName roleName) {

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Role role = roleRepository
                .findByRoleName(roleName)
                .orElseThrow(() ->
                        ApiException.badRequest("Vai trò không tồn tại"));

        LocalDateTime now = LocalDateTime.now();

        Optional<Account> byEmail =
                accountRepository.findByEmail(normalizedEmail);

        Account account;

        if (byEmail.isPresent()) {

            Account existing = byEmail.get();

            if (!isPendingMonitorRegistration(existing)) {
                throw ApiException.conflict("Email đã được sử dụng");
            }

            ensureUsernameAvailable(
                    normalizedUsername,
                    existing.getAccountId()
            );

            account = existing;
            account.setUsername(normalizedUsername);

        } else {

            ensureUsernameAvailable(normalizedUsername, null);

            account = new Account();
            account.setUsername(normalizedUsername);
            account.setEmail(normalizedEmail);
            account.setCreateAt(now);
        }

        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(role);
        account.setEmailVerified(true);
        account.setStatus(AccountStatus.ACTIVE);
        account.setUpdateAt(now);

        return accountRepository.save(account);
    }

    // =========================================================
    // ADMIN CẬP NHẬT TÀI KHOẢN
    // =========================================================

    /**
     * ADMIN cập nhật username, email, vai trò và (tùy chọn) mật khẩu.
     *
     * - password null / rỗng: giữ nguyên mật khẩu cũ.
     * - Đổi email: email mới phải chưa ai dùng. Tài khoản đã xác thực thì
     *   giữ nguyên trạng thái xác thực (ADMIN đã chịu trách nhiệm về email
     *   mới). Tài khoản vẫn đang chờ xác thực email thì gửi OTP tới email
     *   mới, vì mã cũ đã gửi tới email cũ.
     * - Trạng thái (ACTIVE / LOCKED...) giữ nguyên, muốn khóa / mở khóa
     *   dùng API lock / unlock.
     * - Không được tự cập nhật chính mình (đổi username làm JWT đang dùng
     *   mất hiệu lực, đổi vai trò / email có thể làm mất quyền ADMIN);
     *   mật khẩu của mình thì dùng PATCH /api/accounts/me/password.
     */
    @Transactional
    public Account updateAccount(
            Long accountId,
            String username,
            String email,
            RoleName roleName,
            String password,
            String actorUsername) {

        Account target = getOrThrow(accountId);
        rejectSelf(target, actorUsername, "cập nhật");

        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Role role = roleRepository
                .findByRoleName(roleName)
                .orElseThrow(() ->
                        ApiException.badRequest("Vai trò không tồn tại"));

        // Username / email mới không được trùng tài khoản khác
        ensureUsernameAvailable(normalizedUsername, accountId);

        boolean emailChanged =
                !normalizedEmail.equalsIgnoreCase(target.getEmail());

        if (emailChanged) {
            accountRepository
                    .findByEmail(normalizedEmail)
                    .filter(other ->
                            !other.getAccountId().equals(accountId))
                    .ifPresent(other -> {
                        throw ApiException.conflict(
                                "Email đã được sử dụng");
                    });
        }

        target.setUsername(normalizedUsername);
        target.setRole(role);

        if (password != null && !password.isBlank()) {
            target.setPasswordHash(passwordEncoder.encode(password));
        }

        boolean needsNewOtp = false;

        if (emailChanged) {
            target.setEmail(normalizedEmail);
            needsNewOtp = !Boolean.TRUE.equals(target.getEmailVerified());
        }

        target.setUpdateAt(LocalDateTime.now());

        Account saved = accountRepository.save(target);

        if (needsNewOtp) {
            verificationCodeService.sendEmailVerificationCode(saved);
        }

        return saved;
    }

    // =========================================================
    // LẤY DANH SÁCH TÀI KHOẢN
    // =========================================================

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    // =========================================================
    // QUẢN LÝ TÀI KHOẢN (ADMIN)
    // =========================================================

    @Transactional
    public Account changeRole(
            Long accountId,
            RoleName newRole,
            String actorUsername) {

        Account target = getOrThrow(accountId);
        rejectSelf(target, actorUsername, "đổi vai trò");

        Role role = roleRepository.findByRoleName(newRole)
                .orElseThrow(() ->
                        ApiException.badRequest("Vai trò không tồn tại"));

        target.setRole(role);
        target.setUpdateAt(LocalDateTime.now());
        return accountRepository.save(target);
    }

    @Transactional
    public Account lock(Long accountId, String actorUsername) {

        Account target = getOrThrow(accountId);
        rejectSelf(target, actorUsername, "khóa");

        if (target.getStatus() != AccountStatus.ACTIVE) {
            throw ApiException.conflict(
                    "Chỉ khóa được tài khoản đang hoạt động");
        }

        target.setStatus(AccountStatus.LOCKED);
        target.setUpdateAt(LocalDateTime.now());
        return accountRepository.save(target);
    }

    @Transactional
    public Account unlock(Long accountId) {

        Account target = getOrThrow(accountId);

        if (target.getStatus() != AccountStatus.LOCKED) {
            throw ApiException.conflict(
                    "Tài khoản không ở trạng thái bị khóa");
        }

        target.setStatus(AccountStatus.ACTIVE);
        target.setUpdateAt(LocalDateTime.now());
        return accountRepository.save(target);
    }

    @Transactional
    public void delete(Long accountId, String actorUsername) {

        Account target = getOrThrow(accountId);
        rejectSelf(target, actorUsername, "xóa");

        accountRepository.delete(target);
    }

    // =========================================================
    // ĐỔI MẬT KHẨU KHI ĐÃ ĐĂNG NHẬP
    // =========================================================

    @Transactional
    public void changePassword(
            String username,
            String currentPassword,
            String newPassword) {

        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() ->
                        ApiException.notFound("Tài khoản không tồn tại"));

        if (!passwordEncoder.matches(
                currentPassword, account.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }

        if (passwordEncoder.matches(
                newPassword, account.getPasswordHash())) {
            throw ApiException.badRequest(
                    "Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        account.setUpdateAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    // ---------- helper ----------

    /**
     * Username phải chưa ai dùng (bỏ qua chính tài khoản đang xử lý).
     * excludeAccountId = null khi tạo tài khoản mới.
     */
    private void ensureUsernameAvailable(
            String username,
            Long excludeAccountId) {

        accountRepository
                .findByUsername(username)
                .filter(other -> excludeAccountId == null
                        || !other.getAccountId().equals(excludeAccountId))
                .ifPresent(other -> {
                    throw ApiException.conflict("Username đã tồn tại");
                });
    }

    private Account getOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        ApiException.notFound("Không tìm thấy tài khoản"));
    }

    // Admin không được tự đổi vai trò / khóa / xóa chính mình
    // => luôn còn ít nhất một ADMIN hoạt động
    private void rejectSelf(
            Account target,
            String actorUsername,
            String action) {

        if (target.getUsername().equals(actorUsername)) {
            throw ApiException.badRequest(
                    "Không thể tự " + action + " chính mình");
        }
    }
}