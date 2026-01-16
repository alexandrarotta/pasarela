package com.pasarela.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String path = request.getRequestURI();
        String message = path != null && path.startsWith("/api/payment-intents")
                ? "Invalid X-Api-Key"
                : "Unauthorized";

        ApiErrorResponse body = new ApiErrorResponse("UNAUTHORIZED", message, requestId);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
