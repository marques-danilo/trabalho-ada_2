package com.marketplace.orderprocessor.domain.service;

import com.marketplace.orderprocessor.domain.model.ProcessedOrder;

import java.util.Optional;

/**
 * Decide se o processamento de um pedido deve falhar.
 *
 * <p>Isolado atras de uma interface para que os testes injetem um desfecho deterministico, em
 * vez de depender de sorteio.</p>
 */
public interface FailureSimulator {

    /**
     * @return o motivo da falha, ou {@link Optional#empty()} quando o pedido deve ser confirmado
     */
    Optional<String> shouldFail(ProcessedOrder order);
}
