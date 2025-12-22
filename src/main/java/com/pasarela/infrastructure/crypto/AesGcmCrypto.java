package com.pasarela.infrastructure.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmCrypto {
    private static final String PREFIX = "v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCrypto(byte[] rawKey32) {
        if (rawKey32 == null || rawKey32.length != 32) {
            throw new IllegalArgumentException("APP_ENCRYPTION_KEY_BASE64 must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(rawKey32, "AES");
    }

    public String encryptToString(byte[] plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            return String.join(
                    ":",
                    PREFIX,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext)
            );
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public byte[] decryptToBytes(String token) {
        try {
            if (token == null) throw new IllegalArgumentException("token is null");
            String[] parts = token.split(":");
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported token format");
            }
            byte[] nonce = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}

