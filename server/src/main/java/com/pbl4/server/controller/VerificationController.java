package com.pbl4.server.controller;

import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.service.VerificationCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

    private final VerificationCodeService verificationCodeService;
    private final AccountRepository accountRepository;

    public VerificationController(
            VerificationCodeService verificationCodeService,
            AccountRepository accountRepository) {

        this.verificationCodeService = verificationCodeService;
        this.accountRepository = accountRepository;
    }

    // ==============================
    // GỬI OTP XÁC THỰC EMAIL
    // ==============================
    @PostMapping("/email/send")
    public ResponseEntity<?> sendEmailVerification(
            @RequestParam String email) {

        Account account = accountRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy tài khoản"
                        )
                );

        verificationCodeService.createVerificationCode(
                account
        );

        return ResponseEntity.ok(
                "Đã gửi mã OTP đến email"
        );
    }

    // ==============================
    // XÁC THỰC OTP
    // ==============================
    @PostMapping("/email")
    public ResponseEntity<?> verifyEmail(
            @RequestParam String email,
            @RequestParam String code) {

        Account account = accountRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Không tìm thấy tài khoản"
                        )
                );

        verificationCodeService.verifyEmail(
                account,
                code
        );

        return ResponseEntity.ok(
                "Xác thực email thành công. "
                        + "Tài khoản đã được kích hoạt."
        );
    }
}