package com.marketplace.checkout.domain.exception;

import java.util.UUID;

/** Pedido inexistente apos esgotadas as tentativas de consulta. Mapeada para HTTP 404. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID pedidoId) {
        super("Pedido nao encontrado: " + pedidoId);
    }
}
