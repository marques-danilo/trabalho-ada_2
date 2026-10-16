package com.marketplace.checkout.domain.model;

/**
 * Estados possiveis de um pedido dentro da SAGA de checkout.
 */
public enum OrderStatus {

    /** Estoque reservado no Redis e pedido publicado em order-created. */
    PROCESSING,

    /** Order Processor confirmou a persistencia do pedido. */
    CONFIRMED,

    /** Processamento falhou; a reserva de estoque foi devolvida ao Redis. */
    CANCELLED
}
