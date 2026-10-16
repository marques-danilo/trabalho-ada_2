package com.marketplace.orderprocessor.application.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensagem consumida da fila {@code order-created}.
 *
 * <p>Replica do contrato publicado pelo Checkout. Os dois servicos mantem suas proprias classes
 * de mensagem de proposito: cada um evolui seu modelo interno sem depender de um jar compartilhado.</p>
 */
public record OrderCreatedEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        LocalDateTime dataHoraCriacao
) {
}
