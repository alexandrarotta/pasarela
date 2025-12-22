package com.pasarela.infrastructure.checkout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasarela.infrastructure.crypto.AesGcmCrypto;
import com.pasarela.infrastructure.persistence.entity.PaymentIntentPrivateDataEntity;
import com.pasarela.infrastructure.persistence.repository.PaymentIntentPrivateDataRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutConfigStore {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final PaymentIntentPrivateDataRepository privateDataRepository;
    private final AesGcmCrypto crypto;
    private final ObjectMapper objectMapper;

    public CheckoutConfigStore(
            PaymentIntentPrivateDataRepository privateDataRepository,
            AesGcmCrypto crypto,
            ObjectMapper objectMapper
    ) {
        this.privateDataRepository = privateDataRepository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
    }

    public void upsert(UUID paymentIntentId, Map<String, Object> checkoutConfig) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(checkoutConfig);
            String enc = crypto.encryptToString(json);

            PaymentIntentPrivateDataEntity entity = privateDataRepository.findById(paymentIntentId)
                    .orElseGet(() -> {
                        PaymentIntentPrivateDataEntity e = new PaymentIntentPrivateDataEntity();
                        e.setPaymentIntentId(paymentIntentId);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });
            entity.setCheckoutConfigEnc(enc);
            privateDataRepository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("checkout config store failed", e);
        }
    }

    public Optional<Map<String, Object>> get(UUID paymentIntentId) {
        return privateDataRepository.findById(paymentIntentId).map(entity -> {
            try {
                byte[] json = crypto.decryptToBytes(entity.getCheckoutConfigEnc());
                return objectMapper.readValue(new String(json, StandardCharsets.UTF_8), MAP);
            } catch (Exception e) {
                throw new IllegalStateException("checkout config decrypt failed", e);
            }
        });
    }
}
