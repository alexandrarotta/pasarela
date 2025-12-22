package com.pasarela.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.config.AppProperties;
import com.pasarela.domain.model.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AdyenAdapter implements PaymentProviderAdapter {
    private static final Logger log = LoggerFactory.getLogger(AdyenAdapter.class);
    private static final String CHECKOUT_API_VERSION = "v71";

    private final AppProperties properties;
    private final WebClient adyenWebClient;
    private final ObjectMapper objectMapper;

    public AdyenAdapter(AppProperties properties, WebClient adyenWebClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.adyenWebClient = adyenWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.ADYEN;
    }

    @Override
    public CreateSessionResult createSession(CreateSessionCommand command) {
        var cfg = properties.providers().adyen();
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()
                || cfg.merchantAccount() == null || cfg.merchantAccount().isBlank()
                || cfg.clientKey() == null || cfg.clientKey().isBlank()) {
            throw new ProviderException(provider(), ProviderErrorType.VALIDATION, "Adyen is not configured");
        }

        String returnUrl = command.returnUrl();
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = properties.frontend().baseUrl() + "/checkout/" + command.paymentIntentId();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("merchantAccount", cfg.merchantAccount());
        body.put("reference", command.paymentIntentId().toString());
        body.put("returnUrl", returnUrl);
        body.put("channel", "Web");
        body.put("amount", Map.of(
                "value", command.amountMinor(),
                "currency", command.currency().toUpperCase()
        ));

        try {
            AdyenSessionResponse resp = adyenWebClient.post()
                    .uri("/" + CHECKOUT_API_VERSION + "/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", cfg.apiKey())
                    .headers(h -> {
                        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
                            h.set("Idempotency-Key", "po:" + command.merchantId() + ":" + command.idempotencyKey());
                        }
                    })
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, res -> res.bodyToMono(String.class).flatMap(b -> Mono.error(
                            new ProviderException(provider(), ProviderErrorType.HTTP_5XX, "Adyen 5xx"))))
                    .bodyToMono(AdyenSessionResponse.class)
                    .timeout(Duration.ofSeconds(12))
                    .block();

            if (resp == null || resp.id == null || resp.sessionData == null) {
                throw new ProviderException(provider(), ProviderErrorType.UNKNOWN, "Adyen session response invalid");
            }

            Map<String, Object> checkoutConfig = new HashMap<>();
            checkoutConfig.put("type", "ADYEN");
            checkoutConfig.put("clientKey", cfg.clientKey());
            checkoutConfig.put("environment", cfg.environment());
            checkoutConfig.put("sessionId", resp.id);
            checkoutConfig.put("sessionData", resp.sessionData);

            return new CreateSessionResult(resp.id, checkoutConfig);
        } catch (ProviderException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw mapWebClientException(e);
        } catch (Exception e) {
            throw new ProviderException(provider(), ProviderErrorType.UNKNOWN, "Adyen request failed", e);
        }
    }

    @Override
    public RefundResult refund(RefundCommand command) {
        var cfg = properties.providers().adyen();
        if (cfg.apiKey() == null || cfg.apiKey().isBlank()
                || cfg.merchantAccount() == null || cfg.merchantAccount().isBlank()) {
            throw new ProviderException(provider(), ProviderErrorType.VALIDATION, "Adyen is not configured");
        }

        if (command.providerRef() == null || command.providerRef().startsWith("CS")) {
            throw new ProviderException(provider(), ProviderErrorType.VALIDATION, "Adyen refund requires PSP reference (wait for webhook)");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("merchantAccount", cfg.merchantAccount());
        body.put("reference", "refund-" + command.providerRef());
        body.put("amount", Map.of("value", command.amountMinor(), "currency", command.currency().toUpperCase()));

        try {
            Map<?, ?> resp = adyenWebClient.post()
                    .uri("/" + CHECKOUT_API_VERSION + "/payments/" + command.providerRef() + "/refunds")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", cfg.apiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(12))
                    .block();
            String refundRef = resp == null ? null : (String) resp.get("pspReference");
            return new RefundResult(refundRef == null ? "UNKNOWN" : refundRef);
        } catch (WebClientResponseException e) {
            throw mapWebClientException(e);
        } catch (Exception e) {
            throw new ProviderException(provider(), ProviderErrorType.UNKNOWN, "Adyen refund request failed", e);
        }
    }

    private ProviderException mapWebClientException(WebClientResponseException e) {
        int status = e.getRawStatusCode();
        ProviderErrorType type;
        if (status >= 500) type = ProviderErrorType.HTTP_5XX;
        else if (status == 408 || status == 504) type = ProviderErrorType.TIMEOUT;
        else type = ProviderErrorType.VALIDATION;

        log.warn("Adyen error type={} status={}", type, status);
        return new ProviderException(provider(), type, "Adyen request failed");
    }

    private static final class AdyenSessionResponse {
        public String id;
        public String sessionData;
    }
}

