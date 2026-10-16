package com.marketplace.orderprocessor.infrastructure.memory;

import com.marketplace.orderprocessor.application.event.OrderCompensationEvent;
import com.marketplace.orderprocessor.application.event.OrderStatusChangedEvent;
import com.marketplace.orderprocessor.domain.port.SagaEventPublisherPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Substituto do producer RabbitMQ nos testes: registra o que seria publicado em cada fila.
 */
@Component
@Profile("test")
public class InMemorySagaEventPublisher implements SagaEventPublisherPort {

    private final List<OrderCompensationEvent> compensacoes = new CopyOnWriteArrayList<>();
    private final List<OrderStatusChangedEvent> statusChanges = new CopyOnWriteArrayList<>();

    @Override
    public void publishCompensation(OrderCompensationEvent event) {
        compensacoes.add(event);
    }

    @Override
    public void publishStatusChanged(OrderStatusChangedEvent event) {
        statusChanges.add(event);
    }

    public List<OrderCompensationEvent> compensacoes() {
        return List.copyOf(compensacoes);
    }

    public List<OrderStatusChangedEvent> statusChanges() {
        return List.copyOf(statusChanges);
    }

    public void reset() {
        compensacoes.clear();
        statusChanges.clear();
    }
}
