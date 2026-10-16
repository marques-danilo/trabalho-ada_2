package com.marketplace.checkout.domain.model;

import com.marketplace.checkout.domain.exception.InvalidOrderException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Agregado de pedido no dominio de Checkout.
 *
 * <p>Concentra as invariantes de negocio descritas no escopo do projeto: produto obrigatorio,
 * quantidade positiva e CPF do cliente obrigatorio. O agregado e imutavel: transicoes de estado
 * devolvem uma nova instancia, o que mantem o historico de alteracoes explicito no codigo que
 * orquestra a SAGA.</p>
 */
public record CustomerOrder(
        UUID pedidoId,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        OrderStatus status,
        LocalDateTime dataHoraRequisicao,
        LocalDateTime dataHoraAtualizacao
) {

    private static final int CPF_LENGTH = 11;
    private static final int MAX_NOME_LENGTH = 150;

    public CustomerOrder {
        if (pedidoId == null) {
            throw new InvalidOrderException("O identificador do pedido e obrigatorio");
        }
        if (produtoId == null || produtoId.isBlank()) {
            throw new InvalidOrderException("O campo produto_id e obrigatorio");
        }
        if (quantidade <= 0) {
            throw new InvalidOrderException("A quantidade deve ser maior que zero");
        }
        if (clienteCpf == null || clienteCpf.isBlank()) {
            throw new InvalidOrderException("O campo cliente_cpf e obrigatorio");
        }
        clienteCpf = clienteCpf.replaceAll("\\D", "");
        if (clienteCpf.length() != CPF_LENGTH) {
            throw new InvalidOrderException("O campo cliente_cpf deve conter 11 digitos");
        }
        if (clienteNome != null && clienteNome.length() > MAX_NOME_LENGTH) {
            throw new InvalidOrderException("O campo cliente_nome excede o tamanho maximo permitido");
        }
        produtoId = produtoId.trim();
        clienteNome = clienteNome == null ? null : clienteNome.trim();
        if (status == null) {
            throw new InvalidOrderException("O status do pedido e obrigatorio");
        }
        if (dataHoraRequisicao == null) {
            throw new InvalidOrderException("A data/hora de requisicao e obrigatoria");
        }
    }

    /**
     * Cria um novo pedido com identificador UUID v4 e status inicial {@link OrderStatus#PROCESSING}.
     *
     * <p>Os timestamps sao truncados em segundos para casar com o contrato de API acordado
     * ({@code 2026-09-07T20:03:57}).</p>
     */
    public static CustomerOrder create(String produtoId,
                                       int quantidade,
                                       String clienteCpf,
                                       String clienteNome,
                                       LocalDateTime agora) {
        LocalDateTime momento = agora.truncatedTo(ChronoUnit.SECONDS);
        return new CustomerOrder(
                UUID.randomUUID(),
                produtoId,
                quantidade,
                clienteCpf,
                clienteNome,
                OrderStatus.PROCESSING,
                momento,
                momento
        );
    }

    public CustomerOrder withStatus(OrderStatus novoStatus, LocalDateTime agora) {
        return new CustomerOrder(
                pedidoId,
                produtoId,
                quantidade,
                clienteCpf,
                clienteNome,
                novoStatus,
                dataHoraRequisicao,
                agora.truncatedTo(ChronoUnit.SECONDS)
        );
    }

    /**
     * Um pedido ja finalizado nao deve regredir para PROCESSING nem alternar entre estados finais.
     * Protege contra reentrega de mensagens pelo RabbitMQ.
     */
    public boolean isFinalizado() {
        return status == OrderStatus.CONFIRMED || status == OrderStatus.CANCELLED;
    }
}
