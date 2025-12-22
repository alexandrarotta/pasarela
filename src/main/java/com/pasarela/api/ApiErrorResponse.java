package com.pasarela.api;

public record ApiErrorResponse(
        String error,
        String message,
        String requestId
) {}

