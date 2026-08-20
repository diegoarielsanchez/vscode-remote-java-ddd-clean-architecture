FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
# domain-commons is a sibling module outside this service's own directory, so it
# is supplied as a separate named build context (see --build-context in the
# Jenkinsfile / docker-compose.yml) and installed into the local repo first.
COPY --from=domain-commons . /domain-commons-src
RUN mvn -f /domain-commons-src/pom.xml install -DskipTests --no-transfer-progress
COPY . .
RUN mvn -pl settlement-application -am clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/settlement-application/target/*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
