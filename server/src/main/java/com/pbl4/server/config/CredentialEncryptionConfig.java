package com.pbl4.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class CredentialEncryptionConfig {

    @Bean
    public SecretKey credentialEncryptionKey(
            @Value("${app.security.credential-encryption-key}")
            String encodedKey) {

        byte[] keyBytes;

        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Credential encryption key phải được mã hóa Base64.",
                    e
            );
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "AES-256 yêu cầu khóa có độ dài chính xác 32 byte."
            );
        }

        return new SecretKeySpec(keyBytes, "AES");
    }
}