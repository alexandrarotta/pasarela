package com.pasarela.infrastructure.provider;

import com.pasarela.config.AppProperties;
import com.pasarela.domain.model.PaymentProvider;
import com.stripe.Stripe;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StripeAdapter implements PaymentProviderAdapter {
    private static final Logger log = LoggerFactory.getLogger(StripeAdapter.class);

    private final AppProperties properties;

    public StripeAdapter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand command) {
        String secretKey = properties.providers().stripe().secretKey();
        String publishableKey = properties.providers().stripe().publishableKey();
        if (secretKey == null || secretKey.isBlank() || publishableKey == null || publishableKey.isBlank()) {
            throw new ProviderException(provider(), ProviderErrorType.VALIDATION, "Stripe is not configured");
        }

        try {
            Stripe.apiKey = secretKey;
            Stripe.enableTelemetry = false;

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(command.amountMinor())
                    .setCurrency(command.currency().toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    )
                    .putMetadata("pasarela_payment_intent_id", command.paymentIntentId().toString())
                    .putMetadata("pasarela_merchant_id", command.merchantId().toString());

            if (command.description() != null && !command.description().isBlank()) {
                builder.setDescription(command.description());
            }

            String providerIdempotencyKey = "po:" + command.merchantId() + ":" +
                    (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                            ? command.paymentIntentId()
                            : command.idempotencyKey());

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey(providerIdempotencyKey)
                    .build();

            PaymentIntent pi = PaymentIntent.create(builder.build(), options);

            Map<String, Object> checkoutConfig = new HashMap<>();
            checkoutConfig.put("type", "STRIPE");
            checkoutConfig.put("publishableKey", publishableKey);
            checkoutConfig.put("clientSecret", pi.getClientSecret());

            return new CreateSessionResult(pi.getId(), checkoutConfig);
        } catch (StripeException e) {
            throw mapStripeException(e);
        }
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        String secretKey = properties.providers().stripe().secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new ProviderException(provider(), ProviderErrorType.VALIDATION, "Stripe is not configured");
        }

        try {
            Stripe.apiKey = secretKey;
            Stripe.enableTelemetry = false;

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(command.providerRef())
                    .setAmount(command.amountMinor())
                    .build();
            Refund refund = Refund.create(params);
            return new RefundResult(refund.getId());
        } catch (StripeException e) {
            throw mapStripeException(e);
        }
    }

    private ProviderException mapStripeException(StripeException e) {
        Integer status = e.getStatusCode();
        ProviderErrorType type = ProviderErrorType.UNKNOWN;
        if (e instanceof ApiConnectionException) {
            type = ProviderErrorType.TIMEOUT;
        } else if (status != null) {
            if (status >= 500) type = ProviderErrorType.HTTP_5XX;
            else if (status == 408) type = ProviderErrorType.TIMEOUT;
            else if (status >= 400 && status < 500) type = ProviderErrorType.VALIDATION;
        }
        log.warn("Stripe error type={} status={}", type, status);
        return new ProviderException(provider(), type, "Stripe request failed");
    }
}

