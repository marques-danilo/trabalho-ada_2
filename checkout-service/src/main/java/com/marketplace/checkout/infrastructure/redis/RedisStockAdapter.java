package com.marketplace.checkout.infrastructure.redis;

import com.marketplace.checkout.domain.port.StockPort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * Controle de estoque no Redis.
 *
 * <p>A reserva usa um script Lua porque o Redis executa cada script de forma atomica: a leitura
 * do saldo e o decremento acontecem sem janela para outra requisicao entrar no meio. Fazer
 * {@code GET} seguido de {@code DECRBY} na aplicacao permitiria que duas requisicoes concorrentes
 * lessem o mesmo saldo e vendessem a mesma unidade duas vezes (overselling).</p>
 */
@Repository
@Profile("!test")
public class RedisStockAdapter implements StockPort {

    private static final String STOCK_KEY_PREFIX = "stock:";
    private static final String COMPENSATION_KEY_PREFIX = "compensation:";
    private static final Duration COMPENSATION_MARK_TTL = Duration.ofDays(1);

    private static final long PRODUTO_INEXISTENTE = -2L;
    private static final long SALDO_INSUFICIENTE = -1L;

    /**
     * Devolve o saldo restante em caso de sucesso, -1 para saldo insuficiente e -2 quando o
     * produto nao tem estoque cadastrado. Nenhuma escrita ocorre nos dois casos de recusa.
     */
    private static final RedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local saldo = redis.call('GET', KEYS[1])
            if not saldo then
              return -2
            end
            local solicitado = tonumber(ARGV[1])
            if tonumber(saldo) < solicitado then
              return -1
            end
            return redis.call('DECRBY', KEYS[1], solicitado)
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisStockAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public ReservationResult reserve(String produtoId, int quantidade) {
        Long resultado = redis.execute(
                RESERVE_SCRIPT,
                List.of(stockKey(produtoId)),
                String.valueOf(quantidade)
        );

        if (resultado == null || resultado == PRODUTO_INEXISTENTE) {
            return ReservationResult.productNotFound();
        }
        if (resultado == SALDO_INSUFICIENTE) {
            return ReservationResult.insufficient();
        }
        return ReservationResult.reserved(resultado);
    }

    @Override
    public long release(String produtoId, int quantidade) {
        Long saldo = redis.opsForValue().increment(stockKey(produtoId), quantidade);
        return saldo == null ? 0L : saldo;
    }

    @Override
    public Long available(String produtoId) {
        String valor = redis.opsForValue().get(stockKey(produtoId));
        return valor == null ? null : Long.parseLong(valor);
    }

    @Override
    public void setStock(String produtoId, long quantidade) {
        redis.opsForValue().set(stockKey(produtoId), String.valueOf(quantidade));
    }

    @Override
    public boolean initializeIfAbsent(String produtoId, long quantidade) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(stockKey(produtoId), String.valueOf(quantidade))
        );
    }

    @Override
    public boolean markCompensationOnce(String pedidoId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                COMPENSATION_KEY_PREFIX + pedidoId, "1", COMPENSATION_MARK_TTL
        ));
    }

    private String stockKey(String produtoId) {
        return STOCK_KEY_PREFIX + produtoId;
    }
}
