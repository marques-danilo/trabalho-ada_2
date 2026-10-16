package com.marketplace.checkout.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.checkout.domain.model.CustomerOrder;
import com.marketplace.checkout.domain.port.OrderCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de consulta de pedidos no Redis, serializado como JSON sob a chave {@code order:{id}}.
 *
 * <p>Falhas de cache sao degradacao, nao erro: se o Redis estiver indisponivel a consulta segue
 * para o banco em vez de devolver erro ao cliente.</p>
 */
@Repository
@Profile("!test")
public class RedisOrderCacheAdapter implements OrderCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisOrderCacheAdapter.class);
    private static final String ORDER_KEY_PREFIX = "order:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisOrderCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CustomerOrder> find(UUID pedidoId) {
        try {
            String json = redis.opsForValue().get(orderKey(pedidoId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CustomerOrder.class));
        } catch (JsonProcessingException e) {
            log.warn("Conteudo invalido no cache para o pedido {}; removendo a chave", pedidoId, e);
            evict(pedidoId);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Falha ao ler o cache do pedido {}; seguindo para o banco", pedidoId, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(CustomerOrder order, Duration ttl) {
        try {
            redis.opsForValue().set(
                    orderKey(order.pedidoId()),
                    objectMapper.writeValueAsString(order),
                    ttl
            );
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Falha ao gravar o cache do pedido {}", order.pedidoId(), e);
        }
    }

    @Override
    public void evict(UUID pedidoId) {
        try {
            redis.delete(orderKey(pedidoId));
        } catch (RuntimeException e) {
            log.warn("Falha ao remover o cache do pedido {}", pedidoId, e);
        }
    }

    private String orderKey(UUID pedidoId) {
        return ORDER_KEY_PREFIX + pedidoId;
    }
}
