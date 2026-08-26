# Build context MUST be visit-service/ (this Dockerfile's parent directory),
# not the repo root. This service also needs two sibling service trees as
# extra named contexts (see below). From the repo root:
#   DOCKER_BUILDKIT=1 docker build \
#     --build-context domain-commons=domain-commons \
#     --build-context medical-sales-rep-service=medical-sales-rep-service \
#     --build-context healthcare-prof-service=healthcare-prof-service \
#     -f visit-service/visit-application/Dockerfile \
#     -t visit-service:local visit-service
# or simply: ./visit-service/build.sh
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
# domain-commons is a sibling module outside this service's own directory, so it
# is supplied as a separate named build context (see --build-context in the
# Jenkinsfile / docker-compose.yml) and installed into the local repo first.
COPY --from=domain-commons . /domain-commons-src
RUN mvn -f /domain-commons-src/pom.xml install -DskipTests --no-transfer-progress

# visit-infra depends directly on msr-domain and hcp-domain (cross-service
# reads used by its anti-corruption layer). Each of those poms declares
# <relativePath>../pom.xml</relativePath> to its own service's aggregator pom,
# so the whole sibling service tree — not just the domain module — has to be
# supplied as a named build context to resolve it.
#
# Installing msr-domain/hcp-domain alone is not enough: once a pom is
# installed, Maven resolves its <parent> from the *installed pom's location*
# in the repo, not from the original source tree — so relativePath no longer
# finds ../pom.xml, and Maven falls back to looking up the parent by GAV in
# the repo. If that aggregator pom was never installed, resolution fails with
# "Could not find artifact com.das.msr:medical-sales-rep-service:pom" the
# next time msr-domain is resolved as a plain (non-reactor) dependency — which
# is exactly what happens when visit-infra pulls it in below. `install -N`
# (non-recursive) registers just the aggregator pom, without building or
# installing its other modules (msr-infra, msr-application, ...), which
# visit-infra does not need.
COPY --from=medical-sales-rep-service . /medical-sales-rep-service-src
RUN mvn -f /medical-sales-rep-service-src/pom.xml install -N -DskipTests --no-transfer-progress
RUN mvn -f /medical-sales-rep-service-src/msr-domain/pom.xml install -DskipTests --no-transfer-progress
COPY --from=healthcare-prof-service . /healthcare-prof-service-src
RUN mvn -f /healthcare-prof-service-src/pom.xml install -N -DskipTests --no-transfer-progress
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
