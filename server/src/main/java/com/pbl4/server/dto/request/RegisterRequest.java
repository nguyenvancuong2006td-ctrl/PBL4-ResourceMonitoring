package com.pbl4.server.dto.request;

import com.pbl4.server.util.PasswordPolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Yêu cầu đăng ký tài khoản.
 *
 * Cố ý KHÔNG có trường role: mọi tài khoản tự đăng ký luôn là MONITOR,
 * role do server quyết định. Trường lạ trong JSON (kể cả "role")
 * không có tác dụng.
 */
public class RegisterRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(min = 3, max = 50,
            message = "Username phải từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "Username chỉ gồm chữ, số, dấu chấm, "
                    + "gạch dưới và gạch ngang")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = PasswordPolicy.LENGTH_MESSAGE)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEX,
            message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String password;

    public RegisterRequest() {
    }

    public String getUsername() {
        return username;
    }

    // Bỏ khoảng trắng thừa trước khi validate
    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public String getEmail() {
        return email;
    }

    // Email không phân biệt hoa thường: chuẩn hóa về chữ thường
    public void setEmail(String email) {
        this.email = email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getPassword() {
        return password;
    }

    // Không trim mật khẩu
    public void setPassword(String password) {
        this.password = password;
    }
}