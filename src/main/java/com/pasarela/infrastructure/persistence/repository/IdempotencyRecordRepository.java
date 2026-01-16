package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.infrastructure.persistence.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, UUID> {
    Optional<IdempotencyRecordEntity> findByMerchantIdAndEndpointAndIdempotencyKey(UUID merchantId, String endpoint, String idempotencyKey);
}

