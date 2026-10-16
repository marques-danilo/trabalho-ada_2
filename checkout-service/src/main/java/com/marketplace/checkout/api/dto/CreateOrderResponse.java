package com.marketplace.checkout.api.dto;

import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/** Corpo do {@code 202 Accepted} devolvido pelo {@code POST /v1/orders}. */
public record CreateOrderResponse(
        UUID pedidoId,
        OrderStatus status,
        LocalDateTime dataHoraRequisicao
) {

    public static CreateOrderResponse from(CustomerOrder order) {
        return new CreateOrderResponse(order.pedidoId(), order.status(), order.dataHoraRequisicao());
    }
}
