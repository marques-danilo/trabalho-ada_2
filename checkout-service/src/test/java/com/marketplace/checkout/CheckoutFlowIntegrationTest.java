package com.marketplace.checkout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.checkout.application.ApplyOrderStatusUseCase;
import com.marketplace.checkout.application.CompensateOrderUseCase;
import com.marketplace.checkout.application.CreateOrderUseCase;
import com.marketplace.checkout.application.command.CreateOrderCommand;
import com.marketplace.checkout.application.event.OrderCompensationEvent;
import com.marketplace.checkout.application.event.OrderStatusChangedEvent;
import com.marketplace.checkout.domain.exception.InsufficientStockException;
import com.marketplace.checkout.domain.model.OrderStatus;
import com.marketplace.checkout.infrastructure.memory.InMemoryOrderCacheAdapter;
import com.marketplace.checkout.infrastructure.memory.InMemoryOrderEventPublisher;
import com.marketplace.checkout.infrastructure.memory.InMemoryStockAdapter;
import com.marketplace.checkout.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integracao dos fluxos principais do Checkout.
 *
 * <p>Sobe o contexto completo (web + JPA/H2 + casos de uso) com Redis e RabbitMQ substituidos
 * pelos adaptadores em memoria, de modo que a suite roda sem nenhuma infraestrutura externa.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Integracao: fluxos de checkout, consulta e compensacao")
class CheckoutFlowIntegrationTest {

    private static final String PRODUTO = "PROD-001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InMemoryStockAdapter stock;
    @Autowired
    private InMemoryOrderCacheAdapter cache;
    @Autowired
    private InMemoryOrderEventPublisher publisher;
    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired
    private CreateOrderUseCase createOrder;
    @Autowired
    private CompensateOrderUseCase compensateOrder;
    @Autowired
    private ApplyOrderStatusUseCase applyOrderStatus;

    @BeforeEach
    void setUp() {
        stock.reset();
        cache.reset();
        publisher.reset();
        orderJpaRepository.deleteAll();
        stock.setStock(PRODUTO, 10);
    }

    private String corpoDoPedido(int quantidade) {
        return """
                {
                  "produto_id": "%s",
                  "quantidade": %d,
                  "cliente_cpf": "50329291076",
                  "cliente_nome": "Fernando Augusto"
                }
                """.formatted(PRODUTO, quantidade);
    }

