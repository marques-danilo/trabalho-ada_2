package com.marketplace.orderprocessor.api;

import com.marketplace.orderprocessor.config.ProcessorProperties;
import com.marketplace.orderprocessor.domain.model.OrderStatus;
import com.marketplace.orderprocessor.domain.model.ProcessedOrder;
import com.marketplace.orderprocessor.domain.port.ProcessedOrderRepositoryPort;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints internos do Order Processor.
 *
 * <p>Nao fazem parte do contrato do cliente final: servem para inspecionar a base propria deste
 * servico e para ligar ou desligar a falha simulada durante a demonstracao, sem reiniciar o
 * processo.</p>
 */
@RestController
@RequestMapping("/internal")
@Validated
public class ProcessedOrderController {

    private final ProcessedOrderRepositoryPort repository;
    private final ProcessorProperties properties;

    public ProcessedOrderController(ProcessedOrderRepositoryPort repository,
                                    ProcessorProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @GetMapping("/orders")
    public List<OrderView> listar() {
        return repository.findAll().stream().map(OrderView::from).toList();
    }

    @GetMapping("/orders/{pedidoId}")
    public ResponseEntity<OrderView> consultar(@PathVariable UUID pedidoId) {
        return repository.findById(pedidoId)
                .map(OrderView::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Ajusta a simulacao de falha em tempo de execucao. */
    @PostMapping("/failure-mode")
    public FailureView configurarFalha(
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @DecimalMin("0.0") @DecimalMax("1.0") Double rate,
            @RequestParam(required = false) List<String> productIds,
            @RequestParam(required = false) Integer quantityAbove) {

        ProcessorProperties.Failure failure = properties.getFailure();
        if (enabled != null) {
            failure.setEnabled(enabled);
        }
        if (rate != null) {
            failure.setRate(rate);
        }
        if (productIds != null) {
            failure.setProductIds(productIds);
        }
        if (quantityAbove != null) {
            failure.setQuantityAbove(quantityAbove);
        }
        return FailureView.from(failure);
    }

    @GetMapping("/failure-mode")
    public FailureView consultarFalha() {
        return FailureView.from(properties.getFailure());
    }

    public record OrderView(
            UUID pedidoId,
            String produtoId,
            int quantidade,
            String clienteCpf,
            String clienteNome,
            OrderStatus status,
            String motivoFalha,
            LocalDateTime dataHoraCriacao,
            LocalDateTime dataHoraAtualizacao
    ) {
        static OrderView from(ProcessedOrder order) {
            return new OrderView(
                    order.pedidoId(), order.produtoId(), order.quantidade(),
                    order.clienteCpf(), order.clienteNome(), order.status(),
                    order.motivoFalha(), order.dataHoraCriacao(), order.dataHoraAtualizacao()
            );
        }
    }

    public record FailureView(boolean enabled, double rate, List<String> productIds, int quantityAbove) {
        static FailureView from(ProcessorProperties.Failure failure) {
            return new FailureView(
                    failure.isEnabled(), failure.getRate(),
                    List.copyOf(failure.getProductIds()), failure.getQuantityAbove()
            );
        }
    }
}
