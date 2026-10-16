package com.marketplace.checkout.domain.port;

/**
 * Porta de saida para o controle de estoque da flash sale.
 *
 * <p>A reserva precisa ser atomica: em uma queima de estoque milhares de requisicoes concorrem
 * pelo mesmo produto, e um par "le depois escreve" permitiria overselling.</p>
 */
public interface StockPort {

    /**
     * Tenta reservar (decrementar) {@code quantidade} unidades do produto de forma atomica.
     *
     * @return resultado da tentativa, nunca {@code null}
     */
    ReservationResult reserve(String produtoId, int quantidade);

    /**
     * Devolve unidades ao estoque. Usado pelo passo de compensacao da SAGA.
     *
     * @return saldo apos a devolucao
     */
    long release(String produtoId, int quantidade);

    /** Saldo atual, ou {@code null} quando o produto nao esta cadastrado. */
    Long available(String produtoId);

    /** Define o saldo absoluto de um produto (usado na carga inicial e na demonstracao). */
    void setStock(String produtoId, long quantidade);

    /** Define o saldo somente se o produto ainda nao existir. Retorna true se gravou. */
    boolean initializeIfAbsent(String produtoId, long quantidade);

    /**
     * Garante que a devolucao de estoque de um pedido aconteca uma unica vez, mesmo que o
     * RabbitMQ reentregue a mensagem de compensacao.
     *
     * @return true quando esta e a primeira compensacao registrada para o pedido
     */
    boolean markCompensationOnce(String pedidoId);

    /** Desfecho de uma tentativa de reserva. */
    enum Outcome {
        /** Reserva efetuada; {@code saldoRestante} traz o saldo apos o decremento. */
        RESERVED,
        /** Saldo insuficiente; nada foi decrementado. */
        INSUFFICIENT_STOCK,
        /** Produto sem estoque cadastrado no Redis. */
        PRODUCT_NOT_FOUND
    }

    record ReservationResult(Outcome outcome, long saldoRestante) {

        public static ReservationResult reserved(long saldoRestante) {
            return new ReservationResult(Outcome.RESERVED, saldoRestante);
        }

        public static ReservationResult insufficient() {
            return new ReservationResult(Outcome.INSUFFICIENT_STOCK, 0L);
        }

        public static ReservationResult productNotFound() {
            return new ReservationResult(Outcome.PRODUCT_NOT_FOUND, 0L);
        }

        public boolean isReserved() {
            return outcome == Outcome.RESERVED;
        }
    }
}
