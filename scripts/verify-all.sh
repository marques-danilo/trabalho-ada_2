#!/usr/bin/env bash
# Verificacao completa do projeto, sem exigir JDK instalado na maquina:
#   1. roda a suite de testes dos dois modulos dentro de um container Maven
#   2. empacota os jars
#   3. sobe a stack no Docker e executa o roteiro de demonstracao de ponta a ponta
#
# Uso (Linux/macOS ou dentro do WSL):
#   ./scripts/verify-all.sh
#
# Uso no Windows com Docker no WSL, a partir do PowerShell:
#   wsl -e bash scripts/verify-all.sh

set -eu

cd "$(dirname "$0")/.."

MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
# Volume nomeado para o cache do Maven: evita rebaixar as dependencias a cada execucao.
M2_VOLUME=flash-sale-m2

echo "############ 1/3  Testes (unitarios + integracao) ############"
docker run --rm \
  -v "$PWD":/app \
  -v "$M2_VOLUME":/root/.m2 \
  -w /app "$MAVEN_IMAGE" \
  mvn -B test

echo
echo "############ 2/3  Empacotamento dos jars ############"
docker run --rm \
  -v "$PWD":/app \
  -v "$M2_VOLUME":/root/.m2 \
  -w /app "$MAVEN_IMAGE" \
  mvn -B -DskipTests package

ls -lh checkout-service/target/checkout-service.jar \
       order-processor-service/target/order-processor-service.jar

echo
echo "############ 3/3  Fluxos end-to-end no Docker ############"
bash scripts/smoke-test.sh

echo
echo "############ Estado da infraestrutura ############"
bash scripts/inspect.sh

echo
echo "Verificacao concluida."
