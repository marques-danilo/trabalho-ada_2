package com.marketplace.checkout.infrastructure.messaging;

import com.marketplace.checkout.application.event.OrderCreatedEvent;
import com.marketplace.checkout.domain.port.OrderEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Producer da fila {@code order-created}. */
@Component
@Profile("!test")
public class RabbitOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitOrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitOrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(QueueNames.ORDER_CREATED, event);
        log.info("Pedido {} publicado na fila {}", event.pedidoId(), QueueNames.ORDER_CREATED);
    }
}
