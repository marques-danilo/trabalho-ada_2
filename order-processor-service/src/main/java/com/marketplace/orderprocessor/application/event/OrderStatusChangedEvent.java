package com.marketplace.orderprocessor.application.event;

import com.marketplace.orderprocessor.domain.model.OrderStatus;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mensagem publicada na fila {@code order-status-changed}. */
public record OrderStatusChangedEvent(
        UUID pedidoId,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao
) {

    public static OrderStatusChangedEvent from(ProcessedOrder order) {
        return new OrderStatusChangedEvent(
                order.pedidoId(), order.status(), order.dataHoraAtualizacao()
        );
    }
}
