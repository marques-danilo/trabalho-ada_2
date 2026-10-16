package com.marketplace.checkout.infrastructure.messaging;

import com.marketplace.checkout.application.ApplyOrderStatusUseCase;
import com.marketplace.checkout.application.event.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Consumer da fila {@code order-status-changed}: espelha o desfecho do processamento. */
@Component
@Profile("!test")
public class OrderStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedListener.class);

    private final ApplyOrderStatusUseCase applyOrderStatus;

    public OrderStatusChangedListener(ApplyOrderStatusUseCase applyOrderStatus) {
        this.applyOrderStatus = applyOrderStatus;
    }

    @RabbitListener(queues = QueueNames.ORDER_STATUS_CHANGED)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        log.info("Status {} recebido para o pedido {}", event.status(), event.pedidoId());
        try {
            applyOrderStatus.handle(event);
        } catch (RuntimeException e) {
            log.error("Falha ao aplicar o status do pedido {}", event.pedidoId(), e);
        }
    }
}
