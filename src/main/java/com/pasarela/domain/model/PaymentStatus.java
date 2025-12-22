package com.pasarela.domain.model;

public enum PaymentStatus {
    CREATED,
    REQUIRES_PAYMENT_METHOD,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}

