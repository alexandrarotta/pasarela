package com.pasarela.api.paymentintents;

import com.pasarela.application.PaymentIntentService;
import com.pasarela.api.ApiException;
import com.pasarela.application.routing.ProviderPreference;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.domain.model.PaymentStatus;
import com.pasarela.domain.security.MerchantPrincipal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment-intents")
public class PaymentIntentController {
    private final PaymentIntentService paymentIntentService;

    public PaymentIntentController(PaymentIntentService paymentIntentService) {
        this.paymentIntentService = paymentIntentService;
    }

    @PostMapping
    public PaymentIntentCreateResponse create(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CreatePaymentIntentRequest req
    ) {
        MerchantPrincipal resolved = requireMerchant(merchant);
        var created = paymentIntentService.create(
                resolved.merchantId(),
                new PaymentIntentService.CreatePaymentIntentCommand(
                        req.amountMinor(),
                        req.currency(),
                        req.description(),
                        req.providerPreference() == null ? ProviderPreference.AUTO : req.providerPreference()
                ),
                idempotencyKey,
                requestId == null ? "n/a" : requestId
        );
        return PaymentIntentCreateResponse.from(created);
    }

    @PostMapping("/{id}/reroute")
    public PaymentIntentCreateResponse reroute(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @PathVariable("id") UUID paymentIntentId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody RerouteRequest req
    ) {
        MerchantPrincipal resolved = requireMerchant(merchant);
        var created = paymentIntentService.reroute(
                resolved.merchantId(),
                paymentIntentId,
                req.reason(),
                requestId == null ? "n/a" : requestId
        );
        return PaymentIntentCreateResponse.from(created);
    }

    @GetMapping("/{id}")
    public PaymentIntentWithConfigResponse get(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @PathVariable("id") UUID paymentIntentId
    ) {
        MerchantPrincipal resolved = requireMerchant(merchant);
        return paymentIntentService.getWithCheckoutConfig(resolved.merchantId(), paymentIntentId)
                .map(PaymentIntentWithConfigResponse::from)
                .orElseThrow(() -> new com.pasarela.api.ApiException(HttpStatus.NOT_FOUND, "PaymentIntent not found"));
    }

    @GetMapping
    public List<PaymentIntentService.PaymentIntentView> list(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @RequestParam(value = "status", required = false) PaymentStatus status,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        MerchantPrincipal resolved = requireMerchant(merchant);
        return paymentIntentService.list(resolved.merchantId(), status, from, to);
    }

    @PostMapping("/{id}/refund")
    public PaymentIntentService.RefundResultView refund(
            @AuthenticationPrincipal MerchantPrincipal merchant,
            @PathVariable("id") UUID paymentIntentId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody RefundRequest req
    ) {
        MerchantPrincipal resolved = requireMerchant(merchant);
        return paymentIntentService.refund(
                resolved.merchantId(),
                paymentIntentId,
                req.reason(),
                requestId == null ? "n/a" : requestId
        );
    }

    private MerchantPrincipal requireMerchant(MerchantPrincipal merchant) {
        if (merchant == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid X-Api-Key");
        }
        return merchant;
    }

    public record CreatePaymentIntentRequest(
            @Min(1) long amountMinor,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 255) String description,
            ProviderPreference providerPreference
    ) {}

    public record RerouteRequest(@NotBlank @Size(max = 200) String reason) {}

    public record RefundRequest(@NotBlank @Size(max = 200) String reason) {}

    public record PaymentIntentCreateResponse(
            UUID paymentIntentId,
            PaymentStatus status,
            PaymentProvider provider,
            UUID routingDecisionId,
            String routingReasonCode,
            Map<String, Object> checkoutConfig
    ) {
        static PaymentIntentCreateResponse from(PaymentIntentService.PaymentIntentCreated created) {
            var pi = created.paymentIntent();
            return new PaymentIntentCreateResponse(
                    pi.id(),
                    pi.status(),
                    pi.provider(),
                    pi.routingDecisionId(),
                    pi.routingReasonCode(),
                    created.checkoutConfig()
            );
        }
    }

    public record PaymentIntentWithConfigResponse(
            PaymentIntentService.PaymentIntentView paymentIntent,
            Map<String, Object> checkoutConfig
    ) {
        static PaymentIntentWithConfigResponse from(PaymentIntentService.PaymentIntentWithCheckoutConfig v) {
            return new PaymentIntentWithConfigResponse(v.paymentIntent(), v.checkoutConfig());
        }
    }
}
