package com.marketplace.checkout.application;

import com.marketplace.checkout.application.event.OrderCompensationEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import com.marketplace.checkout.domain.port.StockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Acao compensatoria da SAGA: devolve ao Redis o estoque reservado por um pedido que falhou e
 * marca o pedido como {@code CANCELLED} na base do Checkout.
 *
 * <p>A devolucao e protegida por uma marca de idempotencia: se o RabbitMQ reentregar a mesma
 * mensagem de compensacao, o estoque nao e incrementado duas vezes.</p>
 */
@Service
public class CompensateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompensateOrderUseCase.class);

    private final StockPort stock;
    private final OrderRepositoryPort repository;
    private final OrderCachePort cache;
    private final CheckoutProperties properties;
    private final Clock clock;

    public CompensateOrderUseCase(StockPort stock,
                                  OrderRepositoryPort repository,
                                  OrderCachePort cache,
                                  CheckoutProperties properties,
                                  Clock clock) {
        this.stock = stock;
        this.repository = repository;
        this.cache = cache;
        this.properties = properties;
        this.clock = clock;
    }

    public CompensationResult handle(OrderCompensationEvent event) {
        boolean primeiraVez = stock.markCompensationOnce(event.pedidoId().toString());
        if (!primeiraVez) {
            log.warn("Compensacao do pedido {} ja havia sido aplicada; estoque nao foi alterado",
                    event.pedidoId());
            return new CompensationResult(false, stock.available(event.produtoId()));
        }

        long saldo = stock.release(event.produtoId(), event.quantidade());
        log.info("Compensacao aplicada: pedido={} produto={} quantidadeDevolvida={} novoSaldo={} motivo={}",
                event.pedidoId(), event.produtoId(), event.quantidade(), saldo, event.motivo());

        marcarPedidoComoCancelado(event);
        return new CompensationResult(true, saldo);
    }

    private void marcarPedidoComoCancelado(OrderCompensationEvent event) {
        Optional<CustomerOrder> existente = repository.findById(event.pedidoId());
        if (existente.isEmpty()) {
            log.warn("Compensacao recebida para pedido {} inexistente na base do Checkout", event.pedidoId());
            return;
        }

        CustomerOrder cancelado = existente.get()
                .withStatus(OrderStatus.CANCELLED, LocalDateTime.now(clock));
        repository.save(cancelado);
        cache.put(cancelado, properties.getCacheTtl());
    }

    /**
     * @param estoqueDevolvido false quando a mensagem era uma reentrega e foi ignorada
     * @param saldoAtual       saldo do produto no Redis apos a operacao
     */
    public record CompensationResult(boolean estoqueDevolvido, Long saldoAtual) {
    }
}
