package com.marketplace.checkout.infrastructure.messaging;

import com.marketplace.checkout.application.CompensateOrderUseCase;
import com.marketplace.checkout.application.event.OrderCompensationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Consumer da fila {@code order-compensation}: fecha o ciclo da SAGA devolvendo o estoque.
 *
 * <p>Exceptions sao registradas mas nao propagadas. Propagar faria o RabbitMQ reentregar a
 * mensagem indefinidamente; a devolucao de estoque ja e idempotente no caso de uso, e uma falha
 * aqui precisa de inspecao humana, nao de retry infinito.</p>
 */
@Component
@Profile("!test")
public class OrderCompensationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationListener.class);

    private final CompensateOrderUseCase compensateOrder;

    public OrderCompensationListener(CompensateOrderUseCase compensateOrder) {
        this.compensateOrder = compensateOrder;
    }

    @RabbitListener(queues = QueueNames.ORDER_COMPENSATION)
    public void onCompensation(OrderCompensationEvent event) {
        log.info("Mensagem de compensacao recebida para o pedido {}", event.pedidoId());
        try {
            compensateOrder.handle(event);
        } catch (RuntimeException e) {
            log.error("Falha ao compensar o pedido {}", event.pedidoId(), e);
        }
    }
}
