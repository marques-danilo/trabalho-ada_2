package com.marketplace.orderprocessor.domain.service;

import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Falha configuravel, no lugar de uma integracao real de pagamento.
 *
 * <p>Combina tres gatilhos para cobrir o que a demonstracao exige: uma lista de produtos que
 * sempre falham (cenario deterministico), um limite de quantidade e uma taxa aleatoria para
 * simular instabilidade sob carga.</p>
 */
@Component
public class ConfigurableFailureSimulator implements FailureSimulator {

    private final ProcessorProperties properties;

    public ConfigurableFailureSimulator(ProcessorProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> shouldFail(ProcessedOrder order) {
        ProcessorProperties.Failure config = properties.getFailure();
        if (!config.isEnabled()) {
            return Optional.empty();
        }

        List<String> produtosQueFalham = config.getProductIds();
        if (produtosQueFalham != null && produtosQueFalham.contains(order.produtoId())) {
            return Optional.of("Falha no processamento do pedido: produto "
                    + order.produtoId() + " marcado para falha simulada");
        }

        if (config.getQuantityAbove() > 0 && order.quantidade() > config.getQuantityAbove()) {
            return Optional.of("Falha no processamento do pedido: quantidade "
                    + order.quantidade() + " acima do limite permitido de " + config.getQuantityAbove());
        }

        if (config.getRate() > 0 && ThreadLocalRandom.current().nextDouble() < config.getRate()) {
            return Optional.of("Falha no processamento do pedido: indisponibilidade simulada");
        }

        return Optional.empty();
    }
}
