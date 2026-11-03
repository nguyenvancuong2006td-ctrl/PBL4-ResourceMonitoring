package com.pbl4.server.controller;

import com.pbl4.server.dto.request.ForgotPasswordRequest;
import com.pbl4.server.dto.request.LoginRequest;
import com.pbl4.server.dto.request.ResetPasswordRequest;
import com.pbl4.server.dto.response.LoginResponse;
import com.pbl4.server.service.AuthService;
import com.pbl4.server.service.PasswordResetService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService) {

        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /**
     * API đăng nhập.
     *
     * Nhận username và password từ request body dạng JSON.
     * Nếu đăng nhập thành công, trả về JWT cùng thông tin tài khoản
     * (username, role, permissions).
     *
     * Endpoint: POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        try {
            // Kiểm tra dữ liệu đầu vào
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body("Dữ liệu đăng nhập không hợp lệ");
            }

            // Thực hiện đăng nhập
            LoginResponse response = authService.login(
                    request.getUsername(),
                    request.getPassword()
            );

            // Đăng nhập thành công
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            // Dữ liệu đầu vào không hợp lệ
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (RuntimeException e) {

            // Tài khoản không hợp lệ hoặc xác thực thất bại
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());

        } catch (Exception e) {

            // Lỗi hệ thống ngoài dự kiến
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body("Đã xảy ra lỗi trong quá trình đăng nhập");
        }
    }

    /**
     * Quên mật khẩu - bước 1: gửi mã OTP đặt lại mật khẩu qua email.
     *
     * Luôn trả cùng một thông báo dù email có tồn tại hay không,
     * để không lộ email nào đã đăng ký.
     *
     * Endpoint: POST /api/auth/forgot-password
     * Body JSON: { "email": "..." }
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestReset(request.getEmail());

        return ResponseEntity.ok(Map.of(
                "message",
                "Nếu email tồn tại trong hệ thống, "
                + "mã đặt lại mật khẩu đã được gửi. "
                + "Mã có hiệu lực trong 5 phút."
        ));
    }

    /**
     * Quên mật khẩu - bước 2: nhập OTP và mật khẩu mới.
     *
     * Endpoint: POST /api/auth/reset-password
     * Body JSON: { "email": "...", "code": "123456",
     *              "newPassword": "..." }
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(
                request.getEmail(),
                request.getCode(),
                request.getNewPassword()
        );

        return ResponseEntity.ok(Map.of(
                "message",
                "Đặt lại mật khẩu thành công. "
                + "Vui lòng đăng nhập bằng mật khẩu mới."
        ));
    }

    /**
     * Lấy thông tin người dùng đang đăng nhập (role + permissions mới nhất).
     * Yêu cầu JWT hợp lệ (đã được bảo vệ bởi anyRequest().authenticated()).
     *
     * Endpoint: GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {

        if (authentication == null
                || authentication.getName() == null) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Chưa đăng nhập");
        }

        try {
            return ResponseEntity.ok(
                    authService.getCurrentUser(
                            authentication.getName()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (RuntimeException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        }
    }
}