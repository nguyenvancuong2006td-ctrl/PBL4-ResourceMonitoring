package com.pbl4.server.dto.request;

import com.pbl4.server.util.PasswordPolicy;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Yêu cầu đặt lại mật khẩu bằng mã OTP đã gửi qua email.
 */
public class ResetPasswordRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Mã xác thực không được để trống")
    @Pattern(regexp = "^\\d{6}$",
            message = "Mã xác thực gồm đúng 6 chữ số")
    private String code;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = PasswordPolicy.LENGTH_MESSAGE)
    @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEX,
            message = PasswordPolicy.COMPLEXITY_MESSAGE)
    private String newPassword;

    public ResetPasswordRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim();
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}