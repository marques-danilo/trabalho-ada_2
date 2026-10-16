package com.marketplace.checkout.infrastructure.messaging;

/** Nomes das filas que compoem a SAGA coreografada. */
public final class QueueNames {

    /** Pedidos aceitos pelo Checkout, aguardando processamento. */
    public static final String ORDER_CREATED = "order-created";

    /** Pedidos que falharam e cujo estoque precisa voltar ao Redis. */
    public static final String ORDER_COMPENSATION = "order-compensation";

    /** Desfecho do processamento, usado para sincronizar o status na base do Checkout. */
    public static final String ORDER_STATUS_CHANGED = "order-status-changed";

    private QueueNames() {
    }
}
