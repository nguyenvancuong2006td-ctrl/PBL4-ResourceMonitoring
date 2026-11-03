package com.pbl4.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Các origin của frontend được phép gọi API, phân cách bằng dấu phẩy.
     * Mặc định cho phép localhost (React dev server).
     * Khi triển khai, thêm vào application.properties, ví dụ:
     *   app.cors.allowed-origins=https://ten-mien-frontend
     */
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String[] corsAllowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // ==================================================
    // PASSWORD ENCODER
    // ==================================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ==================================================
    // CORS
    // ==================================================

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(corsAllowedOrigins));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type"
        ));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", config);

        return source;
    }

    // ==================================================
    // SECURITY FILTER CHAIN
    // ==================================================

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http

                // Cho phép frontend (origin khác) gọi API
                .cors(cors -> { })

                // REST API không sử dụng CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // Không sử dụng HTTP Basic Authentication
                .httpBasic(AbstractHttpConfigurer::disable)

                // Không sử dụng Form Login mặc định
                .formLogin(AbstractHttpConfigurer::disable)

                // Không sử dụng Logout mặc định
                .logout(AbstractHttpConfigurer::disable)

                // Không lưu Session phía Server
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // ==================================================
                // PHÂN QUYỀN TRUY CẬP API
                // ==================================================

                .authorizeHttpRequests(auth -> auth

                        // ------------------------------------------
                        // 1. ĐĂNG KÝ TÀI KHOẢN
                        // ------------------------------------------

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/accounts/register"
                        ).permitAll()

                        // ------------------------------------------
                        // 2. XÁC THỰC EMAIL / OTP
                        // ------------------------------------------

                        .requestMatchers(
                                "/api/verification/email",
                                "/api/verification/email/send"
                        ).permitAll()

                        // ------------------------------------------
                        // 3. ĐĂNG NHẬP
                        // ------------------------------------------

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // ------------------------------------------
                        // 3b. QUÊN MẬT KHẨU
                        // ------------------------------------------

                        // Người dùng chưa đăng nhập được yêu cầu OTP
                        // và đặt lại mật khẩu mới.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()

                        // ------------------------------------------
                        // 4. AGENT ĐĂNG KÝ VỚI SERVER
                        // ------------------------------------------

                        // Agent đăng ký bằng Registration Token.
                        // Không yêu cầu JWT.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/agent-registration"
                        ).permitAll()

                        // ------------------------------------------
                        // 5. AGENT POLL CREDENTIAL
                        // ------------------------------------------

                        // Agent sử dụng Registration Token
                        // để kiểm tra Credential đã được cấp hay chưa.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/agent-registration/*/credential/poll"
                        ).permitAll()

                        // ------------------------------------------
                        // 6. AGENT ACK CREDENTIAL
                        // ------------------------------------------

                        // Agent xác nhận đã nhận và lưu Credential.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/agent-registration/*/credential/ack"
                        ).permitAll()

                        // ------------------------------------------
                        // 7. AGENT HEARTBEAT
                        // ------------------------------------------

                        // Agent xác thực bằng X-Agent-Credential
                        // tại ClientAgentController.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/client-agents/*/heartbeat"
                        ).permitAll()

                        // ------------------------------------------
                        // 8. AGENT GỬI DỮ LIỆU GIÁM SÁT
                        // ------------------------------------------

                        // Agent phải được xác thực bằng Credential
                        // tại Controller hoặc Service tương ứng.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/monitoring/**"
                        ).permitAll()

                        // ------------------------------------------
                        // 9. AGENT LẤY CÁC LỆNH ĐANG CHỜ
                        // ------------------------------------------

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/remote/commands/agent/*/pending"
                        ).permitAll()

                        // ------------------------------------------
                        // 10. AGENT CHUYỂN LỆNH SANG RUNNING
                        // ------------------------------------------

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/remote/commands/agent/*/*/running"
                        ).permitAll()

                        // ------------------------------------------
                        // 11. AGENT BÁO KẾT QUẢ THỰC THI LỆNH
                        // ------------------------------------------

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/remote/commands/agent/*/*/result"
                        ).permitAll()

                        // ------------------------------------------
                        // 12. WEBSOCKET
                        // ------------------------------------------

                        // Cho phép HTTP Handshake.
                        // Xác thực STOMP được xử lý riêng.
                        .requestMatchers(
                                "/ws",
                                "/ws/**"
                        ).permitAll()

                        // ------------------------------------------
                        // 13. ERROR ENDPOINT
                        // ------------------------------------------

                        .requestMatchers(
                                "/error"
                        ).permitAll()

                        // ------------------------------------------
                        // 14. CÁC API CÒN LẠI
                        // ------------------------------------------

                        // Các API còn lại bắt buộc xác thực JWT.
                        // Phân quyền chi tiết sử dụng @PreAuthorize.
                        .anyRequest().authenticated()
                )

                // ==================================================
                // JWT AUTHENTICATION FILTER
                // ==================================================

                // Kiểm tra JWT trước UsernamePasswordAuthenticationFilter.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}