package com.pasarela.domain.security;

import java.util.UUID;

public record MerchantPrincipal(UUID merchantId, String name) {}

