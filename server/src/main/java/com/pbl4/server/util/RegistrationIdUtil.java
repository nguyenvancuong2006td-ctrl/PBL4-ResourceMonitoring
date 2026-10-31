package com.pbl4.server.util;

import java.util.Collection;
import java.util.Locale;

/**
 * Quy ước đặt ID cho Client và Agent khi Agent đăng ký.
 *
 *   Client: CLIENT-001, CLIENT-002, ...
 *   Agent : AGENT-001,  AGENT-002,  ...
 *
 * Client và Agent của cùng một lần đăng ký luôn dùng cùng số thứ tự.
 * Từ số 1000 trở đi ID dài thêm một chữ số (CLIENT-1000).
 */
public final class RegistrationIdUtil {

    public static final String CLIENT_PREFIX = "CLIENT-";

    public static final String AGENT_PREFIX = "AGENT-";

    // Số chữ số tối thiểu (đệm số 0 phía trước)
    private static final int MIN_DIGITS = 3;

    // Bỏ qua các ID có phần số quá dài để không tràn kiểu long
    private static final int MAX_DIGITS_PARSED = 9;

    private RegistrationIdUtil() {
    }

    /**
     * Số thứ tự lớn nhất trong các ID có dạng PREFIX + chữ số.
     * Các ID không đúng dạng (ví dụ CLI-uuid cũ) bị bỏ qua.
     *
     * @return 0 nếu chưa có ID nào đúng dạng
     */
    public static long highestNumber(
            Collection<String> ids,
            String prefix) {

        long highest = 0;

        if (ids == null) {
            return highest;
        }

        for (String id : ids) {

            if (id == null || !id.startsWith(prefix)) {
                continue;
            }

            String digits = id.substring(prefix.length());

            if (digits.isEmpty()
                    || digits.length() > MAX_DIGITS_PARSED
                    || !isAsciiDigits(digits)) {
                continue;
            }

            highest = Math.max(highest, Long.parseLong(digits));
        }

        return highest;
    }

    /**
     * Ghép tiền tố với số thứ tự, ví dụ format("AGENT-", 7) = "AGENT-007".
     */
    public static String format(String prefix, long number) {

        if (number < 1) {
            throw new IllegalArgumentException(
                    "Số thứ tự phải lớn hơn 0."
            );
        }

        return prefix + String.format(
                Locale.ROOT,
                "%0" + MIN_DIGITS + "d",
                number
        );
    }

    private static boolean isAsciiDigits(String value) {

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (c < '0' || c > '9') {
                return false;
            }
        }

        return true;
    }
}