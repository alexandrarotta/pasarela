package com.pasarela.infrastructure.persistence.repository;

import com.pasarela.infrastructure.persistence.entity.PaymentIntentPrivateDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentIntentPrivateDataRepository extends JpaRepository<PaymentIntentPrivateDataEntity, UUID> {
}

