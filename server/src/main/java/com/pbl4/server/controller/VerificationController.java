package com.pbl4.server.controller;

import com.pbl4.server.exception.ApiException;
import com.pbl4.server.model.Account;
import com.pbl4.server.repository.AccountRepository;
import com.pbl4.server.service.VerificationCodeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

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
    // GỬI LẠI OTP XÁC THỰC EMAIL
    //
    // Luôn trả cùng một thông báo, dù email có tồn tại hay không,
    // để người ngoài không dò được email nào đã đăng ký.
    // Chỉ thực sự gửi cho tài khoản chưa xác thực email.
    // ==============================
    @PostMapping("/email/send")
    public ResponseEntity<Map<String, String>> sendEmailVerification(
            @RequestParam String email) {

        accountRepository
                .findByEmail(normalize(email))
                .filter(account ->
                        !Boolean.TRUE.equals(account.getEmailVerified()))
                .ifPresent(verificationCodeService
                        ::sendEmailVerificationCode);

        return ResponseEntity.ok(Map.of(
                "message",
                "Nếu email hợp lệ và chưa được xác thực, "
                + "mã OTP đã được gửi đến email"
        ));
    }

    // ==============================
    // XÁC THỰC OTP
    // ==============================
    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @RequestParam String email,
            @RequestParam String code) {

        Account account = accountRepository
                .findByEmail(normalize(email))
                .orElseThrow(() ->
                        ApiException.badRequest(
                                "Mã xác thực không đúng "
                                + "hoặc đã hết hạn"
                        )
                );

        verificationCodeService.verifyEmail(
                account,
                code.trim()
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "Xác thực email thành công. "
                + "Tài khoản đã được kích hoạt."
        ));
    }

    private String normalize(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }
}