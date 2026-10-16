package com.marketplace.checkout.domain.exception;

/** Estoque indisponivel para a reserva solicitada. Mapeada para HTTP 409. */
public class InsufficientStockException extends RuntimeException {

    private final String produtoId;

    public InsufficientStockException(String produtoId) {
        super("Estoque insuficiente para o produto " + produtoId);
        this.produtoId = produtoId;
    }

    public String getProdutoId() {
        return produtoId;
    }
}
