package com.marketplace.checkout.application;

import com.marketplace.checkout.application.event.OrderCompensationEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensateOrderUseCase: devolucao do estoque reservado (SAGA)")
class CompensateOrderUseCaseTest {

    private static final Instant INSTANTE = Instant.parse("2026-09-07T20:04:10Z");

    @Mock
    private StockPort stock;
    @Mock
    private OrderRepositoryPort repository;
    @Mock
    private OrderCachePort cache;

    private CompensateOrderUseCase useCase;
    private CustomerOrder pedido;
    private OrderCompensationEvent evento;

    @BeforeEach
    void setUp() {
        useCase = new CompensateOrderUseCase(
                stock, repository, cache, new CheckoutProperties(),
                Clock.fixed(INSTANTE, ZoneId.of("UTC"))
        );
        pedido = new CustomerOrder(
                UUID.randomUUID(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                OrderStatus.PROCESSING,
                LocalDateTime.of(2026, 9, 7, 20, 3, 57),
                LocalDateTime.of(2026, 9, 7, 20, 3, 57)
        );
        evento = new OrderCompensationEvent(
                pedido.pedidoId(), "PROD-001", 2, "Falha no processamento do pedido"
        );
    }

    @Test
    @DisplayName("devolve a quantidade ao estoque e marca o pedido como CANCELLED")
    void devolveEstoqueECancelaPedido() {
        when(stock.markCompensationOnce(pedido.pedidoId().toString())).thenReturn(true);
        when(stock.release("PROD-001", 2)).thenReturn(10L);
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.of(pedido));

        CompensateOrderUseCase.CompensationResult resultado = useCase.handle(evento);

        assertThat(resultado.estoqueDevolvido()).isTrue();
        assertThat(resultado.saldoAtual()).isEqualTo(10L);

        ArgumentCaptor<CustomerOrder> salvo = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(salvo.getValue().dataHoraAtualizacao())
                .isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 4, 10));

        verify(cache).put(salvo.getValue(), java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("mensagem reentregue nao devolve o estoque duas vezes")
    void compensacaoEIdempotente() {
        when(stock.markCompensationOnce(pedido.pedidoId().toString())).thenReturn(false);
        when(stock.available("PROD-001")).thenReturn(10L);

        CompensateOrderUseCase.CompensationResult resultado = useCase.handle(evento);

        assertThat(resultado.estoqueDevolvido()).isFalse();
        verify(stock, never()).release(any(), anyInt());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("estoque e devolvido mesmo se o pedido nao existir na base do Checkout")
    void devolveEstoqueMesmoSemPedidoLocal() {
        when(stock.markCompensationOnce(pedido.pedidoId().toString())).thenReturn(true);
        when(stock.release("PROD-001", 2)).thenReturn(10L);
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.empty());

        CompensateOrderUseCase.CompensationResult resultado = useCase.handle(evento);

        assertThat(resultado.estoqueDevolvido()).isTrue();
        verify(stock).release("PROD-001", 2);
        verify(repository, never()).save(any());
    }
}
