package com.pasarela.infrastructure.provider;

import com.pasarela.domain.model.PaymentProvider;

public interface PaymentProviderAdapter {
    PaymentProvider provider();

    CreateSessionResult createSession(CreateSessionCommand command);

    RefundResult refund(RefundCommand command);
}

