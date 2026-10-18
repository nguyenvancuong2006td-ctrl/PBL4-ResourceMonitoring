package com.pbl4.server.controller;

import com.pbl4.server.dto.response.LoginResponse;
import com.pbl4.server.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestParam String username,
            @RequestParam String password) {

        LoginResponse response =
                authService.login(
                        username,
                        password
                );

        return ResponseEntity.ok(response);
    }
}