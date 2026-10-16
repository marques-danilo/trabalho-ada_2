package com.marketplace.checkout.application;

import com.marketplace.checkout.application.event.OrderStatusChangedEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
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
@DisplayName("ApplyOrderStatusUseCase: sincronizacao do desfecho do processamento")
class ApplyOrderStatusUseCaseTest {

    @Mock
    private OrderRepositoryPort repository;
    @Mock
    private OrderCachePort cache;

    private ApplyOrderStatusUseCase useCase;
    private CustomerOrder pedido;

    @BeforeEach
    void setUp() {
        useCase = new ApplyOrderStatusUseCase(
                repository, cache, new CheckoutProperties(),
                Clock.fixed(Instant.parse("2026-09-07T20:04:10Z"), ZoneId.of("UTC"))
        );
        pedido = new CustomerOrder(
                UUID.randomUUID(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                OrderStatus.PROCESSING,
                LocalDateTime.of(2026, 9, 7, 20, 3, 57),
                LocalDateTime.of(2026, 9, 7, 20, 3, 57)
        );
    }

    @Test
    @DisplayName("aplica CONFIRMED na base e no cache")
    void aplicaConfirmed() {
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.of(pedido));
        LocalDateTime atualizadoEm = LocalDateTime.of(2026, 9, 7, 20, 4, 10);

        useCase.handle(new OrderStatusChangedEvent(pedido.pedidoId(), OrderStatus.CONFIRMED, atualizadoEm));

        ArgumentCaptor<CustomerOrder> salvo = ArgumentCaptor.forClass(CustomerOrder.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(salvo.getValue().dataHoraAtualizacao()).isEqualTo(atualizadoEm);
        verify(cache).put(salvo.getValue(), java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("CANCELLED e final: um CONFIRMED atrasado nao sobrescreve o cancelamento")
    void naoSobrescreveCancelado() {
        CustomerOrder cancelado = pedido.withStatus(OrderStatus.CANCELLED, LocalDateTime.now());
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.of(cancelado));

        useCase.handle(new OrderStatusChangedEvent(
                pedido.pedidoId(), OrderStatus.CONFIRMED, LocalDateTime.now()
        ));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("evento repetido com o mesmo status nao gera escrita")
    void ignoraStatusRepetido() {
        CustomerOrder confirmado = pedido.withStatus(OrderStatus.CONFIRMED, LocalDateTime.now());
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.of(confirmado));

        useCase.handle(new OrderStatusChangedEvent(
                pedido.pedidoId(), OrderStatus.CONFIRMED, LocalDateTime.now()
        ));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("evento para pedido desconhecido e descartado sem erro")
    void ignoraPedidoDesconhecido() {
        UUID desconhecido = UUID.randomUUID();
        when(repository.findById(desconhecido)).thenReturn(Optional.empty());

        useCase.handle(new OrderStatusChangedEvent(desconhecido, OrderStatus.CONFIRMED, LocalDateTime.now()));

        verify(repository, never()).save(any());
    }
}
