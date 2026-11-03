package com.pbl4.server.service;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.exception.ApiException;
import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Quên mật khẩu: 2 bước.
 *
 *   1. requestReset  - nhập email, hệ thống gửi OTP 6 số
 *   2. resetPassword - nhập email + OTP + mật khẩu mới
 */
@Service
public class PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetService.class);

    private static final String INVALID_CODE_MESSAGE =
            "Mã xác thực không đúng hoặc đã hết hạn";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;

    public PasswordResetService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            VerificationCodeService verificationCodeService) {

        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
    }

    // =========================================================
    // BƯỚC 1: YÊU CẦU MÃ ĐẶT LẠI MẬT KHẨU
    // =========================================================

    /**
     * Gửi OTP nếu email thuộc một tài khoản đang hoạt động.
     *
     * Cố ý KHÔNG @Transactional và KHÔNG báo lỗi khi email không tồn tại
     * hoặc gửi mail thất bại: controller luôn trả cùng một thông báo,
     * để người ngoài không dò được email nào đã đăng ký.
     */
    public void requestReset(String email) {

        Optional<Account> found = accountRepository
                .findByEmail(normalize(email))
                .filter(this::canResetPassword);

        if (found.isEmpty()) {
            // Không báo cho người gọi (tránh lộ email nào đã đăng ký),
            // nhưng ghi log để biết vì sao không có OTP nào được gửi.
            log.info("Bỏ qua yêu cầu đặt lại mật khẩu: "
                    + "email không tồn tại hoặc tài khoản "
                    + "chưa kích hoạt / chưa xác thực email");
            return;
        }

        try {

            verificationCodeService.sendPasswordResetCode(found.get());

        } catch (ApiException e) {

            // Đang trong thời gian chờ / vượt giới hạn gửi:
            // mã trước đó vẫn còn hiệu lực
            log.info("Bỏ qua yêu cầu đặt lại mật khẩu: {}",
                    e.getMessage());

        } catch (MailException e) {

            log.error("Không gửi được email đặt lại mật khẩu", e);
        }
    }

    // =========================================================
    // BƯỚC 2: ĐẶT MẬT KHẨU MỚI
    // =========================================================

    /**
     * Kiểm tra OTP rồi đổi mật khẩu.
     *
     * noRollbackFor: số lần nhập sai OTP vẫn phải được lưu.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(
            String email,
            String code,
            String newPassword) {

        Account account = accountRepository
                .findByEmail(normalize(email))
                .filter(this::canResetPassword)
                .orElseThrow(() ->
                        ApiException.badRequest(INVALID_CODE_MESSAGE)
                );

        // Ném ApiException nếu OTP sai / hết hạn / quá số lần
        verificationCodeService.verifyPasswordResetCode(
                account,
                code
        );

        account.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );

        account.setUpdateAt(LocalDateTime.now());

        accountRepository.save(account);
    }

    // Chỉ tài khoản ACTIVE và đã xác thực email mới được đặt lại mật khẩu
    private boolean canResetPassword(Account account) {

        return account.getStatus() == AccountStatus.ACTIVE
                && Boolean.TRUE.equals(account.getEmailVerified());
    }

    private String normalize(String email) {

        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }
}