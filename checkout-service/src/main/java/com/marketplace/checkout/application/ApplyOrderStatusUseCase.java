package com.marketplace.checkout.application;

import com.marketplace.checkout.application.event.OrderStatusChangedEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Aplica na base e no cache do Checkout o desfecho informado pelo Order Processor.
 *
 * <p>Mantem as duas bases segregadas: o Checkout nunca le a base do Order Processor, apenas
 * reage ao evento de mudanca de status.</p>
 */
@Service
public class ApplyOrderStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplyOrderStatusUseCase.class);

    private final OrderRepositoryPort repository;
    private final OrderCachePort cache;
    private final CheckoutProperties properties;
    private final Clock clock;

    public ApplyOrderStatusUseCase(OrderRepositoryPort repository,
                                   OrderCachePort cache,
                                   CheckoutProperties properties,
                                   Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.properties = properties;
        this.clock = clock;
    }

    public void handle(OrderStatusChangedEvent event) {
        Optional<CustomerOrder> existente = repository.findById(event.pedidoId());
        if (existente.isEmpty()) {
            log.warn("Status {} recebido para pedido {} inexistente na base do Checkout",
                    event.status(), event.pedidoId());
            return;
        }

        CustomerOrder order = existente.get();

        // CANCELLED e um estado final: um CONFIRMED atrasado ou reentregue nao pode sobrescreve-lo.
        if (order.status() == OrderStatus.CANCELLED && event.status() == OrderStatus.CONFIRMED) {
            log.warn("Ignorando CONFIRMED para pedido {} que ja esta CANCELLED", event.pedidoId());
            return;
        }
        if (order.status() == event.status()) {
            return;
        }

        LocalDateTime atualizadoEm = event.dataHoraAtualizacao() != null
                ? event.dataHoraAtualizacao()
                : LocalDateTime.now(clock);
        CustomerOrder atualizado = order.withStatus(event.status(), atualizadoEm);
        repository.save(atualizado);
        cache.put(atualizado, properties.getCacheTtl());
        log.info("Pedido {} atualizado para {}", event.pedidoId(), event.status());
    }
}
