package com.marketplace.orderprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Parametros de processamento e de simulacao de falhas do Order Processor. */
@ConfigurationProperties(prefix = "processor")
public class ProcessorProperties {

    /** Atraso artificial para que o estado PROCESSING seja observavel durante a demonstracao. */
    private Duration processingDelay = Duration.ZERO;

    private Failure failure = new Failure();

    public Duration getProcessingDelay() {
        return processingDelay;
    }

    public void setProcessingDelay(Duration processingDelay) {
        this.processingDelay = processingDelay;
    }

    public Failure getFailure() {
        return failure;
    }

    public void setFailure(Failure failure) {
        this.failure = failure;
    }

    /**
     * Configuracao da falha simulada. Os tres gatilhos sao avaliados em conjunto: basta um deles
     * ser satisfeito para que o pedido falhe.
     */
    public static class Failure {

        /** Desliga completamente a simulacao de falhas. */
        private boolean enabled = true;

        /** Probabilidade de falha aleatoria, de 0.0 a 1.0. */
        private double rate = 0.0;

        /** Produtos que sempre falham. Garante um cenario de falha deterministico na demo. */
        private List<String> productIds = new ArrayList<>(List.of("PROD-FAIL"));

        /** Falha quando a quantidade do pedido excede este valor. Zero desliga o gatilho. */
        private int quantityAbove = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getRate() {
            return rate;
        }

        public void setRate(double rate) {
            this.rate = rate;
        }

        public List<String> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<String> productIds) {
            this.productIds = productIds;
        }

        public int getQuantityAbove() {
            return quantityAbove;
        }

        public void setQuantityAbove(int quantityAbove) {
            this.quantityAbove = quantityAbove;
        }
    }
}
