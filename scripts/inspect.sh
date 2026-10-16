#!/usr/bin/env bash
# Inspeciona o estado da infraestrutura: filas no RabbitMQ, chaves no Redis e as linhas de log
# que evidenciam cada passo da SAGA. Util durante a apresentacao.
#
# Uso (Linux/macOS ou dentro do WSL):
#   ./scripts/inspect.sh
#
# Uso no Windows com Docker no WSL, a partir do PowerShell:
#   wsl -e bash scripts/inspect.sh

set -eu

cd "$(dirname "$0")/.."

echo "=== Filas no RabbitMQ ==="
docker compose exec -T rabbitmq \
  rabbitmqctl list_queues name messages messages_ready consumers

echo
echo "=== Chaves no Redis ==="
echo "-- estoque (stock:<produto_id>)"
docker compose exec -T redis sh -c 'redis-cli --scan --pattern "stock:*" | sort | while read -r k; do echo "$k = $(redis-cli GET "$k")"; done'
echo "-- cache de pedidos (order:<pedido_id>), com TTL em segundos"
docker compose exec -T redis sh -c 'redis-cli --scan --pattern "order:*" | sort | while read -r k; do echo "$k ttl=$(redis-cli TTL "$k")s"; done'
echo "-- marcas de compensacao aplicada (compensation:<pedido_id>)"
docker compose exec -T redis sh -c 'redis-cli --scan --pattern "compensation:*" | sort'

echo
echo "=== Log do checkout-service (producer e consumer de compensacao) ==="
docker compose logs --no-log-prefix checkout-service \
  | grep -E 'Estoque reservado|publicado na fila|compensacao recebida|Compensacao aplicada|atualizado para' \
  | tail -15 || echo "(nenhuma linha correspondente ainda)"

echo
echo "=== Log do order-processor-service (consumer e publisher da SAGA) ==="
docker compose logs --no-log-prefix order-processor-service \
  | grep -E 'recebido da fila|CONFIRMED|CANCELLED|Compensacao do pedido|publicado na fila' \
  | tail -15 || echo "(nenhuma linha correspondente ainda)"
