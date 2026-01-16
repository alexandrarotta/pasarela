package com.pasarela.application;

import com.pasarela.infrastructure.persistence.entity.IdempotencyRecordEntity;
import com.pasarela.infrastructure.persistence.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public Optional<UUID> findExisting(UUID merchantId, String endpoint, String idempotencyKey) {
        return idempotencyRecordRepository
                .findByMerchantIdAndEndpointAndIdempotencyKey(merchantId, endpoint, idempotencyKey)
                .map(IdempotencyRecordEntity::getPaymentIntentId);
    }

    public void record(UUID merchantId, String endpoint, String idempotencyKey, UUID paymentIntentId, String requestHash) {
        IdempotencyRecordEntity rec = new IdempotencyRecordEntity();
        rec.setMerchantId(merchantId);
        rec.setEndpoint(endpoint);
        rec.setIdempotencyKey(idempotencyKey);
        rec.setPaymentIntentId(paymentIntentId);
        rec.setRequestHash(requestHash);
        idempotencyRecordRepository.save(rec);
    }
}

