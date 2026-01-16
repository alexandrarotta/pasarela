package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.domain.model.PaymentStatus;
import com.pasarela.infrastructure.persistence.entity.PaymentIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {
    Optional<PaymentIntentEntity> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<PaymentIntentEntity> findTop200ByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    @Query("""
            select p from PaymentIntentEntity p
            where p.merchantId = :merchantId
              and (:status is null or p.status = :status)
              and (:from is null or p.createdAt >= :from)
              and (:to is null or p.createdAt <= :to)
            order by p.createdAt desc
            """)
    List<PaymentIntentEntity> search(
            @Param("merchantId") UUID merchantId,
            @Param("status") PaymentStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    long countByRootPaymentIntentId(UUID rootPaymentIntentId);

    @Query("""
            select p from PaymentIntentEntity p
            where p.providerRef = :providerRef
              and p.provider = com.pasarela.domain.model.PaymentProvider.STRIPE
            """)
    Optional<PaymentIntentEntity> findStripeByProviderRef(@Param("providerRef") String providerRef);
}

