package com.pasarela.infrastructure.persistence.entity;

import com.pasarela.domain.model.PaymentProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routing_decisions")
public class RoutingDecisionEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_intent_id", nullable = false, length = 36)
    private UUID paymentIntentId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "merchant_id", nullable = false, length = 36)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chosen_provider", nullable = false)
    private PaymentProvider chosenProvider;

    @Column(name = "candidate_scores_json", nullable = false)
    private String candidateScoresJson;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(UUID paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public PaymentProvider getChosenProvider() {
        return chosenProvider;
    }

    public void setChosenProvider(PaymentProvider chosenProvider) {
        this.chosenProvider = chosenProvider;
    }

    public String getCandidateScoresJson() {
        return candidateScoresJson;
    }

    public void setCandidateScoresJson(String candidateScoresJson) {
        this.candidateScoresJson = candidateScoresJson;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

