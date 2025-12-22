package com.pasarela.infrastructure.provider;

import java.util.Map;

public record CreateSessionResult(
        String providerRef,
        Map<String, Object> checkoutConfig
) {}

