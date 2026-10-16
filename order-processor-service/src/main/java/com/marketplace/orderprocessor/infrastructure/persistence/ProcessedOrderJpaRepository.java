package com.marketplace.orderprocessor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedOrderJpaRepository extends JpaRepository<ProcessedOrderEntity, UUID> {
}
