package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.ProviderHealthSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderHealthSnapshotRepository extends JpaRepository<ProviderHealthSnapshotEntity, UUID> {
    Optional<ProviderHealthSnapshotEntity> findByProvider(PaymentProvider provider);
}

