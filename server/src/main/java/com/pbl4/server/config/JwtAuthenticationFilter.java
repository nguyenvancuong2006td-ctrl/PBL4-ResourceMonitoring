
package com.pbl4.server.config;

import com.pbl4.server.enums.AccountStatus;
import com.pbl4.server.model.Account;
import com.pbl4.server.model.Permission;
import com.pbl4.server.service.AccountDetailsService;
import com.pbl4.server.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AccountDetailsService accountDetailsService;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            AccountDetailsService accountDetailsService) {

        this.jwtUtil = jwtUtil;
        this.accountDetailsService = accountDetailsService;
    }

    // ==================================================
    // 1. XỬ LÝ XÁC THỰC JWT
    // ==================================================

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // Lấy Authorization Header
        String authHeader = request.getHeader(
                HttpHeaders.AUTHORIZATION
        );

        // --------------------------------------------------
        // 1.1. Không có JWT
        // --------------------------------------------------

        // Cho request đi tiếp.
        // Agent có thể sử dụng Agent Credential riêng
        // mà không cần JWT của tài khoản người dùng.
        if (authHeader == null || authHeader.isBlank()) {

            filterChain.doFilter(request, response);
            return;
        }

        // --------------------------------------------------
        // 1.2. Kiểm tra định dạng Bearer Token
        // --------------------------------------------------

        // Authorization Header có giá trị nhưng không đúng
        // định dạng Bearer thì từ chối request.
        if (authHeader.length() < BEARER_PREFIX.length()
                || !authHeader.regionMatches(
                        true,
                        0,
                        BEARER_PREFIX,
                        0,
                        BEARER_PREFIX.length())) {

            rejectUnauthorized(
                    response,
                    "Authorization Header không hợp lệ"
            );
            return;
        }

        // --------------------------------------------------
        // 1.3. Lấy JWT
        // --------------------------------------------------

        String token = authHeader
                .substring(BEARER_PREFIX.length())
                .trim();

        if (token.isEmpty()) {

            rejectUnauthorized(
                    response,
                    "JWT không được để trống"
            );
            return;
        }

        // --------------------------------------------------
        // 1.4. Kiểm tra tính hợp lệ của JWT
        // --------------------------------------------------

        try {

            if (!jwtUtil.isTokenValid(token)) {

                rejectUnauthorized(
                        response,
                        "JWT không hợp lệ hoặc đã hết hạn"
                );
                return;
            }

        } catch (RuntimeException e) {

            rejectUnauthorized(
                    response,
                    "Không thể xác thực JWT"
            );
            return;
        }

        // --------------------------------------------------
        // 1.5. Lấy username từ JWT
        // --------------------------------------------------

        final String username;

        try {

            username = jwtUtil.extractUsername(token);

            if (username == null || username.isBlank()) {

                rejectUnauthorized(
                        response,
                        "JWT không chứa username hợp lệ"
                );
                return;
            }

        } catch (RuntimeException e) {

            rejectUnauthorized(
                    response,
                    "Không thể đọc thông tin JWT"
            );
            return;
        }

        // ==================================================
        // 2. KIỂM TRA TÀI KHOẢN
        // ==================================================

        // --------------------------------------------------
        // 2.1. Lấy tài khoản từ Database
        // --------------------------------------------------

        final Account account;

        try {

            account = accountDetailsService
                    .getAccountByUsername(username);

        } catch (RuntimeException e) {

            // Lỗi truy vấn Database hoặc tải dữ liệu tài khoản
            // không được xem là JWT hợp lệ.
            SecurityContextHolder.clearContext();

            throw new ServletException(
                    "Không thể truy xuất thông tin tài khoản",
                    e
            );
        }

        if (account == null) {

            rejectUnauthorized(
                    response,
                    "Tài khoản không tồn tại"
            );
            return;
        }

        // --------------------------------------------------
        // 2.2. Kiểm tra trạng thái tài khoản
        // --------------------------------------------------

        if (account.getStatus() != AccountStatus.ACTIVE) {

            rejectUnauthorized(
                    response,
                    "Tài khoản chưa được kích hoạt hoặc đã bị khóa"
            );
            return;
        }

        // --------------------------------------------------
        // 2.3. Kiểm tra email đã xác thực
        // --------------------------------------------------

        if (!Boolean.TRUE.equals(account.getEmailVerified())) {

            rejectUnauthorized(
                    response,
                    "Email chưa được xác thực"
            );
            return;
        }

        // --------------------------------------------------
        // 2.4. Kiểm tra vai trò tài khoản
        // --------------------------------------------------

        if (account.getRole() == null
                || account.getRole().getRoleName() == null) {

            rejectUnauthorized(
                    response,
                    "Tài khoản chưa được gán vai trò"
            );
            return;
        }

        // ==================================================
        // 3. TẠO DANH SÁCH QUYỀN HẠN
        // ==================================================

        Set<SimpleGrantedAuthority> authorities =
                new LinkedHashSet<>();

        // --------------------------------------------------
        // 3.1. Thêm Role Authority
        // --------------------------------------------------

        // Ví dụ:
        // ROLE_ADMIN
        // ROLE_MONITOR
        // ROLE_STAFF

        String roleName = account.getRole()
                .getRoleName()
                .name();

        authorities.add(
                new SimpleGrantedAuthority(
                        "ROLE_" + roleName
                )
        );

        // --------------------------------------------------
        // 3.2. Thêm Permission
        // --------------------------------------------------

        // Lấy các Permission được gán cho Role.
        // Ví dụ:
        // CLIENT_VIEW
        // CLIENT_MANAGE
        // AGENT_VIEW
        // AGENT_MANAGE

        if (account.getRole().getPermissions() != null) {

            for (Permission permission :
                    account.getRole().getPermissions()) {

                if (permission == null
                        || permission.getPermissionCode() == null) {

                    continue;
                }

                authorities.add(
                        new SimpleGrantedAuthority(
                                permission.getPermissionCode().name()
                        )
                );
            }
        }

        // ==================================================
        // 4. TẠO AUTHENTICATION
        // ==================================================

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                );

        // Lưu địa chỉ IP của request
        authentication.setDetails(
                request.getRemoteAddr()
        );

        // ==================================================
        // 5. LƯU VÀO SECURITY CONTEXT
        // ==================================================

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);

        SecurityContextHolder.setContext(securityContext);

        // ==================================================
        // 6. CHUYỂN REQUEST ĐẾN FILTER TIẾP THEO
        // ==================================================

        filterChain.doFilter(request, response);
    }

    // ==================================================
    // 7. XỬ LÝ KHI XÁC THỰC THẤT BẠI
    // ==================================================

    private void rejectUnauthorized(
            HttpServletResponse response,
            String message) throws IOException {

        // Xóa thông tin xác thực hiện tại
        SecurityContextHolder.clearContext();

        if (response.isCommitted()) {
            return;
        }

        // Trả về HTTP 401 Unauthorized
        response.sendError(
                HttpStatus.UNAUTHORIZED.value(),
                message
        );
    }
}
