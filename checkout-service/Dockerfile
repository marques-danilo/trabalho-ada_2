# Build multi-stage: compila com Maven + JDK 21 e executa apenas com a JRE.
# O contexto de build e a raiz do repositorio, porque o modulo depende do pom pai.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Os poms entram primeiro: enquanto as dependencias nao mudarem, esta camada fica em cache.
COPY pom.xml ./
COPY checkout-service/pom.xml checkout-service/
COPY order-processor-service/pom.xml order-processor-service/

COPY checkout-service/src checkout-service/src

# -am constroi tambem o pom pai; os testes rodam no pipeline, nao na imagem.
RUN mvn -B -pl checkout-service -am -DskipTests package


FROM eclipse-temurin:21-jre

WORKDIR /app

# Usuario sem privilegios para rodar a aplicacao.
RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app/data && chown -R spring:spring /app

COPY --from=builder --chown=spring:spring /build/checkout-service/target/checkout-service.jar app.jar

USER spring

EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
