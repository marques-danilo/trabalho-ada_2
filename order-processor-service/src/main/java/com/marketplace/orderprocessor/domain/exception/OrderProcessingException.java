package com.marketplace.orderprocessor.domain.exception;

/**
 * Falha de regra de negocio ou de persistencia durante o processamento do pedido.
 *
 * <p>E o gatilho da acao compensatoria: ao captura-la, o caso de uso marca o pedido como
 * CANCELLED e publica na fila {@code order-compensation}.</p>
 */
public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(String message) {
        super(message);
    }

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
