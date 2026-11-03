package com.pbl4.server.util;

/**
 * Quy tắc mật khẩu dùng chung cho đăng ký và đặt lại mật khẩu.
 *
 * Chỉ cho phép ký tự ASCII hiển thị được (không dấu, không khoảng trắng)
 * vì BCrypt chỉ xử lý tối đa 72 byte; ký tự có dấu chiếm nhiều byte
 * nên dễ vượt giới hạn này.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    public static final String LENGTH_MESSAGE =
            "Mật khẩu phải từ 8 đến 64 ký tự";

    // Ít nhất 1 chữ cái + 1 chữ số, chỉ gồm ASCII hiển thị được
    public static final String COMPLEXITY_REGEX =
            "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$";

    public static final String COMPLEXITY_MESSAGE =
            "Mật khẩu phải có ít nhất một chữ cái và một chữ số, "
            + "không chứa dấu hoặc khoảng trắng";

    private PasswordPolicy() {
    }
}