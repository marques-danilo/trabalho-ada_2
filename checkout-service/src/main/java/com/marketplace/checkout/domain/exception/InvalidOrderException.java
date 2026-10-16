package com.marketplace.checkout.domain.exception;

/** Violacao de uma invariante de negocio do pedido. Mapeada para HTTP 400. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
