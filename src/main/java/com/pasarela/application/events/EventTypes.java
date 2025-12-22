package com.pasarela.application.events;

public final class EventTypes {
    private EventTypes() {}

    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String REFUND_SUCCEEDED = "REFUND_SUCCEEDED";

    public static final String PROVIDER_CREATE_SESSION_SUCCEEDED = "PROVIDER_CREATE_SESSION_SUCCEEDED";
    public static final String PROVIDER_CREATE_SESSION_FAILED = "PROVIDER_CREATE_SESSION_FAILED";
}

