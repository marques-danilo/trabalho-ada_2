package com.marketplace.checkout.application.event;

import com.marketplace.checkout.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensagem consumida da fila {@code order-status-changed}.
 *
 * <p>Complemento deliberado ao escopo minimo: as duas bases sao segregadas, entao o Checkout nao
 * le a base do Order Processor. Para que {@code GET /v1/orders/{pedido_id}} possa devolver
 * {@code CONFIRMED}, o Order Processor publica o desfecho do processamento e o Checkout atualiza
 * sua propria base e seu cache (event-carried state transfer).</p>
 */
public record OrderStatusChangedEvent(
        UUID pedidoId,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao
) {
}
