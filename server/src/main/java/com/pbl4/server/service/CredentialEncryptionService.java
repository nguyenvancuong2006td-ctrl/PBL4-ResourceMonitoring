package com.pbl4.server.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CredentialEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final int GCM_NONCE_LENGTH = 12;

    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    private final SecureRandom secureRandom;

    public CredentialEncryptionService(SecretKey credentialEncryptionKey) {
        this.secretKey = credentialEncryptionKey;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Mã hóa Credential bằng AES-256-GCM.
     */
    public String encrypt(String plainText) {

        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException(
                    "Credential không được để trống."
            );
        }

        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedData = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            ByteBuffer buffer = ByteBuffer.allocate(
                    nonce.length + encryptedData.length
            );

            buffer.put(nonce);
            buffer.put(encryptedData);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Không thể mã hóa Credential.",
                    e
            );
        }
    }

    /**
     * Giải mã Credential.
     */
    public String decrypt(String encryptedText) {

        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalArgumentException(
                    "Dữ liệu mã hóa không được để trống."
            );
        }

        try {
            byte[] encryptedBytes =
                    Base64.getDecoder().decode(encryptedText);

            if (encryptedBytes.length <= GCM_NONCE_LENGTH) {
                throw new IllegalArgumentException(
                        "Dữ liệu mã hóa không hợp lệ."
                );
            }

            ByteBuffer buffer = ByteBuffer.wrap(encryptedBytes);

            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            buffer.get(nonce);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedData = cipher.doFinal(cipherText);

            return new String(
                    decryptedData,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException
                 | IllegalArgumentException e) {

            throw new IllegalStateException(
                    "Không thể giải mã Credential hoặc dữ liệu đã bị thay đổi.",
                    e
            );
        }
    }
}