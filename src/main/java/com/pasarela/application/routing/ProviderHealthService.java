package com.pasarela.application.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.application.PaymentEventService;
import com.pasarela.application.events.EventTypes;
import com.pasarela.domain.model.CircuitState;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.PaymentEventEntity;
import com.pasarela.infrastructure.persistence.entity.ProviderHealthSnapshotEntity;
import com.pasarela.infrastructure.persistence.repository.PaymentEventRepository;
import com.pasarela.infrastructure.persistence.repository.ProviderHealthSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class ProviderHealthService implements ProviderHealthReader {
    private static final Duration ERROR_WINDOW = Duration.ofMinutes(5);
    private static final Duration SUCCESS_WINDOW = Duration.ofMinutes(15);
    private static final Duration SUCCESS_FALLBACK_WINDOW = Duration.ofHours(24);
    private static final Duration OPEN_TTL = Duration.ofMinutes(2);

    private final ProviderHealthSnapshotRepository snapshotRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentEventService paymentEventService;
    private final ObjectMapper objectMapper;

    public ProviderHealthService(
            ProviderHealthSnapshotRepository snapshotRepository,
            PaymentEventRepository paymentEventRepository,
            PaymentEventService paymentEventService,
            ObjectMapper objectMapper
    ) {
        this.snapshotRepository = snapshotRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.paymentEventService = paymentEventService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProviderSnapshot getSnapshot(PaymentProvider provider) {
        ProviderHealthSnapshotEntity entity = snapshotRepository.findByProvider(provider).orElseGet(() -> {
            ProviderHealthSnapshotEntity e = new ProviderHealthSnapshotEntity();
            e.setProvider(provider);
            e.setCircuitState(CircuitState.CLOSED);
            e.setSuccessRate(0);
            e.setErrorRate(0);
            e.setP95LatencyMs(0);
            return snapshotRepository.save(e);
        });

        CircuitState effective = effectiveCircuitState(entity.getCircuitState(), entity.getLastFailureAt(), Instant.now());
        return new ProviderSnapshot(
                provider,
                effective,
                entity.getSuccessRate(),
                entity.getErrorRate(),
                entity.getP95LatencyMs(),
                entity.getLastFailureAt(),
                entity.getUpdatedAt()
        );
    }

    public List<ProviderSnapshot> getAllSnapshots() {
        return List.of(PaymentProvider.values()).stream().map(this::getSnapshot).toList();
    }

    public void recordCreateSessionOutcome(PaymentProvider provider, java.util.UUID paymentIntentId, boolean success, long latencyMs, String errorType, String payloadForHash) {
        String sanitized = null;
        try {
            sanitized = objectMapper.writeValueAsString(Map.of(
                    "latencyMs", latencyMs,
                    "success", success,
                    "errorType", errorType
            ));
        } catch (Exception ignored) {}

        paymentEventService.record(
                provider,
                paymentIntentId,
                success ? EventTypes.PROVIDER_CREATE_SESSION_SUCCEEDED : EventTypes.PROVIDER_CREATE_SESSION_FAILED,
                payloadForHash,
                sanitized
        );

        recomputeAndPersist(provider, success);
    }

    public void recordPaymentOutcomeFromWebhook(PaymentProvider provider, java.util.UUID paymentIntentId, boolean success, String payloadForHash, String sanitizedPayloadJson) {
        paymentEventService.record(
                provider,
                paymentIntentId,
                success ? EventTypes.PAYMENT_SUCCEEDED : EventTypes.PAYMENT_FAILED,
                payloadForHash,
                sanitizedPayloadJson
        );
        recomputeAndPersist(provider, null);
    }

    public void recordRefundOutcomeFromWebhook(PaymentProvider provider, java.util.UUID paymentIntentId, String payloadForHash, String sanitizedPayloadJson) {
        paymentEventService.record(provider, paymentIntentId, EventTypes.REFUND_SUCCEEDED, payloadForHash, sanitizedPayloadJson);
        recomputeAndPersist(provider, null);
    }

    private void recomputeAndPersist(PaymentProvider provider, Boolean lastCreateSessionSuccess) {
        ProviderHealthSnapshotEntity entity = snapshotRepository.findByProvider(provider).orElseGet(() -> {
            ProviderHealthSnapshotEntity e = new ProviderHealthSnapshotEntity();
            e.setProvider(provider);
            e.setCircuitState(CircuitState.CLOSED);
            e.setSuccessRate(0);
            e.setErrorRate(0);
            e.setP95LatencyMs(0);
            return e;
        });

        Instant now = Instant.now();

        Instant errFrom = now.minus(ERROR_WINDOW);
        List<PaymentEventEntity> recentCreates = paymentEventRepository.findRecentByProviderAndTypes(
                provider,
                List.of(EventTypes.PROVIDER_CREATE_SESSION_SUCCEEDED, EventTypes.PROVIDER_CREATE_SESSION_FAILED),
                errFrom
        );

        long total = recentCreates.size();
        long failures = recentCreates.stream().filter(e -> EventTypes.PROVIDER_CREATE_SESSION_FAILED.equals(e.getEventType())).count();
        double errorRate = total == 0 ? 0 : (double) failures / (double) total;

        long p95 = computeP95LatencyMs(provider, now.minus(SUCCESS_WINDOW));

        double successRate = computePaymentSuccessRate(provider, now.minus(SUCCESS_WINDOW));
        if (Double.isNaN(successRate)) {
            successRate = computePaymentSuccessRate(provider, now.minus(SUCCESS_FALLBACK_WINDOW));
            if (Double.isNaN(successRate)) successRate = entity.getSuccessRate();
        }

        CircuitState nextState = effectiveCircuitState(entity.getCircuitState(), entity.getLastFailureAt(), now);
        boolean shouldOpen = errorRate > 0.20 || lastNCreateSessionAreFailures(provider, 5);

        if (Boolean.TRUE.equals(lastCreateSessionSuccess)) {
            if (nextState == CircuitState.HALF_OPEN || nextState == CircuitState.OPEN) {
                nextState = CircuitState.CLOSED;
                entity.setLastFailureAt(null);
            }
        } else if (Boolean.FALSE.equals(lastCreateSessionSuccess)) {
            if (nextState == CircuitState.HALF_OPEN) {
                nextState = CircuitState.OPEN;
                entity.setLastFailureAt(now);
            } else if (shouldOpen) {
                nextState = CircuitState.OPEN;
                entity.setLastFailureAt(now);
            }
        } else {
            if (shouldOpen) {
                nextState = CircuitState.OPEN;
                entity.setLastFailureAt(now);
            } else if (nextState == CircuitState.OPEN) {
                // keep OPEN until TTL passes
            } else if (nextState == CircuitState.HALF_OPEN) {
                // keep HALF_OPEN until probe
            } else {
                nextState = CircuitState.CLOSED;
            }
        }

        entity.setCircuitState(nextState);
        entity.setErrorRate(errorRate);
        entity.setP95LatencyMs(p95);
        entity.setSuccessRate(successRate);
        entity.setWindowStart(now.minus(SUCCESS_WINDOW));
        entity.setWindowEnd(now);
        snapshotRepository.save(entity);
    }

    private boolean lastNCreateSessionAreFailures(PaymentProvider provider, int n) {
        Instant from = Instant.now().minus(Duration.ofHours(6));
        List<PaymentEventEntity> recent = paymentEventRepository.findRecentByProviderAndTypes(
                provider,
                List.of(EventTypes.PROVIDER_CREATE_SESSION_SUCCEEDED, EventTypes.PROVIDER_CREATE_SESSION_FAILED),
                from
        );
        if (recent.size() < n) return false;
        return recent.stream().limit(n).allMatch(e -> EventTypes.PROVIDER_CREATE_SESSION_FAILED.equals(e.getEventType()));
    }

    private long computeP95LatencyMs(PaymentProvider provider, Instant from) {
        List<PaymentEventEntity> successes = paymentEventRepository.findRecentByProviderAndType(
                provider,
                EventTypes.PROVIDER_CREATE_SESSION_SUCCEEDED,
                from
        );
        if (successes.isEmpty()) return 0;

        List<Long> latencies = successes.stream()
                .map(PaymentEventEntity::getSanitizedPayloadJson)
                .map(json -> extractLatency(json))
                .filter(v -> v != null && v >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (latencies.isEmpty()) return 0;
        int index = (int) Math.ceil(0.95 * latencies.size()) - 1;
        if (index < 0) index = 0;
        if (index >= latencies.size()) index = latencies.size() - 1;
        return latencies.get(index);
    }

    private Long extractLatency(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            Object v = map.get("latencyMs");
            if (v instanceof Number n) return n.longValue();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private double computePaymentSuccessRate(PaymentProvider provider, Instant from) {
        long succ = paymentEventRepository.countByProviderAndTypeSince(provider, EventTypes.PAYMENT_SUCCEEDED, from);
        long fail = paymentEventRepository.countByProviderAndTypeSince(provider, EventTypes.PAYMENT_FAILED, from);
        long total = succ + fail;
        if (total == 0) return Double.NaN;
        return (double) succ / (double) total;
    }

    private CircuitState effectiveCircuitState(CircuitState state, Instant lastFailureAt, Instant now) {
        if (state != CircuitState.OPEN) return state;
        if (lastFailureAt == null) return CircuitState.OPEN;
        if (Duration.between(lastFailureAt, now).compareTo(OPEN_TTL) > 0) {
            return CircuitState.HALF_OPEN;
        }
        return CircuitState.OPEN;
    }

}
