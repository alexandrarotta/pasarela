package com.pasarela.infrastructure.provider;

import com.pasarela.domain.model.PaymentProvider;

import java.util.UUID;

public record CreateSessionCommand(
        UUID merchantId,
        UUID paymentIntentId,
        long amountMinor,
        String currency,
        String description,
        String idempotencyKey,
        String returnUrl,
        PaymentProvider provider
) {}

