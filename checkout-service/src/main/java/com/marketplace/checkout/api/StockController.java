package com.marketplace.checkout.api;

import com.marketplace.checkout.api.dto.ErrorResponse;
import com.marketplace.checkout.domain.port.StockPort;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints auxiliares de estoque, fora do escopo funcional do cliente final.
 *
 * <p>Existem para a demonstracao: conferir o saldo antes e depois de um pedido e preparar
 * cenarios de teste sem precisar de um cliente Redis.</p>
 */
@RestController
@RequestMapping("/v1/stock")
@Validated
public class StockController {

    private final StockPort stock;

    public StockController(StockPort stock) {
        this.stock = stock;
    }

    @GetMapping("/{produtoId}")
    public ResponseEntity<?> consultar(@PathVariable String produtoId) {
        Long saldo = stock.available(produtoId);
        if (saldo == null) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("Produto sem estoque cadastrado: " + produtoId));
        }
        return ResponseEntity.ok(new StockResponse(produtoId, saldo));
    }

    @PutMapping("/{produtoId}")
    public ResponseEntity<StockResponse> definir(@PathVariable String produtoId,
                                                 @RequestParam @Min(0) long quantidade) {
        stock.setStock(produtoId, quantidade);
        return ResponseEntity.ok(new StockResponse(produtoId, quantidade));
    }

    public record StockResponse(String produtoId, long quantidadeDisponivel) {
    }
}
