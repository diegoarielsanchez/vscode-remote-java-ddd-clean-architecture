# Build context MUST be identity-service/ (this Dockerfile's parent directory),
# not the repo root — `COPY . .` below expects to land identity-service's own
# reactor pom at /build. identity-service has no dependency on domain-commons,
# so no --build-context flag is needed. From the repo root:
#   docker build -f identity-service/identity-application/Dockerfile \
#     -t identity-service:local identity-service
# or simply: ./identity-service/build.sh
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY . .
RUN mvn -pl identity-application -am clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-noble
ARG OTEL_JAVA_AGENT_VERSION=2.31.0
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /otel \
    && curl -sSL -o /otel/opentelemetry-javaagent.jar \
       "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"
WORKDIR /app
COPY --from=builder /build/identity-application/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
