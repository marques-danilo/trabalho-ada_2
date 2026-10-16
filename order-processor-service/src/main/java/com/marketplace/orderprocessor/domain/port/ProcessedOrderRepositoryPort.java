package com.marketplace.orderprocessor.domain.port;

import com.marketplace.orderprocessor.domain.model.ProcessedOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida para a base de dados propria do Order Processor. */
public interface ProcessedOrderRepositoryPort {

    ProcessedOrder save(ProcessedOrder order);

    Optional<ProcessedOrder> findById(UUID pedidoId);

    List<ProcessedOrder> findAll();
}
