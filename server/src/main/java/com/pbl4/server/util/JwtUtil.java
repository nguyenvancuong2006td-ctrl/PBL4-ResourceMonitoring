
package com.pbl4.server.util;

import com.pbl4.server.model.Account;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long DEFAULT_EXPIRATION_TIME =
            24L * 60 * 60 * 1000; // 24 giờ

    private final SecretKey signingKey;
    private final long expirationTime;

    /**
     * Khởi tạo JwtUtil và kiểm tra cấu hình JWT.
     */
    public JwtUtil(
            @Value("${app.jwt.secret}") String secretKey,
            @Value("${app.jwt.expiration:86400000}") long expirationTime) {

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT Secret Key không được để trống"
            );
        }

        if (secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT Secret Key phải có ít nhất 32 byte"
            );
        }

        if (expirationTime <= 0) {
            throw new IllegalArgumentException(
                    "Thời hạn JWT phải lớn hơn 0"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(
                secretKey.getBytes(StandardCharsets.UTF_8)
        );

        this.expirationTime = expirationTime;
    }

    /**
     * Tạo JWT cho tài khoản sau khi đăng nhập thành công.
     */
    public String generateToken(Account account) {

        if (account == null) {
            throw new IllegalArgumentException(
                    "Tài khoản không được để trống"
            );
        }

        if (account.getUsername() == null
                || account.getUsername().isBlank()) {
            throw new IllegalArgumentException(
                    "Username không được để trống"
            );
        }

        if (account.getRole() == null
                || account.getRole().getRoleName() == null) {
            throw new IllegalArgumentException(
                    "Tài khoản chưa được gán vai trò"
            );
        }

        Date issuedAt = new Date();
        Date expiration = new Date(
                issuedAt.getTime() + expirationTime
        );

        return Jwts.builder()
                .subject(account.getUsername().trim())
                .claim(
                        "role",
                        account.getRole().getRoleName().name()
                )
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Lấy username từ JWT sau khi xác thực chữ ký.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Lấy vai trò từ JWT sau khi xác thực chữ ký.
     */
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    /**
     * Kiểm tra JWT có hợp lệ và còn thời hạn hay không.
     */
    public boolean isTokenValid(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            Claims claims = extractClaims(token);

            String username = claims.getSubject();
            Date expiration = claims.getExpiration();

            return username != null
                    && !username.isBlank()
                    && expiration != null
                    && expiration.after(new Date());

        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Phân tích JWT và xác thực chữ ký.
     */
    private Claims extractClaims(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT không được để trống"
            );
        }

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
