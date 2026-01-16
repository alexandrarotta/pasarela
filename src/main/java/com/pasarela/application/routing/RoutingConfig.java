package com.pasarela.application.routing;

import com.pasarela.domain.model.PaymentProvider;

import java.util.EnumMap;
import java.util.Map;

public record RoutingConfig(
        String forceProvider,
        RoutingWeights weights,
        Map<PaymentProvider, Double> costModel
) {
    public static RoutingConfig defaults() {
        EnumMap<PaymentProvider, Double> cost = new EnumMap<>(PaymentProvider.class);
        cost.put(PaymentProvider.STRIPE, 0.30);
        cost.put(PaymentProvider.ADYEN, 0.25);
        return new RoutingConfig("AUTO", RoutingWeights.defaults(), cost);
    }
}

