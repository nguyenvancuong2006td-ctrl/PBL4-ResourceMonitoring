package com.pbl4.server;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import com.pbl4.server.service.CredentialEncryptionService;

class CredentialEncryptionServiceTest {

    private CredentialEncryptionService createService() {
        // Tạo khóa AES-256 giả lập dành riêng cho kiểm thử
        byte[] keyBytes = new byte[32];

        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) i;
        }

        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        return new CredentialEncryptionService(key);
    }

    @Test
    void encryptAndDecrypt_ShouldReturnOriginalCredential() {
        CredentialEncryptionService service = createService();

        String originalCredential = "PBL4-TEST-CREDENTIAL-123456";

        String encrypted = service.encrypt(originalCredential);
        String decrypted = service.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotEquals(originalCredential, encrypted);
        assertEquals(originalCredential, decrypted);
    }

    @Test
    void encrypt_ShouldGenerateDifferentCiphertextForSameCredential() {
        CredentialEncryptionService service = createService();

        String credential = "PBL4-TEST-CREDENTIAL";

        String encrypted1 = service.encrypt(credential);
        String encrypted2 = service.encrypt(credential);

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void decrypt_ShouldRejectTamperedCiphertext() {
        CredentialEncryptionService service = createService();

        String encrypted = service.encrypt("PBL4-TEST-CREDENTIAL");

        // Thay đổi dữ liệu mã hóa
        byte[] bytes = Base64.getDecoder().decode(encrypted);
        bytes[bytes.length - 1] ^= 1;

        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThrows(
                IllegalStateException.class,
                () -> service.decrypt(tampered)
        );
    }
}