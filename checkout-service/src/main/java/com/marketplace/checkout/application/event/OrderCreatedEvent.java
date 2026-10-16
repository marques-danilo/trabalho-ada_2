package com.marketplace.checkout.application.event;

import com.marketplace.checkout.domain.model.CustomerOrder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mensagem publicada na fila {@code order-created}.
 *
 * <p>Os nomes em JSON sao convertidos para snake_case pela configuracao do Jackson, produzindo o
 * contrato combinado com o Order Processor ({@code pedido_id}, {@code produto_id}, ...).</p>
 */
public record OrderCreatedEvent(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        LocalDateTime dataHoraCriacao
) {

    public static OrderCreatedEvent from(CustomerOrder order) {
        return new OrderCreatedEvent(
                order.pedidoId(),
                order.produtoId(),
                order.quantidade(),
                order.clienteCpf(),
                order.clienteNome(),
                order.dataHoraRequisicao()
        );
    }
}
