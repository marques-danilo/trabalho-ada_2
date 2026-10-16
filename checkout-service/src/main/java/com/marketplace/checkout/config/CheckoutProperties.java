package com.marketplace.checkout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parametros de negocio e de infraestrutura do Checkout, ajustaveis por ambiente. */
@ConfigurationProperties(prefix = "checkout")
public class CheckoutProperties {

    /** TTL do cache de consulta de pedido (Cache-Aside). */
    private Duration cacheTtl = Duration.ofMinutes(5);

    /** Numero de tentativas de localizar o pedido antes de devolver 404. */
    private int queryAttempts = 3;

    /** Espera entre as tentativas de consulta. */
    private Duration queryRetryDelay = Duration.ofMillis(200);

    /** Carga inicial de estoque da flash sale, aplicada somente se o produto ainda nao existir. */
    private Map<String, Long> initialStock = new LinkedHashMap<>();

    /** Sobrescreve o estoque inicial a cada inicializacao (util em demonstracoes). */
    private boolean resetStockOnStartup = false;

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public int getQueryAttempts() {
        return queryAttempts;
    }

    public void setQueryAttempts(int queryAttempts) {
        this.queryAttempts = queryAttempts;
    }

    public Duration getQueryRetryDelay() {
        return queryRetryDelay;
    }

    public void setQueryRetryDelay(Duration queryRetryDelay) {
        this.queryRetryDelay = queryRetryDelay;
    }

    public Map<String, Long> getInitialStock() {
        return initialStock;
    }

    public void setInitialStock(Map<String, Long> initialStock) {
        this.initialStock = initialStock;
    }

    public boolean isResetStockOnStartup() {
        return resetStockOnStartup;
    }

    public void setResetStockOnStartup(boolean resetStockOnStartup) {
        this.resetStockOnStartup = resetStockOnStartup;
    }
}
