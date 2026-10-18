package com.pbl4.server.service;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.enums.VerificationStatus;
import com.pbl4.server.enums.VerificationType;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.VerificationCode;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.repository.VerificationCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class VerificationCodeService {

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

    /**
     * Tạo và gửi mã xác thực email
     */
    public void createVerificationCode(Account account) {

        // 1. Tạo OTP 6 số
        String code = generateCode();

        // 2. Hash OTP trước khi lưu database
        String codeHash = passwordEncoder.encode(code);

        // 3. Tạo VerificationCode
        VerificationCode verificationCode =
                new VerificationCode();

        verificationCode.setAccount(account);

        // Loại xác thực
        verificationCode.setVerificationType(
                VerificationType.EMAIL_VERIFICATION
        );

        // Email nhận mã
        verificationCode.setTarget(
                account.getEmail()
        );

        // Lưu hash, không lưu OTP thật
        verificationCode.setCodeHash(
                codeHash
        );

        // OTP có hiệu lực 5 phút
        verificationCode.setExpiresAt(
                LocalDateTime.now().plusMinutes(5)
        );

        // Chưa xác thực
        verificationCode.setVerifiedAt(null);

        // Số lần nhập sai
        verificationCode.setAttemptCount(0);

        // Số lần gửi lại
        verificationCode.setResendCount(0);

        // Trạng thái ban đầu
        verificationCode.setStatus(
                VerificationStatus.PENDING
        );

        // Thời gian tạo
        verificationCode.setCreatedAt(
                LocalDateTime.now()
        );

        // 4. Lưu database
        verificationCodeRepository.save(
                verificationCode
        );

        // 5. Gửi OTP thật qua email
        emailService.sendVerificationCode(
                account.getEmail(),
                code
        );
    }

    /**
     * Tạo OTP 6 chữ số
     */
    private String generateCode() {

        SecureRandom random =
                new SecureRandom();

        int number =
                100000 + random.nextInt(900000);

        return String.valueOf(number);
    }

    /**
     * Xác thực email bằng OTP
     */
    public void verifyEmail(
            Account account,
            String code) {

        // 1. Lấy mã xác thực mới nhất
        VerificationCode verificationCode =
                verificationCodeRepository
                        .findTopByAccountAccountIdOrderByCreatedAtDesc(
                                account.getAccountId()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Không tìm thấy mã xác thực"
                                )
                        );

        // 2. Kiểm tra trạng thái mã
        if (verificationCode.getStatus()
                != VerificationStatus.PENDING) {

            throw new RuntimeException(
                    "Mã xác thực không còn hiệu lực"
            );
        }

        // 3. Kiểm tra hết hạn
        if (LocalDateTime.now()
                .isAfter(
                        verificationCode.getExpiresAt()
                )) {

            verificationCode.setStatus(
                    VerificationStatus.EXPIRED
            );

            verificationCodeRepository.save(
                    verificationCode
            );

            throw new RuntimeException(
                    "Mã xác thực đã hết hạn"
            );
        }

        // 4. Kiểm tra số lần nhập sai
        if (verificationCode.getAttemptCount() >= 5) {

            verificationCode.setStatus(
                    VerificationStatus.FAILED
            );

            verificationCodeRepository.save(
                    verificationCode
            );

            throw new RuntimeException(
                    "Bạn đã nhập sai quá số lần cho phép"
            );
        }

        // 5. Kiểm tra OTP
        boolean correct =
                passwordEncoder.matches(
                        code,
                        verificationCode.getCodeHash()
                );

        if (!correct) {

            int attemptCount =
                    verificationCode.getAttemptCount() + 1;

            verificationCode.setAttemptCount(
                    attemptCount
            );

            // Nếu sai đủ 5 lần thì khóa mã
            if (attemptCount >= 5) {

                verificationCode.setStatus(
                        VerificationStatus.FAILED
                );
            }

            verificationCodeRepository.save(
                    verificationCode
            );

            throw new RuntimeException(
                    "Mã xác thực không đúng"
            );
        }

        // 6. Xác thực thành công
        verificationCode.setStatus(
                VerificationStatus.VERIFIED
        );

        verificationCode.setVerifiedAt(
                LocalDateTime.now()
        );

        verificationCodeRepository.save(
                verificationCode
        );

        // 7. Kích hoạt tài khoản
        account.setEmailVerified(true);

        account.setStatus(
                AccountStatus.ACTIVE
        );

        // 8. Lưu Account
        accountRepository.save(account);
    }
}