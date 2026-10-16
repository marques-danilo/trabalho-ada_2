package com.marketplace.checkout.infrastructure.persistence;

import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA da porta de persistencia do Checkout. */
@Repository
public class JpaOrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;

    public JpaOrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public CustomerOrder save(CustomerOrder order) {
        OrderEntity entity = jpaRepository.findById(order.pedidoId())
                .orElseGet(() -> OrderEntity.fromDomain(order));
        entity.apply(order);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findById(UUID pedidoId) {
        return jpaRepository.findById(pedidoId).map(OrderEntity::toDomain);
    }
}
