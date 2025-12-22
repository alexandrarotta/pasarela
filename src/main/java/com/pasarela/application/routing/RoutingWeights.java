package com.pasarela.application.routing;

public record RoutingWeights(
        double w1SuccessRate,
        double w2CostScore,
        double w3LatencyScore,
        double w4AvailabilityScore,
        double w5RiskPenalty
) {
    public static RoutingWeights defaults() {
        return new RoutingWeights(0.55, 0.15, 0.15, 0.10, 0.05);
    }
}

