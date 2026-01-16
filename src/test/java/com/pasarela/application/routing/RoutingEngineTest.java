package com.pasarela.application.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.domain.model.CircuitState;
import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingEngineTest {
    @Test
    void tieBreakerIsStable() throws Exception {
        ProviderHealthReader health = RoutingEngineTest::snapshot;

        ObjectMapper mapper = new ObjectMapper();
        RoutingEngine engine = new RoutingEngine(health, mapper);

        MerchantEntity merchant = new MerchantEntity();
        merchant.setName("m1");
        merchant.setApiKeyHash("x");

        EnumMap<PaymentProvider, Double> cost = new EnumMap<>(PaymentProvider.class);
        cost.put(PaymentProvider.STRIPE, 0.0);
        cost.put(PaymentProvider.ADYEN, 0.0);
        RoutingConfig cfg = new RoutingConfig("AUTO", new RoutingWeights(1, 0, 0, 0, 0), cost);
        merchant.setConfigJson(mapper.writeValueAsString(cfg));

        UUID paymentIntentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        RoutingEngine.RoutingResult r1 = engine.decide(merchant, paymentIntentId, 1000, "EUR", ProviderPreference.AUTO, Set.of());
        RoutingEngine.RoutingResult r2 = engine.decide(merchant, paymentIntentId, 1000, "EUR", ProviderPreference.AUTO, Set.of());

        assertEquals(r1.chosenProvider(), r2.chosenProvider());
        assertEquals(expectedTieBreak(paymentIntentId), r1.chosenProvider());
    }

    @Test
    void explicitPreferenceMustRespectHardConstraints() throws Exception {
        ProviderHealthReader health = RoutingEngineTest::snapshot;

        RoutingEngine engine = new RoutingEngine(health, new ObjectMapper());

        MerchantEntity merchant = new MerchantEntity();
        merchant.setName("m1");
        merchant.setApiKeyHash("x");
        merchant.setConfigJson(new ObjectMapper().writeValueAsString(RoutingConfig.defaults()));

        assertThrows(IllegalArgumentException.class, () ->
                engine.decide(merchant, UUID.randomUUID(), 1000, "MXN", ProviderPreference.STRIPE, Set.of())
        );
    }

    private static ProviderSnapshot snapshot(PaymentProvider provider) {
        return new ProviderSnapshot(
                provider,
                CircuitState.CLOSED,
                0.5,
                0.0,
                0,
                null,
                Instant.now()
        );
    }

    private static PaymentProvider expectedTieBreak(UUID paymentIntentId) {
        int bucket = Math.floorMod(fnv1a32(paymentIntentId.toString()), 2);
        return bucket == 0 ? PaymentProvider.STRIPE : PaymentProvider.ADYEN;
    }

    private static int fnv1a32(String s) {
        final int FNV_PRIME = 0x01000193;
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