    private UUID criarPedido(int quantidade) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPedido(quantidade)))
                .andExpect(status().isAccepted())
                .andReturn();
        JsonNode body = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return UUID.fromString(body.get("pedido_id").asText());
    }

    @Test
    @DisplayName("POST /v1/orders devolve 202, reserva o estoque e publica em order-created")
    void criaPedidoComSucesso() throws Exception {
        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPedido(2)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pedido_id").exists())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data_hora_requisicao").exists());

        assertThat(stock.available(PRODUTO)).isEqualTo(8L);
        assertThat(publisher.publicados()).hasSize(1);
        assertThat(publisher.publicados().getFirst().produtoId()).isEqualTo(PRODUTO);
        assertThat(publisher.publicados().getFirst().quantidade()).isEqualTo(2);
    }

    @Test
    @DisplayName("POST /v1/orders devolve 409 quando o estoque e insuficiente")
    void recusaPedidoSemEstoque() throws Exception {
        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPedido(99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem")
                        .value("Estoque insuficiente para o produto PROD-001"));

        assertThat(stock.available(PRODUTO)).isEqualTo(10L);
        assertThat(publisher.publicados()).isEmpty();
    }

    @Test
    @DisplayName("POST /v1/orders devolve 400 para quantidade zero e para produto ausente")
    void validaEntrada() throws Exception {
        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoPedido(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());

        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantidade": 1, "cliente_cpf": "50329291076"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"produto_id": "PROD-001", "quantidade": 1}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(stock.available(PRODUTO)).isEqualTo(10L);
        assertThat(publisher.publicados()).isEmpty();
    }

    @Test
    @DisplayName("GET /v1/orders/{id} devolve o pedido recem-criado a partir do cache")
    void consultaPedidoPeloCache() throws Exception {
        UUID pedidoId = criarPedido(2);
        int hitsAntes = cache.hits();

        mockMvc.perform(get("/v1/orders/{id}", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido_id").value(pedidoId.toString()))
                .andExpect(jsonPath("$.produto_id").value(PRODUTO))
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.cliente_cpf").value("50329291076"))
                .andExpect(jsonPath("$.cliente_nome").value("Fernando Augusto"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data_hora_atualizacao").exists());

        assertThat(cache.hits()).isEqualTo(hitsAntes + 1);
        assertThat(cache.ttlOf(pedidoId)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("GET /v1/orders/{id} com cache vazio busca no banco e repovoa o cache")
    void consultaPedidoPeloBancoERepovoaCache() throws Exception {
        UUID pedidoId = criarPedido(1);
        cache.reset();

        mockMvc.perform(get("/v1/orders/{id}", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedido_id").value(pedidoId.toString()));

        assertThat(cache.misses()).isEqualTo(1);
        assertThat(cache.find(pedidoId)).isPresent();
        assertThat(cache.ttlOf(pedidoId)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("GET /v1/orders/{id} inexistente tenta tres vezes e devolve 404")
    void consultaPedidoInexistente() throws Exception {
        UUID desconhecido = UUID.randomUUID();
        cache.reset();

        mockMvc.perform(get("/v1/orders/{id}", desconhecido))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Pedido nao encontrado"));

        assertThat(cache.misses()).isEqualTo(3);
    }

    @Test
    @DisplayName("SAGA - fluxo de sucesso: pedido criado chega a CONFIRMED")
    void sagaFluxoDeSucesso() throws Exception {
        UUID pedidoId = criarPedido(2);
        assertThat(stock.available(PRODUTO)).isEqualTo(8L);

        // Desfecho publicado pelo Order Processor apos persistir o pedido.
        applyOrderStatus.handle(new OrderStatusChangedEvent(
                pedidoId, OrderStatus.CONFIRMED, LocalDateTime.of(2026, 9, 7, 20, 4, 10)
        ));

        mockMvc.perform(get("/v1/orders/{id}", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data_hora_atualizacao").value("2026-09-07T20:04:10"));

        // O estoque permanece baixado: a venda foi concluida.
        assertThat(stock.available(PRODUTO)).isEqualTo(8L);
    }

    @Test
    @DisplayName("SAGA - fluxo de falha: compensacao devolve o estoque e o pedido fica CANCELLED")
    void sagaFluxoDeCompensacao() throws Exception {
        UUID pedidoId = criarPedido(2);
        assertThat(stock.available(PRODUTO)).isEqualTo(8L);

        CompensateOrderUseCase.CompensationResult resultado = compensateOrder.handle(
                new OrderCompensationEvent(pedidoId, PRODUTO, 2, "Falha no processamento do pedido")
        );

        assertThat(resultado.estoqueDevolvido()).isTrue();
        assertThat(stock.available(PRODUTO)).isEqualTo(10L);

        mockMvc.perform(get("/v1/orders/{id}", pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("SAGA - compensacao reentregue nao devolve o estoque duas vezes")
    void compensacaoReentregueNaoDuplicaEstoque() throws Exception {
        UUID pedidoId = criarPedido(2);
        OrderCompensationEvent evento =
                new OrderCompensationEvent(pedidoId, PRODUTO, 2, "Falha no processamento do pedido");

        compensateOrder.handle(evento);
        compensateOrder.handle(evento);

        assertThat(stock.available(PRODUTO)).isEqualTo(10L);
    }

    @Test
    @DisplayName("concorrencia: 30 pedidos simultaneos sobre 5 unidades nao causam overselling")
    void naoPermiteOverselling() throws Exception {
        stock.setStock(PRODUTO, 5);
        int concorrentes = 30;

        AtomicInteger aceitos = new AtomicInteger();
        AtomicInteger recusados = new AtomicInteger();
        CountDownLatch largada = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            var tarefas = IntStream.range(0, concorrentes)
                    .mapToObj(i -> (Callable<Void>) () -> {
                        largada.await();
                        try {
                            createOrder.handle(new CreateOrderCommand(
                                    PRODUTO, 1, "50329291076", "Cliente " + i
                            ));
                            aceitos.incrementAndGet();
                        } catch (InsufficientStockException e) {
                            recusados.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            var futures = tarefas.stream().map(executor::submit).toList();
            largada.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        assertThat(aceitos.get()).isEqualTo(5);
        assertThat(recusados.get()).isEqualTo(concorrentes - 5);
        assertThat(stock.available(PRODUTO)).isZero();
        assertThat(publisher.publicados()).hasSize(5);
    }
}
