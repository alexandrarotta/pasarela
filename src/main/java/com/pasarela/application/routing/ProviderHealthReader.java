package com.pasarela.application.routing;

import com.pasarela.domain.model.PaymentProvider;

public interface ProviderHealthReader {
    ProviderSnapshot getSnapshot(PaymentProvider provider);
}

