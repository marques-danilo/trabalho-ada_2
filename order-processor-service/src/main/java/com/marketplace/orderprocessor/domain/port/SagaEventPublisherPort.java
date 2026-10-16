package com.marketplace.orderprocessor.domain.port;

import com.marketplace.orderprocessor.application.event.OrderCompensationEvent;
import com.marketplace.orderprocessor.application.event.OrderStatusChangedEvent;

/** Porta de saida para as filas publicadas pelo Order Processor. */
public interface SagaEventPublisherPort {

    /** Pede ao Checkout que devolva ao Redis o estoque reservado pelo pedido. */
    void publishCompensation(OrderCompensationEvent event);

    /** Informa o desfecho do processamento para que o Checkout atualize sua propria base. */
    void publishStatusChanged(OrderStatusChangedEvent event);
}
