package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.infrastructure.persistence.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<MerchantEntity, UUID> {
    Optional<MerchantEntity> findByApiKeyHash(String apiKeyHash);
}

