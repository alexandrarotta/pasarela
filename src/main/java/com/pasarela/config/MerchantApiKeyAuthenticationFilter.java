package com.pasarela.config;

import com.pasarela.application.MerchantAuthService;
import com.pasarela.domain.security.MerchantPrincipal;
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
import java.util.Optional;

@Component
public class MerchantApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Api-Key";

    private final MerchantAuthService merchantAuthService;

    public MerchantApiKeyAuthenticationFilter(MerchantAuthService merchantAuthService) {
        this.merchantAuthService = merchantAuthService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/payment-intents");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<MerchantPrincipal> merchant = merchantAuthService.authenticate(apiKey);
            if (merchant.isPresent()) {
                var auth = new UsernamePasswordAuthenticationToken(
                        merchant.get(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}

