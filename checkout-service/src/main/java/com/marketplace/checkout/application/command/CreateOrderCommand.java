package com.marketplace.checkout.application.command;

/** Dados de entrada do caso de uso de criacao de pedido, desacoplados do DTO HTTP. */
public record CreateOrderCommand(
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome
) {
}
