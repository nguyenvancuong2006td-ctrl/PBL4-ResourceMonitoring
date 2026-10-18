package com.pbl4.server.config;

import com.pbl4.server.model.Account;
import com.pbl4.server.model.Permission;
import com.pbl4.server.service.AccountDetailsService;
import com.pbl4.server.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AccountDetailsService accountDetailsService;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            AccountDetailsService accountDetailsService) {

        this.jwtUtil = jwtUtil;
        this.accountDetailsService = accountDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        // Không có Authorization header
        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        // Lấy JWT token
        String token = authHeader.substring(7);

        // Kiểm tra token
        if (!jwtUtil.isTokenValid(token)) {

            filterChain.doFilter(request, response);
            return;
        }

        // Lấy username từ JWT
        String username =
                jwtUtil.extractUsername(token);

        // Lấy Account từ database
        Account account;

        try {

            account =
                    accountDetailsService
                            .getAccountByUsername(username);

        } catch (RuntimeException e) {

            filterChain.doFilter(request, response);
            return;
        }

        // Danh sách quyền
        List<SimpleGrantedAuthority> authorities =
                new ArrayList<>();

        // Thêm Role
        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_" +
                        account.getRole()
                                .getRoleName()
                                .name()
                )
        );

        // Thêm Permission
        for (Permission permission :
                account.getRole().getPermissions()) {

            authorities.add(
                    new SimpleGrantedAuthority(
                            permission
                                    .getPermissionCode()
                                    .name()
                    )
            );
        }

        // Tạo Authentication
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                );

        // Lưu Authentication
        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        // Cho request đi tiếp
        filterChain.doFilter(request, response);
    }
}