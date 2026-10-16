package com.marketplace.orderprocessor.infrastructure.persistence;

import com.marketplace.orderprocessor.domain.model.OrderStatus;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tabela de pedidos da base do Order Processor (schema {@code order_processor}). */
@Entity
@Table(name = "processed_orders")
public class ProcessedOrderEntity {

    @Id
    @Column(name = "pedido_id", nullable = false, updatable = false)
    private UUID pedidoId;

    @Column(name = "produto_id", nullable = false, length = 60)
    private String produtoId;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    @Column(name = "cliente_cpf", nullable = false, length = 11)
    private String clienteCpf;

    @Column(name = "cliente_nome", length = 150)
    private String clienteNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "motivo_falha", length = 500)
    private String motivoFalha;

    @Column(name = "data_hora_criacao", nullable = false)
    private LocalDateTime dataHoraCriacao;

    @Column(name = "data_hora_atualizacao", nullable = false)
    private LocalDateTime dataHoraAtualizacao;

    @Version
    @Column(name = "versao")
    private Long versao;

    protected ProcessedOrderEntity() {
    }

    public static ProcessedOrderEntity fromDomain(ProcessedOrder order) {
        ProcessedOrderEntity entity = new ProcessedOrderEntity();
        entity.pedidoId = order.pedidoId();
        entity.apply(order);
        return entity;
    }

    void apply(ProcessedOrder order) {
        this.produtoId = order.produtoId();
        this.quantidade = order.quantidade();
        this.clienteCpf = order.clienteCpf();
        this.clienteNome = order.clienteNome();
        this.status = order.status();
        this.motivoFalha = order.motivoFalha();
        this.dataHoraCriacao = order.dataHoraCriacao();
        this.dataHoraAtualizacao = order.dataHoraAtualizacao();
    }

    public ProcessedOrder toDomain() {
        return new ProcessedOrder(
                pedidoId, produtoId, quantidade, clienteCpf, clienteNome,
                status, motivoFalha, dataHoraCriacao, dataHoraAtualizacao
        );
    }
}
