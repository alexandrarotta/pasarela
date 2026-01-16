package com.pasarela.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {
    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(resolveAllowedOrigins(appProperties.frontend().baseUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Api-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> resolveAllowedOrigins(String frontendBaseUrl) {
        List<String> defaults = List.of("http://localhost:3000", "http://127.0.0.1:3000", "http://[::1]:3000");
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) return defaults;

        URI uri;
        try {
            uri = URI.create(frontendBaseUrl.trim());
        } catch (IllegalArgumentException e) {
            return defaults;
        }

        if (uri.getScheme() == null || uri.getHost() == null) return defaults;

        String scheme = uri.getScheme();
        int port = uri.getPort();
        String portPart = port == -1 ? "" : ":" + port;

        String host = uri.getHost();

        List<String> origins = new ArrayList<>();
        origins.add(origin(scheme, host, portPart));

        if (isLocalHost(host)) {
            origins.add(origin(scheme, "localhost", portPart));
            origins.add(origin(scheme, "127.0.0.1", portPart));
            origins.add(origin(scheme, "::1", portPart));
        }

        return origins.stream().distinct().toList();
    }

    private static String origin(String scheme, String host, String portPart) {
        String renderedHost = host.contains(":") ? "[" + host + "]" : host;
        return scheme + "://" + renderedHost + portPart;
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }
}
