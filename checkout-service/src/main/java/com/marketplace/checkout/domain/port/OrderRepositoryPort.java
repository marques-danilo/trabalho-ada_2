package com.marketplace.checkout.domain.port;

import com.marketplace.checkout.domain.model.CustomerOrder;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida para a base de dados do proprio dominio de Checkout.
 *
 * <p>O Checkout mantem sua base segregada do Order Processor; aqui vive a visao de leitura usada
 * pelo endpoint {@code GET /v1/orders/{pedido_id}} quando o cache nao responde.</p>
 */
public interface OrderRepositoryPort {

    CustomerOrder save(CustomerOrder order);

    Optional<CustomerOrder> findById(UUID pedidoId);
}
