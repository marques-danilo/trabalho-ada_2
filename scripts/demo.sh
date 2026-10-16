#!/usr/bin/env bash
# Roteiro de demonstracao do projeto Flash Sale.
#
# Percorre, em ordem, os 12 passos do roteiro minimo: estoque inicial, fluxo de sucesso ate
# CONFIRMED, consulta com Cache-Aside, 404 apos tres tentativas, fluxo de falha com compensacao
# e devolucao do estoque no Redis.
#
# Pre-requisito: a stack precisa estar no ar (docker compose up -d).
# Uso: ./scripts/demo.sh

set -euo pipefail

CHECKOUT="${CHECKOUT_URL:-http://localhost:8081}"
PROCESSOR="${PROCESSOR_URL:-http://localhost:8082}"
CPF="50329291076"
NOME="Fernando Augusto"

# Pausa para o processamento assincrono concluir antes da proxima consulta.
ESPERA="${ESPERA:-2}"

titulo() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
info()   { printf '\033[0;90m%s\033[0m\n' "$*"; }

# Extrai um campo string de um JSON sem depender de jq.
json_campo() {
  local json="$1" campo="$2"
  sed -n "s/.*\"${campo}\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" <<<"$json" | head -1
}

criar_pedido() {
  local produto="$1" quantidade="$2"
  curl -s -X POST "$CHECKOUT/v1/orders" \
    -H 'Content-Type: application/json' \
    -d "{\"produto_id\":\"$produto\",\"quantidade\":$quantidade,\"cliente_cpf\":\"$CPF\",\"cliente_nome\":\"$NOME\"}"
}

aguardar_servicos() {
  titulo "0. Aguardando checkout-service e order-processor-service"
  for _ in $(seq 1 60); do
    if curl -sf "$CHECKOUT/actuator/health" >/dev/null 2>&1 \
       && curl -sf "$PROCESSOR/actuator/health" >/dev/null 2>&1; then
      info "Ambos os servicos responderam."
      return 0
    fi
    sleep 2
  done
  echo "Servicos nao subiram em tempo. Verifique: docker compose ps" >&2
  exit 1
}

aguardar_servicos

titulo "1. Estoque inicial do produto"
curl -s "$CHECKOUT/v1/stock/PROD-001"; echo
info "O estoque e carregado no Redis na inicializacao do checkout-service."

titulo "2-3. POST /v1/orders -> 202 Accepted"
RESPOSTA_SUCESSO="$(criar_pedido PROD-001 2)"
echo "$RESPOSTA_SUCESSO"
PEDIDO_OK="$(json_campo "$RESPOSTA_SUCESSO" pedido_id)"
info "pedido_id = $PEDIDO_OK (status inicial PROCESSING)"

titulo "4. Estoque reservado atomicamente no Redis"
curl -s "$CHECKOUT/v1/stock/PROD-001"; echo
info "O saldo caiu em 2 unidades no momento da requisicao."

info "Aguardando o consumo da fila order-created..."
sleep "$ESPERA"

titulo "5-6. Pedido persistido pelo order-processor-service com status CONFIRMED"
curl -s "$PROCESSOR/internal/orders/$PEDIDO_OK"; echo
info "Esta e a base propria do Order Processor."

titulo "7-8. GET /v1/orders/{pedido_id} no checkout (Cache-Aside no Redis)"
curl -s "$CHECKOUT/v1/orders/$PEDIDO_OK"; echo
info "Primeira leitura vem do cache; em caso de miss, o checkout consulta sua propria base."

titulo "Retry: pedido inexistente faz 3 tentativas e devolve 404"
curl -s -o /tmp/flash-sale-404.json -w 'HTTP %{http_code} em %{time_total}s\n' \
  "$CHECKOUT/v1/orders/00000000-0000-4000-8000-000000000000"
cat /tmp/flash-sale-404.json; echo
info "O tempo total reflete as 3 tentativas com o intervalo configurado."

titulo "Estoque insuficiente -> 409 Conflict"
curl -s -o /tmp/flash-sale-409.json -w 'HTTP %{http_code}\n' \
  -X POST "$CHECKOUT/v1/orders" -H 'Content-Type: application/json' \
  -d "{\"produto_id\":\"PROD-001\",\"quantidade\":999999,\"cliente_cpf\":\"$CPF\",\"cliente_nome\":\"$NOME\"}"
cat /tmp/flash-sale-409.json; echo

titulo "Validacao de entrada -> 400 Bad Request"
curl -s -o /tmp/flash-sale-400.json -w 'HTTP %{http_code}\n' \
  -X POST "$CHECKOUT/v1/orders" -H 'Content-Type: application/json' \
  -d '{"produto_id":"PROD-001","quantidade":0,"cliente_cpf":"50329291076"}'
cat /tmp/flash-sale-400.json; echo

titulo "9. Cenario de falha: PROD-FAIL sempre falha no processamento"
ESTOQUE_ANTES="$(curl -s "$CHECKOUT/v1/stock/PROD-FAIL")"
echo "Estoque antes: $ESTOQUE_ANTES"

RESPOSTA_FALHA="$(criar_pedido PROD-FAIL 3)"
echo "$RESPOSTA_FALHA"
PEDIDO_FALHA="$(json_campo "$RESPOSTA_FALHA" pedido_id)"
info "pedido_id = $PEDIDO_FALHA"

curl -s "$CHECKOUT/v1/stock/PROD-FAIL"; echo
info "Estoque ja reservado (baixou 3 unidades)."

info "Aguardando order-created -> falha -> order-compensation -> devolucao do estoque..."
sleep "$ESPERA"

titulo "10-11. Compensacao consumida e estoque devolvido ao Redis"
curl -s "$CHECKOUT/v1/stock/PROD-FAIL"; echo
info "O saldo voltou ao valor anterior: a acao compensatoria da SAGA foi aplicada."

titulo "12. Pedido atualizado para CANCELLED"
echo "Base do Order Processor:"
curl -s "$PROCESSOR/internal/orders/$PEDIDO_FALHA"; echo
echo "Consulta publica no Checkout:"
curl -s "$CHECKOUT/v1/orders/$PEDIDO_FALHA"; echo

titulo "Demonstracao concluida"
info "Painel do RabbitMQ: http://localhost:15672 (guest/guest)"
