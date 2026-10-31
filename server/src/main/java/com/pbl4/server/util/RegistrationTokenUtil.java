package com.pbl4.server.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RegistrationTokenUtil {

    private RegistrationTokenUtil() {
    }

    public static String hash(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "Registration Token không được để trống."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Không hỗ trợ thuật toán SHA-256.", e
            );
        }
    }
}