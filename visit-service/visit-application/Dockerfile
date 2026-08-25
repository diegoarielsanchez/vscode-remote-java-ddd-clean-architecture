FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
# domain-commons is a sibling module outside this service's own directory, so it
# is supplied as a separate named build context (see --build-context in the
# Jenkinsfile / docker-compose.yml) and installed into the local repo first.
COPY --from=domain-commons . /domain-commons-src
RUN mvn -f /domain-commons-src/pom.xml install -DskipTests --no-transfer-progress

# visit-infra depends directly on msr-domain and hcp-domain (cross-service
# reads used by its anti-corruption layer). Each of those poms declares a
# <relativePath>../pom.xml</relativePath> (explicit or default) to its own
# service's aggregator pom, so the whole sibling service tree — not just the
# domain module — has to be supplied as a named build context to resolve it.
COPY --from=medical-sales-rep-service . /medical-sales-rep-service-src
RUN mvn -f /medical-sales-rep-service-src/msr-domain/pom.xml install -DskipTests --no-transfer-progress
COPY --from=healthcare-prof-service . /healthcare-prof-service-src
RUN mvn -f /healthcare-prof-service-src/hcp-domain/pom.xml install -DskipTests --no-transfer-progress

COPY . .
RUN mvn -pl visit-application -am clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-noble
ARG OTEL_JAVA_AGENT_VERSION=2.31.0
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /otel \
    && curl -sSL -o /otel/opentelemetry-javaagent.jar \
       "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar"
WORKDIR /app
COPY --from=builder /build/visit-application/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
