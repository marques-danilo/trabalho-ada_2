package com.marketplace.orderprocessor.domain.service;

import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigurableFailureSimulator: gatilhos da falha simulada")
class ConfigurableFailureSimulatorTest {

    private ProcessorProperties properties;
    private ConfigurableFailureSimulator simulator;

    @BeforeEach
    void setUp() {
        properties = new ProcessorProperties();
        simulator = new ConfigurableFailureSimulator(properties);
    }

    private ProcessedOrder pedido(String produtoId, int quantidade) {
        return ProcessedOrder.received(
                UUID.randomUUID(), produtoId, quantidade, "50329291076", "Fernando Augusto",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("produto da lista de falhas sempre falha")
    void falhaPorProduto() {
        properties.getFailure().setProductIds(List.of("PROD-FAIL"));

        assertThat(simulator.shouldFail(pedido("PROD-FAIL", 1))).isPresent();
        assertThat(simulator.shouldFail(pedido("PROD-001", 1))).isEmpty();
    }

    @Test
    @DisplayName("quantidade acima do limite falha e dentro do limite passa")
    void falhaPorQuantidade() {
        properties.getFailure().setProductIds(List.of());
        properties.getFailure().setQuantityAbove(5);

        assertThat(simulator.shouldFail(pedido("PROD-001", 6))).isPresent();
        assertThat(simulator.shouldFail(pedido("PROD-001", 5))).isEmpty();
    }

    @Test
    @DisplayName("limite de quantidade zerado desliga o gatilho")
    void limiteZeradoNaoFalha() {
        properties.getFailure().setProductIds(List.of());
        properties.getFailure().setQuantityAbove(0);

        assertThat(simulator.shouldFail(pedido("PROD-001", 10_000))).isEmpty();
    }

    @Test
    @DisplayName("taxa 1.0 falha sempre e taxa 0.0 nunca falha")
    void falhaPorTaxa() {
        properties.getFailure().setProductIds(List.of());

        properties.getFailure().setRate(1.0);
        assertThat(simulator.shouldFail(pedido("PROD-001", 1))).isPresent();

        properties.getFailure().setRate(0.0);
        assertThat(simulator.shouldFail(pedido("PROD-001", 1))).isEmpty();
    }

    @Test
    @DisplayName("simulacao desabilitada ignora todos os gatilhos")
    void desabilitadoNuncaFalha() {
        properties.getFailure().setEnabled(false);
        properties.getFailure().setProductIds(List.of("PROD-FAIL"));
        properties.getFailure().setRate(1.0);
        properties.getFailure().setQuantityAbove(1);

        assertThat(simulator.shouldFail(pedido("PROD-FAIL", 999))).isEmpty();
    }
}
