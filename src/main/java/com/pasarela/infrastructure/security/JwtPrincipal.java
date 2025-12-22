package com.pasarela.infrastructure.security;

import com.pasarela.domain.model.UserRole;

public record JwtPrincipal(String email, UserRole role) {}

