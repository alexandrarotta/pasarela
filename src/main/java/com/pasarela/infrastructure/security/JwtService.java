package com.pasarela.infrastructure.security;

import com.pasarela.config.AppProperties;
import com.pasarela.domain.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 chars");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = properties.jwt().ttlSeconds();
    }

    public String mint(String email, UserRole role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role.name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public JwtPrincipal parseAndValidate(String token) {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            Claims claims = jws.getBody();
            String email = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            return new JwtPrincipal(email, UserRole.valueOf(roleStr));
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid token");
        }
    }
}

