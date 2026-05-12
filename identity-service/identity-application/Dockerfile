FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user (OWASP A06: least privilege)
RUN addgroup -S identity && adduser -S identity -G identity

COPY target/identity-application-*.jar app.jar
RUN chown identity:identity app.jar

USER identity

ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8090

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
