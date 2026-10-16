package com.marketplace.checkout.infrastructure.memory;

import com.marketplace.checkout.application.event.OrderCreatedEvent;
import com.marketplace.checkout.domain.port.OrderEventPublisherPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Substituto do producer RabbitMQ nos testes: guarda as mensagens publicadas para inspecao.
 */
@Component
@Profile("test")
public class InMemoryOrderEventPublisher implements OrderEventPublisherPort {

    private final List<OrderCreatedEvent> publicados = new CopyOnWriteArrayList<>();
    private volatile RuntimeException falhaSimulada;

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        if (falhaSimulada != null) {
            throw falhaSimulada;
        }
        publicados.add(event);
    }

    public List<OrderCreatedEvent> publicados() {
        return List.copyOf(publicados);
    }

    /** Faz a proxima publicacao falhar, para exercitar o rollback local da reserva. */
    public void simularFalha(RuntimeException falha) {
        this.falhaSimulada = falha;
    }

    public void reset() {
        publicados.clear();
        falhaSimulada = null;
    }
}
