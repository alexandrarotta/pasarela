package com.pasarela.config;

import com.pasarela.infrastructure.crypto.AesGcmCrypto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Base64;

@Configuration
public class CryptoConfig {
    @Bean
    public AesGcmCrypto aesGcmCrypto(AppProperties properties) {
        String keyBase64 = properties.crypto() == null ? null : properties.crypto().encryptionKeyBase64();
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalStateException("Missing APP_ENCRYPTION_KEY_BASE64");
        }
        byte[] key = Base64.getDecoder().decode(keyBase64);
        return new AesGcmCrypto(key);
    }
}

