package com.marketplace.checkout.api.dto;

import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** Corpo do {@code 200 OK} devolvido pelo {@code GET /v1/orders/{pedido_id}}. */
public record OrderResponse(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        OrderStatus status,
        LocalDateTime dataHoraAtualizacao
) {

    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.pedidoId(),
                order.produtoId(),
                order.quantidade(),
                order.clienteCpf(),
                order.clienteNome(),
                order.status(),
                order.dataHoraAtualizacao()
        );
    }
}
