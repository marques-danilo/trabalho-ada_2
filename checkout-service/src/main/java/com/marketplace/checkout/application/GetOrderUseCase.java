package com.marketplace.checkout.application;

import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.exception.OrderNotFoundException;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta de pedido com Cache-Aside e retentativas.
 *
 * <p>Fluxo: busca no Redis; em caso de miss, consulta a base do Checkout e repovoa o cache com
 * TTL configurado. Como a criacao do pedido responde 202 antes do processamento terminar, uma
 * consulta imediata pode nao encontrar nada ainda; por isso sao feitas ate tres tentativas antes
 * de devolver 404.</p>
 */
@Service
public class GetOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetOrderUseCase.class);

    private final OrderCachePort cache;
    private final OrderRepositoryPort repository;
    private final CheckoutProperties properties;

    public GetOrderUseCase(OrderCachePort cache,
                           OrderRepositoryPort repository,
                           CheckoutProperties properties) {
        this.cache = cache;
        this.repository = repository;
        this.properties = properties;
    }

    public CustomerOrder handle(UUID pedidoId) {
        int tentativas = Math.max(1, properties.getQueryAttempts());

        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            Optional<CustomerOrder> cacheHit = cache.find(pedidoId);
            if (cacheHit.isPresent()) {
                log.debug("Cache HIT para pedido {} na tentativa {}", pedidoId, tentativa);
                return cacheHit.get();
            }

            Optional<CustomerOrder> doBanco = repository.findById(pedidoId);
            if (doBanco.isPresent()) {
                CustomerOrder order = doBanco.get();
                log.debug("Cache MISS para pedido {}; encontrado no banco na tentativa {}", pedidoId, tentativa);
                cache.put(order, properties.getCacheTtl());
                return order;
            }

            log.debug("Pedido {} nao encontrado na tentativa {}/{}", pedidoId, tentativa, tentativas);
            if (tentativa < tentativas) {
                esperarAntesDaProximaTentativa();
            }
        }

        throw new OrderNotFoundException(pedidoId);
    }

    private void esperarAntesDaProximaTentativa() {
        long millis = properties.getQueryRetryDelay().toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
