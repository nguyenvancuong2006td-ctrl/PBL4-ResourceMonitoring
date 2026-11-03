package com.pbl4.server.service;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.enums.VerificationStatus;
import com.pbl4.server.enums.VerificationType;
import com.pbl4.server.exception.ApiException;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.VerificationCode;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.VerificationCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Tạo, gửi và kiểm tra mã OTP.
 *
 * Dùng cho hai mục đích tách biệt:
 *   - EMAIL_VERIFICATION: xác thực email khi đăng ký
 *   - PASSWORD_RESET:     đặt lại mật khẩu khi quên
 *
 * Mã của loại này không bao giờ dùng được cho loại kia.
 */
@Service
public class VerificationCodeService {

    private static final int OTP_VALID_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    // Khoảng cách tối thiểu giữa hai lần gửi mã
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    // Tối đa số mã được gửi trong 1 giờ cho mỗi tài khoản/loại
    private static final int MAX_CODES_PER_HOUR = 5;

    private static final String INVALID_CODE_MESSAGE =
            "Mã xác thực không đúng hoặc đã hết hạn";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public VerificationCodeService(
            VerificationCodeRepository verificationCodeRepository,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {

        this.verificationCodeRepository = verificationCodeRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // =========================================================
    // GỬI MÃ
    // =========================================================

    /**
     * Tạo và gửi mã xác thực email (đăng ký).
     * Nếu gửi email lỗi, giao dịch bị hủy nên không còn mã "mồ côi".
     */
    @Transactional
    public void sendEmailVerificationCode(Account account) {

        String code = issueCode(
                account,
                VerificationType.EMAIL_VERIFICATION
        );

        emailService.sendVerificationCode(
                account.getEmail(),
                code
        );
    }

    /**
     * Tạo và gửi mã đặt lại mật khẩu.
     */
    @Transactional
    public void sendPasswordResetCode(Account account) {

        String code = issueCode(
                account,
                VerificationType.PASSWORD_RESET
        );

        emailService.sendPasswordResetCode(
                account.getEmail(),
                code
        );
    }

    /**
     * Phát hành mã mới: kiểm tra giới hạn gửi, hủy mã cũ,
     * lưu bản băm của mã mới. Trả về mã thật để gửi email.
     */
    private String issueCode(
            Account account,
            VerificationType type) {

        LocalDateTime now = LocalDateTime.now();
        Long accountId = account.getAccountId();

        // 1. Chống gửi dồn dập
        verificationCodeRepository
                .findTopByAccountAccountIdAndVerificationTypeOrderByCreatedAtDesc(
                        accountId,
                        type
                )
                .ifPresent(latest -> {

                    LocalDateTime nextAllowed = latest.getCreatedAt()
                            .plusSeconds(RESEND_COOLDOWN_SECONDS);

                    if (nextAllowed.isAfter(now)) {

                        long wait = Duration
                                .between(now, nextAllowed)
                                .toSeconds() + 1;

                        throw ApiException.tooManyRequests(
                                "Vui lòng đợi " + wait
                                + " giây trước khi yêu cầu mã mới"
                        );
                    }
                });

        // 2. Giới hạn số mã trong 1 giờ
        long sentLastHour = verificationCodeRepository
                .countByAccountAccountIdAndVerificationTypeAndCreatedAtAfter(
                        accountId,
                        type,
                        now.minusHours(1)
                );

        if (sentLastHour >= MAX_CODES_PER_HOUR) {
            throw ApiException.tooManyRequests(
                    "Bạn đã yêu cầu quá nhiều mã. "
                    + "Vui lòng thử lại sau"
            );
        }

        // 3. Hủy các mã còn hiệu lực trước đó
        List<VerificationCode> oldCodes = verificationCodeRepository
                .findByAccountAccountIdAndVerificationTypeAndStatus(
                        accountId,
                        type,
                        VerificationStatus.PENDING
                );

        for (VerificationCode old : oldCodes) {
            old.setStatus(VerificationStatus.EXPIRED);
        }

        verificationCodeRepository.saveAll(oldCodes);

        // 4. Tạo mã mới, chỉ lưu bản băm
        String code = generateCode();

        VerificationCode verificationCode = new VerificationCode();

        verificationCode.setAccount(account);
        verificationCode.setVerificationType(type);
        verificationCode.setTarget(account.getEmail());
        verificationCode.setCodeHash(passwordEncoder.encode(code));
        verificationCode.setExpiresAt(now.plusMinutes(OTP_VALID_MINUTES));
        verificationCode.setVerifiedAt(null);
        verificationCode.setAttemptCount(0);
        verificationCode.setResendCount((int) sentLastHour);
        verificationCode.setStatus(VerificationStatus.PENDING);
        verificationCode.setCreatedAt(now);

        verificationCodeRepository.save(verificationCode);

        return code;
    }

    /**
     * Tạo OTP 6 chữ số
     */
    private String generateCode() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    // =========================================================
    // KIỂM TRA MÃ
    // =========================================================

    /**
     * Xác thực email bằng OTP và kích hoạt tài khoản.
     *
     * noRollbackFor: khi nhập sai, số lần thử (attemptCount) vẫn phải
     * được lưu dù method ném lỗi.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyEmail(
            Account account,
            String code) {

        if (Boolean.TRUE.equals(account.getEmailVerified())) {
            throw ApiException.conflict(
                    "Email đã được xác thực"
            );
        }

        VerificationCode verificationCode = checkCode(
                account,
                VerificationType.EMAIL_VERIFICATION,
                code
        );

        markVerified(verificationCode);

        account.setEmailVerified(true);

        // Chỉ kích hoạt tài khoản đang chờ xác thực.
        // Tài khoản bị khóa (LOCKED) / vô hiệu (INACTIVE)
        // không được mở lại bằng OTP.
        if (account.getStatus() == AccountStatus.PENDING) {
            account.setStatus(AccountStatus.ACTIVE);
        }

        account.setUpdateAt(LocalDateTime.now());

        accountRepository.save(account);
    }

    /**
     * Kiểm tra OTP đặt lại mật khẩu và đánh dấu đã dùng.
     * Việc đổi mật khẩu do PasswordResetService thực hiện.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyPasswordResetCode(
            Account account,
            String code) {

        VerificationCode verificationCode = checkCode(
                account,
                VerificationType.PASSWORD_RESET,
                code
        );

        markVerified(verificationCode);
    }

    /**
     * Kiểm tra mã mới nhất của một loại xác thực.
     * Ném ApiException 400 nếu không hợp lệ; trả về mã nếu đúng
     * (chưa đánh dấu VERIFIED).
     */
    private VerificationCode checkCode(
            Account account,
            VerificationType type,
            String code) {

        // 1. Mã mới nhất của đúng loại
        VerificationCode verificationCode =
                verificationCodeRepository
                        .findTopByAccountAccountIdAndVerificationTypeOrderByCreatedAtDesc(
                                account.getAccountId(),
                                type
                        )
                        .orElseThrow(() ->
                                ApiException.badRequest(
                                        INVALID_CODE_MESSAGE
                                )
                        );

        // 2. Trạng thái mã
        if (verificationCode.getStatus()
                != VerificationStatus.PENDING) {

            throw ApiException.badRequest(
                    "Mã xác thực không còn hiệu lực"
            );
        }

        // 3. Hết hạn
        if (LocalDateTime.now()
                .isAfter(verificationCode.getExpiresAt())) {

            verificationCode.setStatus(VerificationStatus.EXPIRED);
            verificationCodeRepository.save(verificationCode);

            throw ApiException.badRequest(
                    "Mã xác thực đã hết hạn"
            );
        }

        // 4. Đã nhập sai quá số lần
        if (verificationCode.getAttemptCount() >= MAX_ATTEMPTS) {

            verificationCode.setStatus(VerificationStatus.FAILED);
            verificationCodeRepository.save(verificationCode);

            throw ApiException.badRequest(
                    "Bạn đã nhập sai quá số lần cho phép"
            );
        }

        // 5. So khớp OTP
        if (code == null
                || !passwordEncoder.matches(
                        code,
                        verificationCode.getCodeHash())) {

            int attemptCount = verificationCode.getAttemptCount() + 1;

            verificationCode.setAttemptCount(attemptCount);

            if (attemptCount >= MAX_ATTEMPTS) {
                verificationCode.setStatus(VerificationStatus.FAILED);
            }

            verificationCodeRepository.save(verificationCode);

            throw ApiException.badRequest(INVALID_CODE_MESSAGE);
        }

        return verificationCode;
    }

    private void markVerified(VerificationCode verificationCode) {

        verificationCode.setStatus(VerificationStatus.VERIFIED);
        verificationCode.setVerifiedAt(LocalDateTime.now());

        verificationCodeRepository.save(verificationCode);
    }
}