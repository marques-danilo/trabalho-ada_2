package com.marketplace.checkout.application;

import com.marketplace.checkout.application.command.CreateOrderCommand;
import com.marketplace.checkout.application.event.OrderCreatedEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.exception.InsufficientStockException;
import com.marketplace.checkout.domain.exception.InvalidOrderException;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderEventPublisherPort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import com.marketplace.checkout.domain.port.StockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderUseCase: reserva de estoque e publicacao do pedido")
class CreateOrderUseCaseTest {

    private static final ZoneId ZONA = ZoneId.of("UTC");
    private static final Instant INSTANTE = Instant.parse("2026-09-07T20:03:57Z");

    @Mock
    private StockPort stock;
    @Mock
    private OrderRepositoryPort repository;
    @Mock
    private OrderEventPublisherPort publisher;
    @Mock
    private OrderCachePort cache;

    private CreateOrderUseCase useCase;
    private CheckoutProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CheckoutProperties();
        useCase = new CreateOrderUseCase(
                stock, repository, publisher, cache, properties, Clock.fixed(INSTANTE, ZONA)
        );
    }

    private CreateOrderCommand comando(int quantidade) {
        return new CreateOrderCommand("PROD-001", quantidade, "50329291076", "Fernando Augusto");
    }

    @Test
    @DisplayName("com saldo suficiente, reserva o estoque, persiste, publica e devolve PROCESSING")
    void fluxoDeSucesso() {
        when(stock.reserve("PROD-001", 2)).thenReturn(StockPort.ReservationResult.reserved(8));

        CustomerOrder order = useCase.handle(comando(2));

        assertThat(order.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.pedidoId()).isNotNull();
        assertThat(order.dataHoraRequisicao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 3, 57));

        verify(stock).reserve("PROD-001", 2);
        verify(repository).save(order);

        ArgumentCaptor<OrderCreatedEvent> evento = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(publisher).publishOrderCreated(evento.capture());
        assertThat(evento.getValue().pedidoId()).isEqualTo(order.pedidoId());
        assertThat(evento.getValue().produtoId()).isEqualTo("PROD-001");
        assertThat(evento.getValue().quantidade()).isEqualTo(2);
        assertThat(evento.getValue().clienteCpf()).isEqualTo("50329291076");

        verify(cache).put(order, Duration.ofMinutes(5));
        verify(stock, never()).release(any(), anyInt());
    }

    @Test
    @DisplayName("sem saldo suficiente, falha com 409 e nao publica nada")
    void saldoInsuficiente() {
        when(stock.reserve("PROD-001", 99)).thenReturn(StockPort.ReservationResult.insufficient());

        assertThatThrownBy(() -> useCase.handle(comando(99)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Estoque insuficiente para o produto PROD-001");

        verifyNoInteractions(repository, publisher, cache);
    }

    @Test
    @DisplayName("produto sem estoque cadastrado tambem e tratado como conflito")
    void produtoInexistente() {
        when(stock.reserve("PROD-001", 1)).thenReturn(StockPort.ReservationResult.productNotFound());

        assertThatThrownBy(() -> useCase.handle(comando(1)))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(repository, publisher, cache);
    }

    @Test
    @DisplayName("se a publicacao falhar, devolve o estoque reservado na hora")
    void devolveEstoqueQuandoPublicacaoFalha() {
        when(stock.reserve("PROD-001", 2)).thenReturn(StockPort.ReservationResult.reserved(8));
        doThrow(new AmqpIndisponivelException())
                .when(publisher).publishOrderCreated(any(OrderCreatedEvent.class));

        assertThatThrownBy(() -> useCase.handle(comando(2)))
                .isInstanceOf(AmqpIndisponivelException.class);

        // Nenhum consumidor recebeu o pedido, portanto ninguem vai compensar: o proprio
        // caso de uso precisa desfazer a reserva.
        verify(stock).release("PROD-001", 2);
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("se a persistencia falhar, devolve o estoque e nao publica o pedido")
    void devolveEstoqueQuandoPersistenciaFalha() {
        when(stock.reserve("PROD-001", 3)).thenReturn(StockPort.ReservationResult.reserved(7));
        when(repository.save(any(CustomerOrder.class))).thenThrow(new AmqpIndisponivelException());

        assertThatThrownBy(() -> useCase.handle(
                new CreateOrderCommand("PROD-001", 3, "50329291076", "Fernando Augusto")
        )).isInstanceOf(AmqpIndisponivelException.class);

        verify(stock).release("PROD-001", 3);
        verifyNoInteractions(publisher, cache);
    }

    /** Simula uma indisponibilidade de infraestrutura durante a criacao do pedido. */
    private static class AmqpIndisponivelException extends RuntimeException {
        AmqpIndisponivelException() {
            super("broker indisponivel");
        }
    }

    @Test
    @DisplayName("entrada invalida e recusada antes de tocar o estoque")
    void validaAntesDeReservar() {
        assertThatThrownBy(() -> useCase.handle(comando(0)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("quantidade");

        verifyNoInteractions(stock, repository, publisher, cache);
    }

    @Test
    @DisplayName("usa o TTL configurado ao popular o cache")
    void respeitaTtlConfigurado() {
        properties.setCacheTtl(Duration.ofMinutes(1));
        when(stock.reserve("PROD-001", 1)).thenReturn(StockPort.ReservationResult.reserved(9));

        useCase.handle(comando(1));

        verify(cache).put(any(CustomerOrder.class), eq(Duration.ofMinutes(1)));
    }
}
