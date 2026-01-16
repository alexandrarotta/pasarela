package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.domain.model.PaymentProvider;
import com.pasarela.infrastructure.persistence.entity.RoutingDecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RoutingDecisionRepository extends JpaRepository<RoutingDecisionEntity, UUID> {
    @Query("""
            select r from RoutingDecisionEntity r
            where (:from is null or r.createdAt >= :from)
              and (:to is null or r.createdAt <= :to)
              and (:provider is null or r.chosenProvider = :provider)
            order by r.createdAt desc
            """)
    List<RoutingDecisionEntity> search(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("provider") PaymentProvider provider
    );
}

