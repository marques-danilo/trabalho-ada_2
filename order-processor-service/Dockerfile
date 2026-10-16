# Build multi-stage: compila com Maven + JDK 21 e executa apenas com a JRE.
# O contexto de build e a raiz do repositorio, porque o modulo depende do pom pai.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./
COPY checkout-service/pom.xml checkout-service/
COPY order-processor-service/pom.xml order-processor-service/

COPY order-processor-service/src order-processor-service/src

RUN mvn -B -pl order-processor-service -am -DskipTests package


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring \
    && mkdir -p /app/data && chown -R spring:spring /app

COPY --from=builder --chown=spring:spring \
     /build/order-processor-service/target/order-processor-service.jar app.jar

USER spring

EXPOSE 8082

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
