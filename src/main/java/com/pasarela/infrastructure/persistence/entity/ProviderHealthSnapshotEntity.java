package com.pasarela.infrastructure.persistence.entity;

import com.pasarela.domain.model.CircuitState;
import com.pasarela.domain.model.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_health_snapshot")
public class ProviderHealthSnapshotEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, unique = true)
    private PaymentProvider provider;

    @Column(name = "window_start")
    private Instant windowStart;

    @Column(name = "window_end")
    private Instant windowEnd;

    @Column(name = "success_rate", nullable = false)
    private double successRate;

    @Column(name = "error_rate", nullable = false)
    private double errorRate;

    @Column(name = "p95_latency_ms", nullable = false)
    private long p95LatencyMs;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "circuit_state", nullable = false)
    private CircuitState circuitState;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (updatedAt == null) updatedAt = Instant.now();
        if (circuitState == null) circuitState = CircuitState.CLOSED;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public void setProvider(PaymentProvider provider) {
        this.provider = provider;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(long p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public Instant getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(Instant lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public CircuitState getCircuitState() {
        return circuitState;
    }

    public void setCircuitState(CircuitState circuitState) {
        this.circuitState = circuitState;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

