package com.marketplace.checkout.api;

import com.marketplace.checkout.api.dto.CreateOrderRequest;
import com.marketplace.checkout.api.dto.CreateOrderResponse;
import com.marketplace.checkout.api.dto.OrderResponse;
import com.marketplace.checkout.application.CreateOrderUseCase;
import com.marketplace.checkout.application.GetOrderUseCase;
import com.marketplace.checkout.application.command.CreateOrderCommand;
import com.marketplace.checkout.domain.model.CustomerOrder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;

    public OrderController(CreateOrderUseCase createOrder, GetOrderUseCase getOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
    }

    /**
     * Aceita o pedido apos reservar o estoque e publicar na fila. Responde 202 porque a
     * persistencia e a confirmacao acontecem de forma assincrona no Order Processor.
     */
    @PostMapping
    public ResponseEntity<CreateOrderResponse> criar(@Valid @RequestBody CreateOrderRequest request) {
        CustomerOrder order = createOrder.handle(new CreateOrderCommand(
                request.produtoId(),
                request.quantidade(),
                request.clienteCpf(),
                request.clienteNome()
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CreateOrderResponse.from(order));
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<OrderResponse> consultar(@PathVariable UUID pedidoId) {
        return ResponseEntity.ok(OrderResponse.from(getOrder.handle(pedidoId)));
    }
}
