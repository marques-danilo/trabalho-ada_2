package com.marketplace.orderprocessor.application;

import com.marketplace.orderprocessor.application.event.OrderCompensationEvent;
import com.marketplace.orderprocessor.application.event.OrderCreatedEvent;
import com.marketplace.orderprocessor.application.event.OrderStatusChangedEvent;
import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.exception.OrderProcessingException;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import com.marketplace.orderprocessor.domain.port.ProcessedOrderRepositoryPort;
import com.marketplace.orderprocessor.domain.port.SagaEventPublisherPort;
import com.marketplace.orderprocessor.domain.service.FailureSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Nucleo do Order Processor: persiste o pedido e decide o desfecho da SAGA.
 *
 * <p>Sucesso leva o pedido a {@code CONFIRMED}. Falha leva a {@code CANCELLED} e publica na fila
 * {@code order-compensation}, que o Checkout consome para devolver o estoque ao Redis. Em ambos
 * os casos o desfecho tambem e publicado em {@code order-status-changed}, mantendo a base do
 * Checkout coerente sem que um servico leia a base do outro.</p>
 */
@Service
public class ProcessOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessOrderUseCase.class);

    private final ProcessedOrderRepositoryPort repository;
    private final SagaEventPublisherPort publisher;
    private final FailureSimulator failureSimulator;
    private final ProcessorProperties properties;
    private final Clock clock;

    public ProcessOrderUseCase(ProcessedOrderRepositoryPort repository,
                               SagaEventPublisherPort publisher,
                               FailureSimulator failureSimulator,
                               ProcessorProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.failureSimulator = failureSimulator;
        this.properties = properties;
        this.clock = clock;
    }

    public ProcessedOrder handle(OrderCreatedEvent event) {
        // Reentrega de mensagem nao deve reprocessar nem disparar uma segunda compensacao.
        Optional<ProcessedOrder> existente = repository.findById(event.pedidoId());
        if (existente.isPresent() && existente.get().isFinalizado()) {
            log.info("Pedido {} ja processado com status {}; mensagem ignorada",
                    event.pedidoId(), existente.get().status());
            return existente.get();
        }

        ProcessedOrder order = existente.orElseGet(() -> ProcessedOrder.received(
                event.pedidoId(),
                event.produtoId(),
                event.quantidade(),
                event.clienteCpf(),
                event.clienteNome(),
                event.dataHoraCriacao(),
                LocalDateTime.now(clock)
        ));
        try {
            // A persistencia entra no try junto com a regra de negocio: uma falha de banco
            // tambem precisa disparar a compensacao do estoque ja reservado.
            order = repository.save(order);
            aplicarAtrasoDeProcessamento();

            Optional<String> motivoFalha = failureSimulator.shouldFail(order);
            if (motivoFalha.isPresent()) {
                throw new OrderProcessingException(motivoFalha.get());
            }
            return confirmar(order);
        } catch (RuntimeException e) {
            return cancelarECompensar(order, e);
        }
    }

    private ProcessedOrder confirmar(ProcessedOrder order) {
        ProcessedOrder confirmado = repository.save(order.confirm(LocalDateTime.now(clock)));
        publisher.publishStatusChanged(OrderStatusChangedEvent.from(confirmado));
        log.info("Pedido {} CONFIRMED (produto={} quantidade={})",
                confirmado.pedidoId(), confirmado.produtoId(), confirmado.quantidade());
        return confirmado;
    }

    private ProcessedOrder cancelarECompensar(ProcessedOrder order, RuntimeException causa) {
        String motivo = causa instanceof OrderProcessingException
                ? causa.getMessage()
                : "Falha no processamento do pedido";
        log.warn("Pedido {} CANCELLED: {}", order.pedidoId(), motivo);

        ProcessedOrder cancelado = order.cancel(motivo, LocalDateTime.now(clock));
        try {
            cancelado = repository.save(cancelado);
        } catch (RuntimeException e) {
            // Se o banco for a propria causa da falha, nao conseguimos registrar o CANCELLED.
            // Ainda assim publicamos a compensacao: deixar estoque reservado para sempre e pior
            // do que perder o registro local do cancelamento.
            log.error("Nao foi possivel gravar o cancelamento do pedido {}; compensacao sera publicada",
                    cancelado.pedidoId(), e);
        }

        publisher.publishCompensation(OrderCompensationEvent.from(cancelado, motivo));
        publisher.publishStatusChanged(OrderStatusChangedEvent.from(cancelado));
        return cancelado;
    }

    private void aplicarAtrasoDeProcessamento() {
        long millis = properties.getProcessingDelay().toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
