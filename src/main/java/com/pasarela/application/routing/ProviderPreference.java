package com.pasarela.application.routing;

import com.pasarela.domain.model.PaymentProvider;

public enum ProviderPreference {
    AUTO,
    STRIPE,
    ADYEN;

    public PaymentProvider toProvider() {
        return switch (this) {
            case STRIPE -> PaymentProvider.STRIPE;
            case ADYEN -> PaymentProvider.ADYEN;
            case AUTO -> throw new IllegalStateException("AUTO is not a provider");
        };
    }
}

