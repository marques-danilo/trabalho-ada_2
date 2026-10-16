package com.marketplace.checkout.domain.port;

import com.marketplace.checkout.domain.model.CustomerOrder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida do cache de consulta de pedidos (padrao Cache-Aside). */
public interface OrderCachePort {

    Optional<CustomerOrder> find(UUID pedidoId);

    void put(CustomerOrder order, Duration ttl);

    void evict(UUID pedidoId);
}
