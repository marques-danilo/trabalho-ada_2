package com.marketplace.orderprocessor.application;

import com.marketplace.orderprocessor.application.event.OrderCompensationEvent;
import com.marketplace.orderprocessor.application.event.OrderCreatedEvent;
import com.marketplace.orderprocessor.application.event.OrderStatusChangedEvent;
import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.model.OrderStatus;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import com.marketplace.orderprocessor.domain.port.ProcessedOrderRepositoryPort;
import com.marketplace.orderprocessor.domain.port.SagaEventPublisherPort;
import com.marketplace.orderprocessor.domain.service.FailureSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessOrderUseCase: confirmacao, falha e compensacao")
class ProcessOrderUseCaseTest {

    private static final Instant INSTANTE = Instant.parse("2026-09-07T20:04:10Z");

    @Mock
    private ProcessedOrderRepositoryPort repository;
    @Mock
    private SagaEventPublisherPort publisher;
    @Mock
    private FailureSimulator failureSimulator;

    private ProcessOrderUseCase useCase;
    private OrderCreatedEvent evento;

    @BeforeEach
    void setUp() {
        useCase = new ProcessOrderUseCase(
                repository, publisher, failureSimulator, new ProcessorProperties(),
                Clock.fixed(INSTANTE, ZoneId.of("UTC"))
        );
        evento = new OrderCreatedEvent(
                UUID.randomUUID(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                LocalDateTime.of(2026, 9, 7, 20, 3, 57)
        );
    }

    /** Faz o repositorio devolver o que recebeu, como um banco real faria. */
    private void repositorioEcoa() {
        when(repository.save(any(ProcessedOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("sucesso: persiste o pedido como CONFIRMED e publica o status")
    void fluxoDeSucesso() {
        when(repository.findById(evento.pedidoId())).thenReturn(Optional.empty());
        repositorioEcoa();
        when(failureSimulator.shouldFail(any())).thenReturn(Optional.empty());

        ProcessedOrder resultado = useCase.handle(evento);

        assertThat(resultado.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(resultado.motivoFalha()).isNull();
        assertThat(resultado.dataHoraCriacao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 3, 57));
        assertThat(resultado.dataHoraAtualizacao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 4, 10));

        verify(publisher, never()).publishCompensation(any());

        ArgumentCaptor<OrderStatusChangedEvent> status =
                ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(publisher).publishStatusChanged(status.capture());
        assertThat(status.getValue().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(status.getValue().pedidoId()).isEqualTo(evento.pedidoId());
    }

    @Test
    @DisplayName("falha de regra: marca CANCELLED e publica a compensacao com produto e quantidade")
    void fluxoDeFalhaPublicaCompensacao() {
        when(repository.findById(evento.pedidoId())).thenReturn(Optional.empty());
        repositorioEcoa();
        when(failureSimulator.shouldFail(any()))
                .thenReturn(Optional.of("Falha no processamento do pedido"));

        ProcessedOrder resultado = useCase.handle(evento);

        assertThat(resultado.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(resultado.motivoFalha()).isEqualTo("Falha no processamento do pedido");

        ArgumentCaptor<OrderCompensationEvent> compensacao =
                ArgumentCaptor.forClass(OrderCompensationEvent.class);
        verify(publisher).publishCompensation(compensacao.capture());
        assertThat(compensacao.getValue().pedidoId()).isEqualTo(evento.pedidoId());
        assertThat(compensacao.getValue().produtoId()).isEqualTo("PROD-001");
        assertThat(compensacao.getValue().quantidade()).isEqualTo(2);
        assertThat(compensacao.getValue().motivo()).isEqualTo("Falha no processamento do pedido");

        ArgumentCaptor<OrderStatusChangedEvent> status =
                ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(publisher).publishStatusChanged(status.capture());
        assertThat(status.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("falha de persistencia tambem dispara a compensacao do estoque")
    void falhaDePersistenciaCompensaEstoque() {
        when(repository.findById(evento.pedidoId())).thenReturn(Optional.empty());
        when(repository.save(any(ProcessedOrder.class)))
                .thenThrow(new RuntimeException("banco indisponivel"));

        ProcessedOrder resultado = useCase.handle(evento);

        assertThat(resultado.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(publisher).publishCompensation(any(OrderCompensationEvent.class));
    }

    @Test
    @DisplayName("mensagem reentregue de pedido ja CONFIRMED nao reprocessa nem compensa")
    void naoReprocessaPedidoConfirmado() {
        ProcessedOrder jaConfirmado = ProcessedOrder.received(
                evento.pedidoId(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                evento.dataHoraCriacao(), LocalDateTime.now()
        ).confirm(LocalDateTime.now());
        when(repository.findById(evento.pedidoId())).thenReturn(Optional.of(jaConfirmado));

        ProcessedOrder resultado = useCase.handle(evento);

        assertThat(resultado.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(repository, never()).save(any());
        verify(publisher, never()).publishCompensation(any());
        verify(publisher, never()).publishStatusChanged(any());
    }

    @Test
    @DisplayName("mensagem reentregue de pedido ja CANCELLED nao gera segunda compensacao")
    void naoCompensaDuasVezes() {
        ProcessedOrder jaCancelado = ProcessedOrder.received(
                evento.pedidoId(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                evento.dataHoraCriacao(), LocalDateTime.now()
        ).cancel("Falha anterior", LocalDateTime.now());
        when(repository.findById(evento.pedidoId())).thenReturn(Optional.of(jaCancelado));

        ProcessedOrder resultado = useCase.handle(evento);

        assertThat(resultado.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(publisher, never()).publishCompensation(any());
    }
}
