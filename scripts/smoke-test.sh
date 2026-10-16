#!/usr/bin/env bash
# Sobe a stack completa, aguarda os servicos ficarem saudaveis e executa o roteiro de
# demonstracao de ponta a ponta.
#
# Uso (Linux/macOS ou dentro do WSL):
#   ./scripts/smoke-test.sh
#
# Uso no Windows com Docker no WSL, a partir do PowerShell:
#   wsl -e bash scripts/smoke-test.sh

set -eu

cd "$(dirname "$0")/.."

echo "== Subindo a stack (redis, rabbitmq, checkout-service, order-processor-service)"
docker compose up -d --build

echo "== Aguardando os servicos responderem em /actuator/health"
pronto=0
for i in $(seq 1 90); do
  if curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1 \
     && curl -sf http://localhost:8082/actuator/health >/dev/null 2>&1; then
    echo "   ambos saudaveis (tentativa $i)"
    pronto=1
    break
  fi
  sleep 2
done

docker compose ps --format 'table {{.Name}}\t{{.Status}}'

if [ "$pronto" -ne 1 ]; then
  echo
  echo "!! Os servicos nao ficaram saudaveis. Ultimas linhas dos logs:" >&2
  docker compose logs --tail=60 checkout-service order-processor-service >&2
  exit 1
fi

echo
bash scripts/demo.sh
