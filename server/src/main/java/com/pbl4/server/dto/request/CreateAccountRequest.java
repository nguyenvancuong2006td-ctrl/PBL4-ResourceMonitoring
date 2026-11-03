package com.pbl4.server.dto.request;

import com.pbl4.server.enums.RoleName;
import com.pbl4.server.util.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * DTO dành cho ADMIN tạo tài khoản.
 *
 * Tài khoản sau khi tạo sẽ ở trạng thái PENDING
 * và cần xác thực email bằng mã OTP.
 */
public class CreateAccountRequest {

    @NotBlank(message = "Username không được để trống")
    @Size(
            min = 3,
            max = 50,
            message = "Username phải từ 3 đến 50 ký tự"
    )
    @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "Username chỉ gồm chữ, số, dấu chấm, gạch dưới và gạch ngang"
    )
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(
            min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = PasswordPolicy.LENGTH_MESSAGE
    )
    @Pattern(
            regexp = PasswordPolicy.COMPLEXITY_REGEX,
            message = PasswordPolicy.COMPLEXITY_MESSAGE
    )
    private String password;

    @NotNull(message = "Vai trò không được để trống")
    private RoleName roleName;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public RoleName getRoleName() {
        return roleName;
    }

    public void setRoleName(RoleName roleName) {
        this.roleName = roleName;
    }
}