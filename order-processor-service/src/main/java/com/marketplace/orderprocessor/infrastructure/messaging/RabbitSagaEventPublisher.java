package com.marketplace.orderprocessor.infrastructure.messaging;

import com.marketplace.orderprocessor.application.event.OrderCompensationEvent;
import com.marketplace.orderprocessor.application.event.OrderStatusChangedEvent;
import com.marketplace.orderprocessor.domain.port.SagaEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Producer das filas {@code order-compensation} e {@code order-status-changed}. */
@Component
@Profile("!test")
public class RabbitSagaEventPublisher implements SagaEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitSagaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitSagaEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCompensation(OrderCompensationEvent event) {
        rabbitTemplate.convertAndSend(QueueNames.ORDER_COMPENSATION, event);
        log.info("Compensacao do pedido {} publicada na fila {} (produto={} quantidade={})",
                event.pedidoId(), QueueNames.ORDER_COMPENSATION, event.produtoId(), event.quantidade());
    }

    @Override
    public void publishStatusChanged(OrderStatusChangedEvent event) {
        rabbitTemplate.convertAndSend(QueueNames.ORDER_STATUS_CHANGED, event);
        log.info("Status {} do pedido {} publicado na fila {}",
                event.status(), event.pedidoId(), QueueNames.ORDER_STATUS_CHANGED);
    }
}
