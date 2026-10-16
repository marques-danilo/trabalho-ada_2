package com.marketplace.checkout.infrastructure.persistence;

import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.model.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tabela de pedidos da base do Checkout (schema {@code checkout}).
 *
 * <p>Mantida separada do agregado de dominio para que mudancas de mapeamento nao vazem para as
 * regras de negocio.</p>
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

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

    @Column(name = "data_hora_requisicao", nullable = false)
    private LocalDateTime dataHoraRequisicao;

    @Column(name = "data_hora_atualizacao", nullable = false)
    private LocalDateTime dataHoraAtualizacao;

    /** Bloqueio otimista: evita que compensacao e confirmacao concorrentes se sobrescrevam. */
    @Version
    @Column(name = "versao")
    private Long versao;

    protected OrderEntity() {
    }

    public static OrderEntity fromDomain(CustomerOrder order) {
        OrderEntity entity = new OrderEntity();
        entity.pedidoId = order.pedidoId();
        entity.apply(order);
        return entity;
    }

    void apply(CustomerOrder order) {
        this.produtoId = order.produtoId();
        this.quantidade = order.quantidade();
        this.clienteCpf = order.clienteCpf();
        this.clienteNome = order.clienteNome();
        this.status = order.status();
        this.dataHoraRequisicao = order.dataHoraRequisicao();
        this.dataHoraAtualizacao = order.dataHoraAtualizacao();
    }

    public CustomerOrder toDomain() {
        return new CustomerOrder(
                pedidoId,
                produtoId,
                quantidade,
                clienteCpf,
                clienteNome,
                status,
                dataHoraRequisicao,
                dataHoraAtualizacao
        );
    }

    public UUID getPedidoId() {
        return pedidoId;
    }
}
