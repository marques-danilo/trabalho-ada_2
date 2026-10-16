package com.marketplace.checkout.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /v1/orders}.
 *
 * <p>Os nomes chegam em snake_case ({@code produto_id}, {@code cliente_cpf}) por conta da
 * estrategia de nomenclatura configurada no Jackson.</p>
 */
public record CreateOrderRequest(

        @NotBlank(message = "O campo produto_id e obrigatorio")
        @Size(max = 60, message = "O campo produto_id excede o tamanho maximo permitido")
        String produtoId,

        @Min(value = 1, message = "A quantidade deve ser maior que zero")
        int quantidade,

        @NotBlank(message = "O campo cliente_cpf e obrigatorio")
        @Pattern(regexp = "\\d{11}", message = "O campo cliente_cpf deve conter 11 digitos")
        String clienteCpf,

        @Size(max = 150, message = "O campo cliente_nome excede o tamanho maximo permitido")
        String clienteNome
) {
}
