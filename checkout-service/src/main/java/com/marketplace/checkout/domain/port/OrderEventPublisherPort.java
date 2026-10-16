package com.marketplace.checkout.domain.port;

import com.marketplace.checkout.application.event.OrderCreatedEvent;

/** Porta de saida para a publicacao de pedidos na fila {@code order-created}. */
public interface OrderEventPublisherPort {

    void publishOrderCreated(OrderCreatedEvent event);
}
