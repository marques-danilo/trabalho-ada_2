package com.marketplace.orderprocessor.infrastructure.messaging;

/** Nomes das filas que compoem a SAGA coreografada. */
public final class QueueNames {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_COMPENSATION = "order-compensation";
    public static final String ORDER_STATUS_CHANGED = "order-status-changed";

    private QueueNames() {
    }
}
