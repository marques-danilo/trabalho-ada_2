package com.marketplace.checkout.application;

import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.exception.OrderNotFoundException;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetOrderUseCase: Cache-Aside e tres tentativas antes do 404")
class GetOrderUseCaseTest {

    @Mock
    private OrderCachePort cache;
    @Mock
    private OrderRepositoryPort repository;

    private CheckoutProperties properties;
    private GetOrderUseCase useCase;
    private CustomerOrder pedido;

    @BeforeEach
    void setUp() {
        properties = new CheckoutProperties();
        properties.setQueryRetryDelay(Duration.ZERO);
        useCase = new GetOrderUseCase(cache, repository, properties);
        pedido = new CustomerOrder(
                UUID.randomUUID(), "PROD-001", 2, "50329291076", "Fernando Augusto",
                OrderStatus.CONFIRMED,
                LocalDateTime.of(2026, 9, 7, 20, 3, 57),
                LocalDateTime.of(2026, 9, 7, 20, 4, 10)
        );
    }

    @Test
    @DisplayName("cache hit devolve o pedido sem consultar o banco")
    void cacheHit() {
        when(cache.find(pedido.pedidoId())).thenReturn(Optional.of(pedido));

        CustomerOrder resultado = useCase.handle(pedido.pedidoId());

        assertThat(resultado).isEqualTo(pedido);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("cache miss consulta o banco e repovoa o cache com o TTL de 5 minutos")
    void cacheMissRepovoaCache() {
        when(cache.find(pedido.pedidoId())).thenReturn(Optional.empty());
        when(repository.findById(pedido.pedidoId())).thenReturn(Optional.of(pedido));

        CustomerOrder resultado = useCase.handle(pedido.pedidoId());

        assertThat(resultado).isEqualTo(pedido);
        verify(cache).put(pedido, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("faz exatamente tres tentativas e entao sinaliza pedido inexistente")
    void tresTentativasAntesDo404() {
        UUID desconhecido = UUID.randomUUID();
        when(cache.find(desconhecido)).thenReturn(Optional.empty());
        when(repository.findById(desconhecido)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.handle(desconhecido))
                .isInstanceOf(OrderNotFoundException.class);

        verify(cache, times(3)).find(desconhecido);
        verify(repository, times(3)).findById(desconhecido);
        verify(cache, never()).put(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("pedido que aparece somente na terceira tentativa e devolvido com sucesso")
    void encontraNaTerceiraTentativa() {
        when(cache.find(pedido.pedidoId())).thenReturn(Optional.empty());
        when(repository.findById(pedido.pedidoId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pedido));

        CustomerOrder resultado = useCase.handle(pedido.pedidoId());

        assertThat(resultado).isEqualTo(pedido);
        verify(repository, times(3)).findById(pedido.pedidoId());
    }

    @Test
    @DisplayName("numero de tentativas e configuravel")
    void tentativasConfiguraveis() {
        properties.setQueryAttempts(5);
        UUID desconhecido = UUID.randomUUID();
        when(cache.find(desconhecido)).thenReturn(Optional.empty());
        when(repository.findById(desconhecido)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.handle(desconhecido))
                .isInstanceOf(OrderNotFoundException.class);

        verify(repository, times(5)).findById(desconhecido);
    }
}
