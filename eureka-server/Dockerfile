FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-noble
ARG OTEL_JAVA_AGENT_VERSION=2.31.0
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /otel \
    && curl -sSL -o /otel/opentelemetry-javaagent.jar \
       "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
