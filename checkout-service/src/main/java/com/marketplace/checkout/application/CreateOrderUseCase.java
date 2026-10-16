package com.marketplace.checkout.application;

import com.marketplace.checkout.application.command.CreateOrderCommand;
import com.marketplace.checkout.application.event.OrderCreatedEvent;
import com.marketplace.checkout.config.CheckoutProperties;
import com.marketplace.checkout.domain.exception.InsufficientStockException;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.port.OrderCachePort;
import com.marketplace.checkout.domain.port.OrderEventPublisherPort;
import com.marketplace.checkout.domain.port.OrderRepositoryPort;
import com.marketplace.checkout.domain.port.StockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Primeiro passo da SAGA: reserva o estoque e publica o pedido para processamento assincrono.
 *
 * <p>A resposta ao cliente e devolvida logo apos a publicacao, sem esperar o Order Processor,
 * que e o requisito de latencia da flash sale.</p>
 */
@Service
public class CreateOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOrderUseCase.class);

    private final StockPort stock;
    private final OrderRepositoryPort repository;
    private final OrderEventPublisherPort publisher;
    private final OrderCachePort cache;
    private final CheckoutProperties properties;
    private final Clock clock;

    public CreateOrderUseCase(StockPort stock,
                              OrderRepositoryPort repository,
                              OrderEventPublisherPort publisher,
                              OrderCachePort cache,
                              CheckoutProperties properties,
                              Clock clock) {
        this.stock = stock;
        this.repository = repository;
        this.publisher = publisher;
        this.cache = cache;
        this.properties = properties;
        this.clock = clock;
    }

    public CustomerOrder handle(CreateOrderCommand command) {
        // As invariantes de negocio ficam no agregado: construir o pedido ja valida a entrada.
        CustomerOrder order = CustomerOrder.create(
                command.produtoId(),
                command.quantidade(),
                command.clienteCpf(),
                command.clienteNome(),
                LocalDateTime.now(clock)
        );

        StockPort.ReservationResult reservation = stock.reserve(order.produtoId(), order.quantidade());
        if (!reservation.isReserved()) {
            log.info("Reserva recusada para pedido do produto {} (quantidade {}): {}",
                    order.produtoId(), order.quantidade(), reservation.outcome());
            throw new InsufficientStockException(order.produtoId());
        }

        log.info("Estoque reservado: produto={} quantidade={} saldoRestante={} pedido={}",
                order.produtoId(), order.quantidade(), reservation.saldoRestante(), order.pedidoId());

        try {
            repository.save(order);
            publisher.publishOrderCreated(OrderCreatedEvent.from(order));
        } catch (RuntimeException e) {
            // A reserva ja aconteceu, mas o pedido nunca chegou a fila: nenhum consumidor vai
            // compensar por nos, portanto devolvemos o estoque aqui mesmo.
            long saldo = stock.release(order.produtoId(), order.quantidade());
            log.error("Falha ao registrar/publicar o pedido {}; estoque devolvido (saldo={})",
                    order.pedidoId(), saldo, e);
            throw e;
        }

        cache.put(order, properties.getCacheTtl());
        return order;
    }
}
