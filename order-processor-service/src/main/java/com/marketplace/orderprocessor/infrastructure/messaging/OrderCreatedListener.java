package com.marketplace.orderprocessor.infrastructure.messaging;

import com.marketplace.orderprocessor.application.ProcessOrderUseCase;
import com.marketplace.orderprocessor.application.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Consumer da fila {@code order-created}.
 *
 * <p>O caso de uso ja converte qualquer falha em CANCELLED + compensacao, entao nao propagamos
 * excecoes: fazer isso devolveria a mensagem para a fila e geraria compensacoes repetidas.</p>
 */
@Component
@Profile("!test")
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final ProcessOrderUseCase processOrder;

    public OrderCreatedListener(ProcessOrderUseCase processOrder) {
        this.processOrder = processOrder;
    }

    @RabbitListener(queues = QueueNames.ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Pedido {} recebido da fila {}", event.pedidoId(), QueueNames.ORDER_CREATED);
        try {
            processOrder.handle(event);
        } catch (RuntimeException e) {
            log.error("Falha irrecuperavel ao processar o pedido {}", event.pedidoId(), e);
        }
    }
}
