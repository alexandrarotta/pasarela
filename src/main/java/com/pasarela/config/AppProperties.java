package com.pasarela.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Frontend frontend,
        Jwt jwt,
        Crypto crypto,
        Providers providers
) {
    public record Frontend(String baseUrl) {}

    public record Jwt(String secret, long ttlSeconds) {}

    public record Crypto(String encryptionKeyBase64) {}

    public record Providers(Stripe stripe, Adyen adyen) {
        public record Stripe(String secretKey, String publishableKey, String webhookSecret) {}

        public record Adyen(
                String apiKey,
                String merchantAccount,
                String clientKey,
                String hmacKey,
                String environment
        ) {}
    }
}

