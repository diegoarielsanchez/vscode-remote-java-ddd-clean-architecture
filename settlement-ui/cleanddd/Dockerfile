FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN mvn -f settlement-ui/cleanddd/pom.xml clean package -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/settlement-ui/cleanddd/target/*.war app.war
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.war"]
