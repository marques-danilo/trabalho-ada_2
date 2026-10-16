package com.marketplace.checkout.domain.model;

import com.marketplace.checkout.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Agregado CustomerOrder: invariantes do pedido")
class CustomerOrderTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 9, 7, 20, 3, 57, 123_000_000);

    @Test
    @DisplayName("gera UUID v4, status PROCESSING e timestamps truncados em segundos")
    void criaPedidoValido() {
        CustomerOrder order = CustomerOrder.create("PROD-001", 2, "50329291076", "Fernando Augusto", AGORA);

        assertThat(order.pedidoId()).isNotNull();
        assertThat(order.pedidoId().version()).isEqualTo(4);
        assertThat(order.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(order.quantidade()).isEqualTo(2);
        assertThat(order.dataHoraRequisicao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 3, 57));
        assertThat(order.dataHoraAtualizacao()).isEqualTo(order.dataHoraRequisicao());
    }

    @Test
    @DisplayName("cada pedido recebe um identificador distinto")
    void identificadoresSaoUnicos() {
        CustomerOrder primeiro = CustomerOrder.create("PROD-001", 1, "50329291076", "Ana", AGORA);
        CustomerOrder segundo = CustomerOrder.create("PROD-001", 1, "50329291076", "Ana", AGORA);

        assertThat(primeiro.pedidoId()).isNotEqualTo(segundo.pedidoId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("recusa produto_id ausente")
    void recusaProdutoVazio(String produtoId) {
        assertThatThrownBy(() -> CustomerOrder.create(produtoId, 1, "50329291076", "Ana", AGORA))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("produto_id");
    }

    @Test
    @DisplayName("recusa produto_id nulo")
    void recusaProdutoNulo() {
        assertThatThrownBy(() -> CustomerOrder.create(null, 1, "50329291076", "Ana", AGORA))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("produto_id");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("recusa quantidade menor ou igual a zero")
    void recusaQuantidadeInvalida(int quantidade) {
        assertThatThrownBy(() -> CustomerOrder.create("PROD-001", quantidade, "50329291076", "Ana", AGORA))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("quantidade");
    }

    @Test
    @DisplayName("recusa CPF ausente")
    void recusaCpfAusente() {
        assertThatThrownBy(() -> CustomerOrder.create("PROD-001", 1, "  ", "Ana", AGORA))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("cliente_cpf");
    }

    @Test
    @DisplayName("normaliza CPF formatado removendo pontuacao")
    void normalizaCpf() {
        CustomerOrder order = CustomerOrder.create("PROD-001", 1, "503.292.910-76", "Ana", AGORA);

        assertThat(order.clienteCpf()).isEqualTo("50329291076");
    }

    @Test
    @DisplayName("recusa CPF com quantidade de digitos diferente de 11")
    void recusaCpfComTamanhoInvalido() {
        assertThatThrownBy(() -> CustomerOrder.create("PROD-001", 1, "123", "Ana", AGORA))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("11 digitos");
    }

    @Test
    @DisplayName("transicao de status preserva a data de requisicao e atualiza a de alteracao")
    void transicaoDeStatus() {
        CustomerOrder order = CustomerOrder.create("PROD-001", 2, "50329291076", "Ana", AGORA);
        LocalDateTime depois = AGORA.plusSeconds(13);

        CustomerOrder confirmado = order.withStatus(OrderStatus.CONFIRMED, depois);

        assertThat(confirmado.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmado.pedidoId()).isEqualTo(order.pedidoId());
        assertThat(confirmado.dataHoraRequisicao()).isEqualTo(order.dataHoraRequisicao());
        assertThat(confirmado.dataHoraAtualizacao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 4, 10));
        assertThat(order.status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @DisplayName("reconhece estados finais da SAGA")
    void reconheceEstadosFinais() {
        CustomerOrder order = CustomerOrder.create("PROD-001", 1, "50329291076", "Ana", AGORA);

        assertThat(order.isFinalizado()).isFalse();
        assertThat(order.withStatus(OrderStatus.CONFIRMED, AGORA).isFinalizado()).isTrue();
        assertThat(order.withStatus(OrderStatus.CANCELLED, AGORA).isFinalizado()).isTrue();
    }
}
