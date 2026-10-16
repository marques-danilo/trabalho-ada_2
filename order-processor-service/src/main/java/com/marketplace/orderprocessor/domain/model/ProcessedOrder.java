package com.marketplace.orderprocessor.domain.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Agregado de pedido na visao do Order Processor.
 *
 * <p>Este servico e o dono do ciclo de processamento: decide o desfecho e guarda o motivo da
 * falha quando ela ocorre. Sua base e independente da base do Checkout.</p>
 */
public record ProcessedOrder(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        OrderStatus status,
        String motivoFalha,
        LocalDateTime dataHoraCriacao,
        LocalDateTime dataHoraAtualizacao
) {

    public static ProcessedOrder received(UUID pedidoId,
                                          String produtoId,
                                          int quantidade,
                                          String clienteCpf,
                                          String clienteNome,
                                          LocalDateTime dataHoraCriacao,
                                          LocalDateTime agora) {
        LocalDateTime momento = agora.truncatedTo(ChronoUnit.SECONDS);
        return new ProcessedOrder(
                pedidoId,
                produtoId,
                quantidade,
                clienteCpf,
                clienteNome,
                OrderStatus.PROCESSING,
                null,
                dataHoraCriacao == null ? momento : dataHoraCriacao.truncatedTo(ChronoUnit.SECONDS),
                momento
        );
    }

    public ProcessedOrder confirm(LocalDateTime agora) {
        return new ProcessedOrder(
                pedidoId, produtoId, quantidade, clienteCpf, clienteNome,
                OrderStatus.CONFIRMED, null,
                dataHoraCriacao, agora.truncatedTo(ChronoUnit.SECONDS)
        );
    }

    public ProcessedOrder cancel(String motivo, LocalDateTime agora) {
        return new ProcessedOrder(
                pedidoId, produtoId, quantidade, clienteCpf, clienteNome,
                OrderStatus.CANCELLED, motivo,
                dataHoraCriacao, agora.truncatedTo(ChronoUnit.SECONDS)
        );
    }

    /** Pedidos em estado final nao sao reprocessados em caso de reentrega da mensagem. */
    public boolean isFinalizado() {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED;
    }
}
