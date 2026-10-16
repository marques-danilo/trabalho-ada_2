package com.marketplace.checkout.infrastructure.memory;

import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.port.OrderCachePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Substituto do cache Redis nos testes.
 *
 * <p>Conta leituras e escritas para que os testes de Cache-Aside possam afirmar que houve hit ou
 * miss, e nao apenas que o valor retornado estava correto.</p>
 */
@Repository
@Profile("test")
public class InMemoryOrderCacheAdapter implements OrderCachePort {

    private final Map<UUID, CustomerOrder> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Duration> ttls = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();

    @Override
    public Optional<CustomerOrder> find(UUID pedidoId) {
        CustomerOrder order = cache.get(pedidoId);
        if (order == null) {
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        return Optional.of(order);
    }

    @Override
    public void put(CustomerOrder order, Duration ttl) {
        cache.put(order.pedidoId(), order);
        ttls.put(order.pedidoId(), ttl);
    }

    @Override
    public void evict(UUID pedidoId) {
        cache.remove(pedidoId);
        ttls.remove(pedidoId);
    }

    public Duration ttlOf(UUID pedidoId) {
        return ttls.get(pedidoId);
    }

    public int hits() {
        return hits.get();
    }

    public int misses() {
        return misses.get();
    }

    public void reset() {
        cache.clear();
        ttls.clear();
        hits.set(0);
        misses.set(0);
    }
}
