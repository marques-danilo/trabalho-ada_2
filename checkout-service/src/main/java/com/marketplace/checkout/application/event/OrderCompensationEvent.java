package com.marketplace.checkout.application.event;

import java.util.UUID;

/**
 * Mensagem consumida da fila {@code order-compensation}.
 *
 * <p>E a acao compensatoria da SAGA: informa qual produto e qual quantidade precisam voltar para
 * o estoque no Redis.</p>
 */
public record OrderCompensationEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String motivo
) {
}
