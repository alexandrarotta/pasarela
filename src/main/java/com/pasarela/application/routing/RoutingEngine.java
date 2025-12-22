package com.pasarela.application.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.domain.model.CircuitState;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RoutingEngine {
    private static final Set<String> STRIPE_CURRENCIES = Set.of("USD", "EUR", "GBP");
    private static final Set<String> ADYEN_CURRENCIES = Set.of("USD", "EUR", "MXN");

    private final ProviderHealthReader providerHealthReader;
    private final ObjectMapper objectMapper;

    public RoutingEngine(ProviderHealthReader providerHealthReader, ObjectMapper objectMapper) {
        this.providerHealthReader = providerHealthReader;
        this.objectMapper = objectMapper;
    }

    public RoutingResult decide(
            MerchantEntity merchant,
            UUID paymentIntentId,
            long amountMinor,
            String currency,
            ProviderPreference preference,
            Set<PaymentProvider> excludedProviders
    ) {
        String cur = currency == null ? "" : currency.toUpperCase();
        RoutingConfig merchantConfig = parseConfig(merchant.getConfigJson());

        if (preference != null && preference != ProviderPreference.AUTO) {
            PaymentProvider chosen = preference.toProvider();
            ensureHardConstraints(chosen, cur);
            return buildResult(paymentIntentId, amountMinor, cur, merchantConfig, chosen, "EXPLICIT_PREFERENCE");
        }

        if (preference == null || preference == ProviderPreference.AUTO) {
            if (merchantConfig.forceProvider() != null && !"AUTO".equalsIgnoreCase(merchantConfig.forceProvider())) {
                PaymentProvider forced = PaymentProvider.valueOf(merchantConfig.forceProvider().toUpperCase());
                ensureHardConstraints(forced, cur);
                return buildResult(paymentIntentId, amountMinor, cur, merchantConfig, forced, "MERCHANT_FORCE_PROVIDER");
            }
        }

        List<PaymentProvider> candidates = List.of(PaymentProvider.STRIPE, PaymentProvider.ADYEN).stream()
                .filter(p -> excludedProviders == null || !excludedProviders.contains(p))
                .filter(p -> supportsCurrency(p, cur))
                .toList();

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No provider supports currency " + cur);
        }

        Map<PaymentProvider, ProviderSnapshot> snapshots = new EnumMap<>(PaymentProvider.class);
        for (PaymentProvider provider : candidates) {
            snapshots.put(provider, providerHealthReader.getSnapshot(provider));
        }

        List<PaymentProvider> nonOpen = candidates.stream()
                .filter(p -> snapshots.get(p).circuitState() != CircuitState.OPEN)
                .toList();
        List<PaymentProvider> effectiveCandidates = nonOpen.isEmpty() ? candidates : nonOpen;

        EnumMap<PaymentProvider, ScoreBreakdown> breakdowns = new EnumMap<>(PaymentProvider.class);
        for (PaymentProvider provider : effectiveCandidates) {
            breakdowns.put(provider, score(provider, amountMinor, merchantConfig, snapshots.get(provider)));
        }

        PaymentProvider chosen = chooseBest(paymentIntentId, effectiveCandidates, breakdowns);

        String reason = nonOpen.isEmpty()
                ? "HEALTH_DEGRADED_NO_ALTERNATIVE"
                : "WEIGHTED_SCORE";

        return toResult(chosen, reason, breakdowns, snapshots, merchantConfig, amountMinor, cur);
    }

    private RoutingResult buildResult(UUID paymentIntentId, long amountMinor, String currency, RoutingConfig cfg, PaymentProvider chosen, String reason) {
        EnumMap<PaymentProvider, ProviderSnapshot> snapshots = new EnumMap<>(PaymentProvider.class);
        snapshots.put(PaymentProvider.STRIPE, providerHealthReader.getSnapshot(PaymentProvider.STRIPE));
        snapshots.put(PaymentProvider.ADYEN, providerHealthReader.getSnapshot(PaymentProvider.ADYEN));

        EnumMap<PaymentProvider, ScoreBreakdown> breakdowns = new EnumMap<>(PaymentProvider.class);
        for (PaymentProvider p : PaymentProvider.values()) {
            if (supportsCurrency(p, currency)) breakdowns.put(p, score(p, amountMinor, cfg, snapshots.get(p)));
        }
        return toResult(chosen, reason, breakdowns, snapshots, cfg, amountMinor, currency);
    }

    private RoutingResult toResult(
            PaymentProvider chosen,
            String reasonCode,
            Map<PaymentProvider, ScoreBreakdown> breakdowns,
            Map<PaymentProvider, ProviderSnapshot> snapshots,
            RoutingConfig cfg,
            long amountMinor,
            String currency
    ) {
        Map<String, Object> candidateScores = new HashMap<>();
        for (Map.Entry<PaymentProvider, ScoreBreakdown> entry : breakdowns.entrySet()) {
            PaymentProvider p = entry.getKey();
            ScoreBreakdown b = entry.getValue();
            ProviderSnapshot snap = snapshots.get(p);

            Map<String, Object> inputs = new HashMap<>();
            if (snap != null) {
                inputs.put("successRate", snap.successRate());
                inputs.put("errorRate", snap.errorRate());
                inputs.put("p95LatencyMs", snap.p95LatencyMs());
                inputs.put("circuitState", snap.circuitState().name());
            }
            inputs.put("costScore", b.costScore());
            inputs.put("latencyScore", b.latencyScore());
            inputs.put("availabilityScore", b.availabilityScore());
            inputs.put("riskPenalty", b.riskPenalty());

            candidateScores.put(p.name(), Map.of(
                    "score", b.totalScore(),
                    "inputs", inputs
            ));
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of(
                    "currency", currency,
                    "amountMinor", amountMinor,
                    "weights", cfg.weights(),
                    "candidates", candidateScores,
                    "computedAt", Instant.now().toString()
            ));
        } catch (Exception e) {
            json = "{\"error\":\"candidateScoresJson_failed\"}";
        }

        return new RoutingResult(chosen, reasonCode, json);
    }

    private PaymentProvider chooseBest(UUID paymentIntentId, List<PaymentProvider> candidates, Map<PaymentProvider, ScoreBreakdown> breakdowns) {
        if (candidates.size() == 1) return candidates.getFirst();
        PaymentProvider best = candidates.getFirst();
        double bestScore = breakdowns.get(best).totalScore();
        for (PaymentProvider p : candidates) {
            double s = breakdowns.get(p).totalScore();
            if (s > bestScore) {
                best = p;
                bestScore = s;
            }
        }

        // Stable tie-breaker
        PaymentProvider finalBest = best;
        PaymentProvider other = candidates.stream().filter(p -> p != finalBest).findFirst().orElse(finalBest);
        double otherScore = breakdowns.get(other).totalScore();
        if (Math.abs(bestScore - otherScore) < 1e-6) {
            int bucket = Math.floorMod(fnv1a32(paymentIntentId.toString()), 2);
            return bucket == 0 ? PaymentProvider.STRIPE : PaymentProvider.ADYEN;
        }
        return best;
    }

    private ScoreBreakdown score(PaymentProvider provider, long amountMinor, RoutingConfig cfg, ProviderSnapshot snapshot) {
        RoutingWeights w = cfg.weights() == null ? RoutingWeights.defaults() : cfg.weights();

        double successRate = snapshot == null ? 0 : clamp01(snapshot.successRate());
        double costScore = clamp01(cfg.costModel().getOrDefault(provider, 0.3));
        double latencyScore = snapshot == null ? 0 : clamp01(snapshot.p95LatencyMs() / 2000.0);
        double availabilityScore = snapshot == null ? 1 : switch (snapshot.circuitState()) {
            case CLOSED -> 1.0;
            case HALF_OPEN -> 0.5;
            case OPEN -> 0.0;
        };
        double riskPenalty = amountMinor >= 200_00 ? 0.25 : (amountMinor >= 1_000_00 ? 0.10 : 0.0);

        double score =
                (w.w1SuccessRate() * successRate)
                        - (w.w2CostScore() * costScore)
                        - (w.w3LatencyScore() * latencyScore)
                        + (w.w4AvailabilityScore() * availabilityScore)
                        - (w.w5RiskPenalty() * riskPenalty);

        return new ScoreBreakdown(score, costScore, latencyScore, availabilityScore, riskPenalty);
    }

    private RoutingConfig parseConfig(String json) {
        try {
            if (json == null || json.isBlank()) return RoutingConfig.defaults();
            RoutingConfig cfg = objectMapper.readValue(json, RoutingConfig.class);
            if (cfg == null) return RoutingConfig.defaults();
            if (cfg.weights() == null) cfg = new RoutingConfig(cfg.forceProvider(), RoutingWeights.defaults(), cfg.costModel());
            if (cfg.costModel() == null || cfg.costModel().isEmpty()) cfg = new RoutingConfig(cfg.forceProvider(), cfg.weights(), RoutingConfig.defaults().costModel());
            return cfg;
        } catch (Exception e) {
            return RoutingConfig.defaults();
        }
    }

    private void ensureHardConstraints(PaymentProvider provider, String currency) {
        if (!supportsCurrency(provider, currency)) {
            throw new IllegalArgumentException("Provider " + provider + " does not support currency " + currency);
        }
    }

    private boolean supportsCurrency(PaymentProvider provider, String currency) {
        return switch (provider) {
            case STRIPE -> STRIPE_CURRENCIES.contains(currency);
            case ADYEN -> ADYEN_CURRENCIES.contains(currency);
        };
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static int fnv1a32(String s) {
        final int FNV_PRIME = 0x01000193;
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private record ScoreBreakdown(
            double totalScore,
            double costScore,
            double latencyScore,
            double availabilityScore,
            double riskPenalty
    ) {}

    public record RoutingResult(
            PaymentProvider chosenProvider,
            String reasonCode,
            String candidateScoresJson
    ) {}
}
