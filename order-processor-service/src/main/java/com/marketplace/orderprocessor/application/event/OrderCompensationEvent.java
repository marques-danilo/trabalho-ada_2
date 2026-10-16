package com.marketplace.orderprocessor.application.event;

import com.marketplace.orderprocessor.domain.model.ProcessedOrder;

import java.util.UUID;

/** Mensagem publicada na fila {@code order-compensation}. */
public record OrderCompensationEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String motivo
) {

    public static OrderCompensationEvent from(ProcessedOrder order, String motivo) {
        return new OrderCompensationEvent(
                order.pedidoId(), order.produtoId(), order.quantidade(), motivo
        );
    }
}
