package com.marketplace.checkout.infrastructure.memory;

import com.marketplace.checkout.domain.port.StockPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Substituto do Redis nos testes automatizados.
 *
 * <p>Reproduz a semantica que importa para as regras de negocio: a reserva e atomica e nunca
 * deixa o saldo negativo. Onde o adaptador real usa um script Lua, aqui um bloco sincronizado
 * cumpre o mesmo papel.</p>
 */
@Repository
@Profile("test")
public class InMemoryStockAdapter implements StockPort {

    private final Map<String, AtomicLong> estoque = new ConcurrentHashMap<>();
    private final Set<String> compensacoesAplicadas = ConcurrentHashMap.newKeySet();

    @Override
    public ReservationResult reserve(String produtoId, int quantidade) {
        AtomicLong saldo = estoque.get(produtoId);
        if (saldo == null) {
            return ReservationResult.productNotFound();
        }
        synchronized (saldo) {
            if (saldo.get() < quantidade) {
                return ReservationResult.insufficient();
            }
            return ReservationResult.reserved(saldo.addAndGet(-quantidade));
        }
    }

    @Override
    public long release(String produtoId, int quantidade) {
        return estoque.computeIfAbsent(produtoId, k -> new AtomicLong()).addAndGet(quantidade);
    }

    @Override
    public Long available(String produtoId) {
        AtomicLong saldo = estoque.get(produtoId);
        return saldo == null ? null : saldo.get();
    }

    @Override
    public void setStock(String produtoId, long quantidade) {
        estoque.put(produtoId, new AtomicLong(quantidade));
    }

    @Override
    public boolean initializeIfAbsent(String produtoId, long quantidade) {
        return estoque.putIfAbsent(produtoId, new AtomicLong(quantidade)) == null;
    }

    @Override
    public boolean markCompensationOnce(String pedidoId) {
        return compensacoesAplicadas.add(pedidoId);
    }

    /** Devolve o adaptador ao estado inicial entre testes. */
    public void reset() {
        estoque.clear();
        compensacoesAplicadas.clear();
    }
}
