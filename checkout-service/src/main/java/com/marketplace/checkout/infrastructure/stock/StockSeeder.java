package com.marketplace.checkout.infrastructure.stock;

import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.port.StockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Carga inicial do estoque da flash sale.
 *
 * <p>Por padrao usa {@code SETNX}, entao reiniciar o servico nao apaga o saldo real de uma venda
 * em andamento. Para demonstracoes, {@code checkout.reset-stock-on-startup=true} forca o valor
 * configurado a cada inicializacao.</p>
 */
@Component
public class StockSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockSeeder.class);

    private final StockPort stock;
    private final CheckoutProperties properties;

    public StockSeeder(StockPort stock, CheckoutProperties properties) {
        this.stock = stock;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Long> inicial = properties.getInitialStock();
        if (inicial == null || inicial.isEmpty()) {
            return;
        }

        inicial.forEach((produtoId, quantidade) -> {
            if (properties.isResetStockOnStartup()) {
                stock.setStock(produtoId, quantidade);
                log.info("Estoque inicial definido: {} = {}", produtoId, quantidade);
                return;
            }
            boolean gravou = stock.initializeIfAbsent(produtoId, quantidade);
            log.info("Estoque inicial {} = {} ({})", produtoId, quantidade,
                    gravou ? "criado" : "ja existia, preservado");
        });
    }
}
