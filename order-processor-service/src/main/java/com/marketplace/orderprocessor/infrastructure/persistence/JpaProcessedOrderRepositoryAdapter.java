package com.marketplace.orderprocessor.infrastructure.persistence;

import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import com.marketplace.orderprocessor.domain.port.ProcessedOrderRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA da porta de persistencia do Order Processor. */
@Repository
public class JpaProcessedOrderRepositoryAdapter implements ProcessedOrderRepositoryPort {

    private final ProcessedOrderJpaRepository jpaRepository;

    public JpaProcessedOrderRepositoryAdapter(ProcessedOrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ProcessedOrder save(ProcessedOrder order) {
        ProcessedOrderEntity entity = jpaRepository.findById(order.pedidoId())
                .orElseGet(() -> ProcessedOrderEntity.fromDomain(order));
        entity.apply(order);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessedOrder> findById(UUID pedidoId) {
        return jpaRepository.findById(pedidoId).map(ProcessedOrderEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessedOrder> findAll() {
        return jpaRepository.findAll().stream().map(ProcessedOrderEntity::toDomain).toList();
    }
}
