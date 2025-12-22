package com.pasarela.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.api.ApiException;
import com.pasarela.application.routing.ProviderHealthService;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.domain.model.PaymentStatus;
import com.pasarela.infrastructure.persistence.entity.PaymentIntentEntity;
import com.pasarela.infrastructure.persistence.repository.PaymentIntentRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final com.pasarela.config.AppProperties properties;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ProviderHealthService providerHealthService;
    private final ObjectMapper objectMapper;

    public WebhookService(
            com.pasarela.config.AppProperties properties,
            PaymentIntentRepository paymentIntentRepository,
            ProviderHealthService providerHealthService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.paymentIntentRepository = paymentIntentRepository;
        this.providerHealthService = providerHealthService;
        this.objectMapper = objectMapper;
    }

    public void handleStripe(String payload, String signatureHeader) {
        String webhookSecret = properties.providers().stripe().webhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Stripe webhook secret not configured");
        }

        final Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Stripe-Signature");
        }

        String type = event.getType();
        log.info("Stripe webhook received type={} id={}", type, event.getId());

        switch (type) {
            case "payment_intent.succeeded" -> {
                PaymentIntent stripePi = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (stripePi == null) return;
                onStripePaymentOutcome(payload, stripePi.getId(), true, Map.of("stripeEventId", event.getId(), "type", type));
            }
            case "payment_intent.payment_failed" -> {
                PaymentIntent stripePi = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (stripePi == null) return;
                onStripePaymentOutcome(payload, stripePi.getId(), false, Map.of("stripeEventId", event.getId(), "type", type));
            }
            case "charge.refunded" -> {
                Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElse(null);
                if (charge == null) return;
                String stripePiId = charge.getPaymentIntent();
                if (stripePiId == null) return;
                onStripeRefund(payload, stripePiId, Map.of("stripeEventId", event.getId(), "type", type));
            }
            default -> {
                // ignore
            }
        }
    }

    private void onStripePaymentOutcome(String payload, String stripePaymentIntentId, boolean success, Map<String, Object> sanitized) {
        Optional<PaymentIntentEntity> pi = paymentIntentRepository.findStripeByProviderRef(stripePaymentIntentId);
        if (pi.isEmpty()) return;

        PaymentIntentEntity entity = pi.get();
        entity.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
        paymentIntentRepository.save(entity);

        providerHealthService.recordPaymentOutcomeFromWebhook(
                PaymentProvider.STRIPE,
                entity.getId(),
                success,
                payload,
                toJsonSafe(sanitized)
        );
    }

    private void onStripeRefund(String payload, String stripePaymentIntentId, Map<String, Object> sanitized) {
        Optional<PaymentIntentEntity> pi = paymentIntentRepository.findStripeByProviderRef(stripePaymentIntentId);
        if (pi.isEmpty()) return;

        PaymentIntentEntity entity = pi.get();
        entity.setStatus(PaymentStatus.REFUNDED);
        paymentIntentRepository.save(entity);

        providerHealthService.recordRefundOutcomeFromWebhook(
                PaymentProvider.STRIPE,
                entity.getId(),
                payload,
                toJsonSafe(sanitized)
        );
    }

    public String handleAdyen(String payload) {
        String hmacKeyBase64 = properties.providers().adyen().hmacKey();
        if (hmacKeyBase64 == null || hmacKeyBase64.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Adyen HMAC key not configured");
        }

        final AdyenNotificationRequest req;
        try {
            req = objectMapper.readValue(payload, AdyenNotificationRequest.class);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Adyen webhook body");
        }

        byte[] hmacKey = Base64.getDecoder().decode(hmacKeyBase64);
        for (AdyenNotificationItemWrapper wrapper : req.notificationItems) {
            AdyenNotificationItem item = wrapper.NotificationRequestItem;
            if (!verifyAdyenHmac(item, hmacKey)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Adyen HMAC");
            }
            applyAdyenItem(payload, item);
        }

        return "[accepted]";
    }

    private void applyAdyenItem(String payload, AdyenNotificationItem item) {
        UUID paymentIntentId;
        try {
            paymentIntentId = UUID.fromString(item.merchantReference);
        } catch (Exception e) {
            return;
        }

        PaymentIntentEntity pi = paymentIntentRepository.findById(paymentIntentId).orElse(null);
        if (pi == null || pi.getProvider() != PaymentProvider.ADYEN) return;

        boolean success = "true".equalsIgnoreCase(item.success);
        String eventCode = item.eventCode == null ? "" : item.eventCode;

        Map<String, Object> sanitized = Map.of(
                "eventCode", eventCode,
                "success", success,
                "pspReference", item.pspReference
        );

        if ("AUTHORISATION".equalsIgnoreCase(eventCode)) {
            pi.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
            if (item.pspReference != null && !item.pspReference.isBlank()) {
                // After authorisation, PSP reference becomes the stable provider reference for refunds.
                pi.setProviderRef(item.pspReference);
            }
            paymentIntentRepository.save(pi);

            providerHealthService.recordPaymentOutcomeFromWebhook(
                    PaymentProvider.ADYEN,
                    pi.getId(),
                    success,
                    payload,
                    toJsonSafe(sanitized)
            );
            return;
        }

        if ("REFUND".equalsIgnoreCase(eventCode) && success) {
            pi.setStatus(PaymentStatus.REFUNDED);
            paymentIntentRepository.save(pi);

            providerHealthService.recordRefundOutcomeFromWebhook(
                    PaymentProvider.ADYEN,
                    pi.getId(),
                    payload,
                    toJsonSafe(sanitized)
            );
        }
    }

    private boolean verifyAdyenHmac(AdyenNotificationItem item, byte[] hmacKey) {
        String signature = item.additionalData == null ? null : item.additionalData.get("hmacSignature");
        if (signature == null || signature.isBlank()) return false;

        String message = String.join(":",
                nvl(item.pspReference),
                nvl(item.originalReference),
                nvl(item.merchantAccountCode),
                nvl(item.merchantReference),
                item.amount == null ? "" : String.valueOf(item.amount.value),
                item.amount == null ? "" : nvl(item.amount.currency),
                nvl(item.eventCode),
                nvl(item.success)
        );

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(out);
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            log.warn("Adyen HMAC verify failed", e);
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) {
            result |= ab[i] ^ bb[i];
        }
        return result == 0;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private String toJsonSafe(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static class AdyenNotificationRequest {
        public String live;
        public List<AdyenNotificationItemWrapper> notificationItems;
    }

    public static class AdyenNotificationItemWrapper {
        public AdyenNotificationItem NotificationRequestItem;
    }

    public static class AdyenNotificationItem {
        public Map<String, String> additionalData;
        public Amount amount;
        public String eventCode;
        public String success;
        public String merchantReference;
        public String pspReference;
        public String originalReference;
        public String merchantAccountCode;
    }

    public static class Amount {
        public String currency;
        public Long value;
    }
}

