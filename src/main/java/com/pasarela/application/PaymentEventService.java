package com.pasarela.application;

import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.crypto.Sha256;
import com.pasarela.infrastructure.persistence.entity.PaymentEventEntity;
import com.pasarela.infrastructure.persistence.repository.PaymentEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentEventService {
    private final PaymentEventRepository paymentEventRepository;

    public PaymentEventService(PaymentEventRepository paymentEventRepository) {
        this.paymentEventRepository = paymentEventRepository;
    }

    public void record(PaymentProvider provider, UUID paymentIntentId, String eventType, String payloadForHash, String sanitizedPayloadJson) {
        PaymentEventEntity event = new PaymentEventEntity();
        event.setProvider(provider);
        event.setPaymentIntentId(paymentIntentId);
        event.setEventType(eventType);
        event.setPayloadHash(Sha256.hex(payloadForHash == null ? "" : payloadForHash));
        event.setSanitizedPayloadJson(sanitizedPayloadJson);
        paymentEventRepository.save(event);
    }
}

