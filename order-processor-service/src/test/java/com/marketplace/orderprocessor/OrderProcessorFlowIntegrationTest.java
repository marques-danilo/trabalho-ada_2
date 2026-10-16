package com.marketplace.orderprocessor;

import com.marketplace.orderprocessor.application.ProcessOrderUseCase;
import com.marketplace.orderprocessor.application.event.OrderCreatedEvent;
import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.model.OrderStatus;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import com.marketplace.orderprocessor.domain.port.ProcessedOrderRepositoryPort;
import com.marketplace.orderprocessor.infrastructure.memory.InMemorySagaEventPublisher;
import com.marketplace.orderprocessor.infrastructure.persistence.ProcessedOrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integracao do Order Processor: contexto completo com H2 real e publisher em memoria.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integracao: processamento do pedido e publicacao da compensacao")
class OrderProcessorFlowIntegrationTest {

    @Autowired
    private ProcessOrderUseCase processOrder;
    @Autowired
    private ProcessedOrderRepositoryPort repository;
    @Autowired
    private InMemorySagaEventPublisher publisher;
    @Autowired
    private ProcessedOrderJpaRepository jpaRepository;
    @Autowired
    private ProcessorProperties properties;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
        publisher.reset();
        properties.getFailure().setEnabled(true);
        properties.getFailure().setProductIds(List.of("PROD-FAIL"));
        properties.getFailure().setRate(0.0);
        properties.getFailure().setQuantityAbove(0);
    }

    private OrderCreatedEvent evento(String produtoId, int quantidade) {
        return new OrderCreatedEvent(
                UUID.randomUUID(), produtoId, quantidade, "50329291076", "Fernando Augusto",
                LocalDateTime.of(2026, 9, 7, 20, 3, 57)
        );
    }

    @Test
    @DisplayName("pedido valido e persistido na base propria com status CONFIRMED")
    void persisteConfirmado() {
        OrderCreatedEvent evento = evento("PROD-001", 2);

        processOrder.handle(evento);

        Optional<ProcessedOrder> salvo = repository.findById(evento.pedidoId());
        assertThat(salvo).isPresent();
        assertThat(salvo.get().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(salvo.get().produtoId()).isEqualTo("PROD-001");
        assertThat(salvo.get().quantidade()).isEqualTo(2);
        assertThat(salvo.get().motivoFalha()).isNull();

        assertThat(publisher.compensacoes()).isEmpty();
        assertThat(publisher.statusChanges()).hasSize(1);
        assertThat(publisher.statusChanges().getFirst().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("produto marcado para falha resulta em CANCELLED e mensagem de compensacao")
    void persisteCanceladoEPublicaCompensacao() {
        OrderCreatedEvent evento = evento("PROD-FAIL", 3);

        processOrder.handle(evento);

        Optional<ProcessedOrder> salvo = repository.findById(evento.pedidoId());
        assertThat(salvo).isPresent();
        assertThat(salvo.get().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(salvo.get().motivoFalha()).contains("PROD-FAIL");

        assertThat(publisher.compensacoes()).hasSize(1);
        var compensacao = publisher.compensacoes().getFirst();
        assertThat(compensacao.pedidoId()).isEqualTo(evento.pedidoId());
        assertThat(compensacao.produtoId()).isEqualTo("PROD-FAIL");
        assertThat(compensacao.quantidade()).isEqualTo(3);

        assertThat(publisher.statusChanges()).hasSize(1);
        assertThat(publisher.statusChanges().getFirst().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("gatilho de quantidade configurado em runtime leva o pedido a CANCELLED")
    void falhaPorQuantidadeConfiguradaEmRuntime() {
        properties.getFailure().setProductIds(List.of());
        properties.getFailure().setQuantityAbove(2);

        OrderCreatedEvent evento = evento("PROD-001", 5);
        processOrder.handle(evento);

        assertThat(repository.findById(evento.pedidoId()))
                .get()
                .extracting(ProcessedOrder::status)
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(publisher.compensacoes()).hasSize(1);
    }

    @Test
    @DisplayName("reentrega da mesma mensagem nao duplica compensacao nem registros")
    void reentregaEIdempotente() {
        OrderCreatedEvent evento = evento("PROD-FAIL", 1);

        processOrder.handle(evento);
        processOrder.handle(evento);
        processOrder.handle(evento);

        assertThat(jpaRepository.count()).isEqualTo(1);
        assertThat(publisher.compensacoes()).hasSize(1);
    }

    @Test
    @DisplayName("GET /internal/orders/{id} expoe o pedido persistido na base do processor")
    void consultaInterna() throws Exception {
        OrderCreatedEvent evento = evento("PROD-001", 2);
        processOrder.handle(evento);

        mockMvc.perform(get("/internal/orders/{id}", evento.pedidoId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido_id").value(evento.pedidoId().toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/internal/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
