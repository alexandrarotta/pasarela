package com.pasarela.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.api.ApiException;
import com.pasarela.application.routing.ProviderPreference;
import com.pasarela.application.routing.RoutingConfig;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import com.pasarela.infrastructure.persistence.entity.RoutingDecisionEntity;
import com.pasarela.infrastructure.persistence.repository.MerchantRepository;
import com.pasarela.infrastructure.persistence.repository.PaymentIntentRepository;
import com.pasarela.infrastructure.persistence.repository.RoutingDecisionRepository;
import com.pasarela.infrastructure.provider.AdyenAdapter;
import com.pasarela.infrastructure.provider.CreateSessionCommand;
import com.pasarela.infrastructure.provider.CreateSessionResult;
import com.pasarela.infrastructure.provider.RefundCommand;
import com.pasarela.infrastructure.provider.RefundResult;
import com.pasarela.infrastructure.provider.StripeAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentIntentServiceFkTest {
    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private RoutingDecisionRepository routingDecisionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StripeAdapter stripeAdapter;

    @MockBean
    private AdyenAdapter adyenAdapter;

    @BeforeEach
    void stubAdapters() {
        when(stripeAdapter.provider()).thenReturn(PaymentProvider.STRIPE);
        when(adyenAdapter.provider()).thenReturn(PaymentProvider.ADYEN);

        when(stripeAdapter.createSession(any(CreateSessionCommand.class)))
                .thenReturn(new CreateSessionResult("stripe_ref", Map.of("type", "TEST")));
        when(adyenAdapter.createSession(any(CreateSessionCommand.class)))
                .thenReturn(new CreateSessionResult("adyen_ref", Map.of("type", "TEST")));

        when(stripeAdapter.refund(any(RefundCommand.class))).thenReturn(new RefundResult("refund_ref"));
        when(adyenAdapter.refund(any(RefundCommand.class))).thenReturn(new RefundResult("refund_ref"));
    }

    @Test
    void createPersistsPaymentIntentBeforeRoutingDecision() {
        MerchantEntity merchant = createMerchant();

        var created = paymentIntentService.create(
                merchant.getId(),
                new PaymentIntentService.CreatePaymentIntentCommand(1500, "EUR", "test", ProviderPreference.AUTO),
                null,
                "req-1"
        );

        var pi = paymentIntentRepository.findById(created.paymentIntent().id()).orElseThrow();
        assertNotNull(pi.getRoutingDecisionId());

        RoutingDecisionEntity decision = routingDecisionRepository.findById(pi.getRoutingDecisionId()).orElseThrow();
        assertEquals(pi.getId(), decision.getPaymentIntentId());
    }

    @Test
    void createWithUnknownMerchantFailsBeforeWrite() {
        UUID unknownMerchant = UUID.randomUUID();

        ApiException ex = assertThrows(ApiException.class, () -> paymentIntentService.create(
                unknownMerchant,
                new PaymentIntentService.CreatePaymentIntentCommand(1500, "EUR", "test", ProviderPreference.AUTO),
                null,
                "req-2"
        ));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals(0, paymentIntentRepository.count());
        assertTrue(routingDecisionRepository.findAll().isEmpty());
    }

    private MerchantEntity createMerchant() {
        MerchantEntity entity = new MerchantEntity();
        entity.setName("merchant");
        entity.setApiKeyHash(UUID.randomUUID().toString().replace("-", ""));
        entity.setConfigJson(serializeConfig());
        return merchantRepository.save(entity);
    }

    private String serializeConfig() {
        try {
            return objectMapper.writeValueAsString(RoutingConfig.defaults());
        } catch (Exception e) {
            return "{}";
        }
    }
}
