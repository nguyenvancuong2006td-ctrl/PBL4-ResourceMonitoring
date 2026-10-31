package com.pbl4.server.controller;

import com.pbl4.server.dto.request.LoginRequest;
import com.pbl4.server.dto.response.LoginResponse;
import com.pbl4.server.service.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * API đăng nhập.
     *
     * Nhận username và password từ request body dạng JSON.
     * Nếu đăng nhập thành công, trả về JWT cùng thông tin tài khoản.
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
}