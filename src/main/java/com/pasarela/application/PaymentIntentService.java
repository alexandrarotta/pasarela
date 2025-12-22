package com.pasarela.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.api.ApiException;
import com.pasarela.application.routing.ProviderHealthService;
import com.pasarela.application.routing.ProviderPreference;
import com.pasarela.application.routing.RoutingEngine;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.domain.model.PaymentStatus;
import com.pasarela.infrastructure.checkout.CheckoutConfigStore;
import com.pasarela.infrastructure.crypto.Sha256;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import com.pasarela.infrastructure.persistence.entity.PaymentIntentEntity;
import com.pasarela.infrastructure.persistence.entity.RoutingDecisionEntity;
import com.pasarela.infrastructure.persistence.repository.MerchantRepository;
import com.pasarela.infrastructure.persistence.repository.PaymentIntentRepository;
import com.pasarela.infrastructure.persistence.repository.RoutingDecisionRepository;
import com.pasarela.infrastructure.provider.CreateSessionCommand;
import com.pasarela.infrastructure.provider.CreateSessionResult;
import com.pasarela.infrastructure.provider.ProviderErrorType;
import com.pasarela.infrastructure.provider.ProviderException;
import com.pasarela.infrastructure.provider.RefundCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentIntentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentIntentService.class);
    private static final String IDEMPOTENCY_ENDPOINT = "/api/payment-intents";
    private static final int MAX_ATTEMPTS_PER_ROOT = 3;

    private final MerchantRepository merchantRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final RoutingDecisionRepository routingDecisionRepository;
    private final RoutingEngine routingEngine;
    private final ProviderAdapterRegistry providerAdapterRegistry;
    private final CheckoutConfigStore checkoutConfigStore;
    private final IdempotencyService idempotencyService;
    private final ProviderHealthService providerHealthService;
    private final ObjectMapper objectMapper;

    public PaymentIntentService(
            MerchantRepository merchantRepository,
            PaymentIntentRepository paymentIntentRepository,
            RoutingDecisionRepository routingDecisionRepository,
            RoutingEngine routingEngine,
            ProviderAdapterRegistry providerAdapterRegistry,
            CheckoutConfigStore checkoutConfigStore,
            IdempotencyService idempotencyService,
            ProviderHealthService providerHealthService,
            ObjectMapper objectMapper
    ) {
        this.merchantRepository = merchantRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.routingDecisionRepository = routingDecisionRepository;
        this.routingEngine = routingEngine;
        this.providerAdapterRegistry = providerAdapterRegistry;
        this.checkoutConfigStore = checkoutConfigStore;
        this.idempotencyService = idempotencyService;
        this.providerHealthService = providerHealthService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentIntentCreated create(UUID merchantId, CreatePaymentIntentCommand command, String idempotencyKey, String requestId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Merchant not found"));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<UUID> existing = idempotencyService.findExisting(merchantId, IDEMPOTENCY_ENDPOINT, idempotencyKey);
            if (existing.isPresent()) {
                return get(merchantId, existing.get())
                        .map(pi -> new PaymentIntentCreated(pi, requireCheckoutConfig(pi.id())))
                        .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Idempotency record found but payment intent missing"));
            }
        }

        UUID paymentIntentId = UUID.randomUUID();
        UUID rootId = paymentIntentId;
        int attempt = 0;

        PaymentIntentCreated created = createInternal(merchant, paymentIntentId, rootId, attempt, command, idempotencyKey, requestId, Set.of());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.record(
                    merchantId,
                    IDEMPOTENCY_ENDPOINT,
                    idempotencyKey,
                    created.paymentIntent().id(),
                    Sha256.hex(requestHash(command))
            );
        }
        return created;
    }

    @Transactional
    public PaymentIntentCreated reroute(UUID merchantId, UUID paymentIntentId, String reason, String requestId) {
        MerchantEntity merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Merchant not found"));

        PaymentIntentEntity existing = paymentIntentRepository.findByIdAndMerchantId(paymentIntentId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PaymentIntent not found"));

        if (!(existing.getStatus() == PaymentStatus.FAILED || existing.getStatus() == PaymentStatus.REQUIRES_PAYMENT_METHOD)) {
            throw new ApiException(HttpStatus.CONFLICT, "Reroute allowed only for FAILED or REQUIRES_PAYMENT_METHOD");
        }

        UUID rootId = existing.getRootPaymentIntentId() == null ? existing.getId() : existing.getRootPaymentIntentId();
        long count = paymentIntentRepository.countByRootPaymentIntentId(rootId);
        if (count >= MAX_ATTEMPTS_PER_ROOT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Max reroute attempts reached");
        }

        UUID newId = UUID.randomUUID();
        int attemptNumber = (int) count;

        CreatePaymentIntentCommand cmd = new CreatePaymentIntentCommand(
                existing.getAmountMinor(),
                existing.getCurrency(),
                existing.getDescription(),
                ProviderPreference.AUTO
        );

        Set<PaymentProvider> excluded = Set.of(existing.getProvider());
        return createInternal(merchant, newId, rootId, attemptNumber, cmd, null, requestId, excluded);
    }

    public Optional<PaymentIntentView> get(UUID merchantId, UUID paymentIntentId) {
        return paymentIntentRepository.findByIdAndMerchantId(paymentIntentId, merchantId).map(this::toView);
    }

    public Optional<PaymentIntentWithCheckoutConfig> getWithCheckoutConfig(UUID merchantId, UUID paymentIntentId) {
        return paymentIntentRepository.findByIdAndMerchantId(paymentIntentId, merchantId).map(pi -> new PaymentIntentWithCheckoutConfig(
                toView(pi),
                requireCheckoutConfig(pi.getId())
        ));
    }

    public List<PaymentIntentView> list(UUID merchantId, PaymentStatus status, Instant from, Instant to) {
        return paymentIntentRepository.search(merchantId, status, from, to).stream().map(this::toView).toList();
    }

    @Transactional
    public RefundResultView refund(UUID merchantId, UUID paymentIntentId, String reason, String requestId) {
        PaymentIntentEntity pi = paymentIntentRepository.findByIdAndMerchantId(paymentIntentId, merchantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PaymentIntent not found"));
        if (pi.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "Refund allowed only for SUCCEEDED");
        }

        var adapter = providerAdapterRegistry.get(pi.getProvider());
        var result = adapter.refund(new RefundCommand(pi.getProvider(), pi.getProviderRef(), pi.getAmountMinor(), pi.getCurrency(), reason));
        pi.setStatus(PaymentStatus.PROCESSING);
        paymentIntentRepository.save(pi);

        return new RefundResultView(pi.getId(), pi.getStatus(), pi.getProvider(), result.providerRefundRef());
    }

    private PaymentIntentCreated createInternal(
            MerchantEntity merchant,
            UUID paymentIntentId,
            UUID rootId,
            int attempt,
            CreatePaymentIntentCommand command,
            String idempotencyKey,
            String requestId,
            Set<PaymentProvider> excludedProviders
    ) {
        String currency = command.currency().toUpperCase();

        RoutingEngine.RoutingResult routing = routingEngine.decide(
                merchant,
                paymentIntentId,
                command.amountMinor(),
                currency,
                command.providerPreference(),
                excludedProviders
        );

        PaymentIntentEntity pi = new PaymentIntentEntity();
        pi.setId(paymentIntentId);
        pi.setMerchantId(merchant.getId());
        pi.setAmountMinor(command.amountMinor());
        pi.setCurrency(currency);
        pi.setDescription(command.description());
        pi.setStatus(PaymentStatus.CREATED);
        pi.setProvider(routing.chosenProvider());
        pi.setRoutingReasonCode(routing.reasonCode());
        pi.setIdempotencyKey(idempotencyKey);
        pi.setRootPaymentIntentId(rootId);
        pi.setAttemptNumber(attempt);
        // Persist the payment intent first to satisfy SQLite FK from routing_decisions.
        paymentIntentRepository.saveAndFlush(pi);

        RoutingDecisionEntity decision = new RoutingDecisionEntity();
        decision.setMerchantId(merchant.getId());
        decision.setPaymentIntentId(paymentIntentId);
        decision.setChosenProvider(routing.chosenProvider());
        decision.setCandidateScoresJson(routing.candidateScoresJson());
        decision.setReasonCode(routing.reasonCode());
        RoutingDecisionEntity savedDecision = routingDecisionRepository.save(decision);

        pi.setRoutingDecisionId(savedDecision.getId());
        pi.setRoutingReasonCode(savedDecision.getReasonCode());
        paymentIntentRepository.save(pi);

        CreateSessionResult session = null;
        long latencyMs = 0;

        try {
            session = createSessionFor(pi, merchant, command, idempotencyKey);
            latencyMs = session.checkoutConfig().containsKey("_latencyMs")
                    ? ((Number) session.checkoutConfig().get("_latencyMs")).longValue()
                    : 0;
            providerHealthService.recordCreateSessionOutcome(pi.getProvider(), pi.getId(), true, latencyMs, null, "req:" + requestId);
        } catch (ProviderException ex) {
            providerHealthService.recordCreateSessionOutcome(pi.getProvider(), pi.getId(), false, latencyMs, ex.getType().name(), "req:" + requestId);

            boolean eligibleForInstantFallback = ex.getType() == ProviderErrorType.TIMEOUT
                    || ex.getType() == ProviderErrorType.HTTP_5XX
                    || ex.getType() == ProviderErrorType.VALIDATION;

            if (eligibleForInstantFallback) {
                java.util.Set<PaymentProvider> newExcluded = new java.util.HashSet<>();
                if (excludedProviders != null) newExcluded.addAll(excludedProviders);
                newExcluded.add(pi.getProvider());
                try {
                    RoutingEngine.RoutingResult fallbackRouting = routingEngine.decide(
                            merchant,
                            paymentIntentId,
                            command.amountMinor(),
                            currency,
                            ProviderPreference.AUTO,
                            java.util.Set.copyOf(newExcluded)
                    );

                    RoutingDecisionEntity fallbackDecision = new RoutingDecisionEntity();
                    fallbackDecision.setMerchantId(merchant.getId());
                    fallbackDecision.setPaymentIntentId(paymentIntentId);
                    fallbackDecision.setChosenProvider(fallbackRouting.chosenProvider());
                    fallbackDecision.setCandidateScoresJson(fallbackRouting.candidateScoresJson());
                    fallbackDecision.setReasonCode("INSTANT_FALLBACK");
                    RoutingDecisionEntity savedFallbackDecision = routingDecisionRepository.save(fallbackDecision);

                    pi.setProvider(fallbackRouting.chosenProvider());
                    pi.setRoutingDecisionId(savedFallbackDecision.getId());
                    pi.setRoutingReasonCode("INSTANT_FALLBACK");
                    paymentIntentRepository.save(pi);

                    try {
                        session = createSessionFor(pi, merchant, command, idempotencyKey);
                        latencyMs = session.checkoutConfig().containsKey("_latencyMs")
                                ? ((Number) session.checkoutConfig().get("_latencyMs")).longValue()
                                : 0;
                        providerHealthService.recordCreateSessionOutcome(pi.getProvider(), pi.getId(), true, latencyMs, null, "req:" + requestId + ":fallback");
                    } catch (ProviderException fallbackEx) {
                        providerHealthService.recordCreateSessionOutcome(pi.getProvider(), pi.getId(), false, latencyMs, fallbackEx.getType().name(), "req:" + requestId + ":fallback");
                        throw fallbackEx;
                    }
                } catch (Exception fallbackError) {
                    pi.setStatus(PaymentStatus.FAILED);
                    paymentIntentRepository.save(pi);
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Both providers failed creating checkout session");
                }
            } else {
                pi.setStatus(PaymentStatus.FAILED);
                paymentIntentRepository.save(pi);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Provider failed creating checkout session");
            }
        }

        if (session == null) {
            pi.setStatus(PaymentStatus.FAILED);
            paymentIntentRepository.save(pi);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Checkout session not created");
        }

        pi.setProviderRef(session.providerRef());
        pi.setStatus(PaymentStatus.REQUIRES_PAYMENT_METHOD);
        paymentIntentRepository.save(pi);

        Map<String, Object> checkoutConfig = session.checkoutConfig();
        checkoutConfig.remove("_latencyMs");
        checkoutConfigStore.upsert(pi.getId(), checkoutConfig);

        return new PaymentIntentCreated(toView(pi), checkoutConfig);
    }

    private CreateSessionResult createSessionFor(
            PaymentIntentEntity pi,
            MerchantEntity merchant,
            CreatePaymentIntentCommand cmd,
            String idempotencyKey
    ) {
        long startedAt = System.nanoTime();
        try {
            CreateSessionResult res = providerAdapterRegistry.get(pi.getProvider()).createSession(new CreateSessionCommand(
                    merchant.getId(),
                    pi.getId(),
                    pi.getAmountMinor(),
                    pi.getCurrency(),
                    pi.getDescription(),
                    idempotencyKey,
                    null,
                    pi.getProvider()
            ));
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            Map<String, Object> config = new java.util.HashMap<>(res.checkoutConfig());
            config.put("_latencyMs", latencyMs);
            return new CreateSessionResult(res.providerRef(), config);
        } catch (RuntimeException e) {
            long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (e instanceof ProviderException pe) {
                throw pe;
            }
            log.warn("createSession failed provider={} latencyMs={}", pi.getProvider(), latencyMs);
            throw e;
        }
    }

    private PaymentIntentView toView(PaymentIntentEntity pi) {
        return new PaymentIntentView(
                pi.getId(),
                pi.getMerchantId(),
                pi.getAmountMinor(),
                pi.getCurrency(),
                pi.getDescription(),
                pi.getStatus(),
                pi.getProvider(),
                pi.getProviderRef(),
                pi.getIdempotencyKey(),
                pi.getRoutingDecisionId(),
                pi.getRoutingReasonCode(),
                pi.getRootPaymentIntentId(),
                pi.getAttemptNumber(),
                pi.getCreatedAt(),
                pi.getUpdatedAt()
        );
    }

    private Map<String, Object> requireCheckoutConfig(UUID paymentIntentId) {
        return checkoutConfigStore.get(paymentIntentId).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT,
                "Checkout config missing"
        ));
    }

    private String requestHash(CreatePaymentIntentCommand cmd) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "amountMinor", cmd.amountMinor(),
                    "currency", cmd.currency(),
                    "description", cmd.description(),
                    "providerPreference", cmd.providerPreference().name()
            ));
        } catch (Exception e) {
            return cmd.amountMinor() + ":" + cmd.currency() + ":" + cmd.providerPreference();
        }
    }

    public record CreatePaymentIntentCommand(
            long amountMinor,
            String currency,
            String description,
            ProviderPreference providerPreference
    ) {}

    public record PaymentIntentCreated(
            PaymentIntentView paymentIntent,
            Map<String, Object> checkoutConfig
    ) {}

    public record PaymentIntentWithCheckoutConfig(
            PaymentIntentView paymentIntent,
            Map<String, Object> checkoutConfig
    ) {}

    public record PaymentIntentView(
            UUID id,
            UUID merchantId,
            long amountMinor,
            String currency,
            String description,
            PaymentStatus status,
            PaymentProvider provider,
            String providerRef,
            String idempotencyKey,
            UUID routingDecisionId,
            String routingReasonCode,
            UUID rootPaymentIntentId,
            int attemptNumber,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record RefundResultView(
            UUID paymentIntentId,
            PaymentStatus status,
            PaymentProvider provider,
            String providerRefundRef
    ) {}
}
