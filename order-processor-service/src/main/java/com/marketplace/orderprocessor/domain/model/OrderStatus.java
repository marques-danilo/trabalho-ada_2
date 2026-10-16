package com.marketplace.orderprocessor.domain.model;

/** Estados do pedido na visao do Order Processor. */
public enum OrderStatus {

    /** Mensagem recebida, processamento em andamento. */
    PROCESSING,

    /** Pedido persistido com sucesso. */
    CONFIRMED,

    /** Processamento falhou; compensacao publicada para o Checkout. */
    CANCELLED
}
