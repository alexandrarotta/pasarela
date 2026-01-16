package com.pasarela.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intent_private_data")
public class PaymentIntentPrivateDataEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payment_intent_id", nullable = false, length = 36)
    private UUID paymentIntentId;

    @Column(name = "checkout_config_enc", nullable = false)
    private String checkoutConfigEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(UUID paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getCheckoutConfigEnc() {
        return checkoutConfigEnc;
    }

    public void setCheckoutConfigEnc(String checkoutConfigEnc) {
        this.checkoutConfigEnc = checkoutConfigEnc;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

