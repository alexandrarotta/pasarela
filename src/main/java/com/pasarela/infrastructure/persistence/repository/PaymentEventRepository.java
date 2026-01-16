package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.PaymentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, UUID> {
    @Query("""
            select e from PaymentEventEntity e
            where e.provider = :provider
              and e.eventType in :eventTypes
              and e.createdAt >= :from
            order by e.createdAt desc
            """)
    List<PaymentEventEntity> findRecentByProviderAndTypes(
            @Param("provider") PaymentProvider provider,
            @Param("eventTypes") List<String> eventTypes,
            @Param("from") Instant from
    );

    @Query("""
            select e from PaymentEventEntity e
            where e.provider = :provider
              and e.eventType = :eventType
              and e.createdAt >= :from
            order by e.createdAt desc
            """)
    List<PaymentEventEntity> findRecentByProviderAndType(
            @Param("provider") PaymentProvider provider,
            @Param("eventType") String eventType,
            @Param("from") Instant from
    );

    @Query("""
            select count(e) from PaymentEventEntity e
            where e.provider = :provider
              and e.eventType = :eventType
              and e.createdAt >= :from
            """)
    long countByProviderAndTypeSince(
            @Param("provider") PaymentProvider provider,
            @Param("eventType") String eventType,
            @Param("from") Instant from
    );
}

