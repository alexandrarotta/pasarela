package com.pasarela.config;

import com.pasarela.infrastructure.security.JwtPrincipal;
import com.pasarela.infrastructure.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/payment-intents");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            if (!token.isEmpty()) {
                try {
                    JwtPrincipal principal = jwtService.parseAndValidate(token);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal.email(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (IllegalArgumentException ignored) {
                    // leave unauthenticated
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}

