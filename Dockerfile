FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl && \
    curl -L -o /app/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

COPY target/negotiation-simulator-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", \
  "-Dotel.java.global-autoconfigure.enabled=true", \
  "-javaagent:/app/opentelemetry-javaagent.jar", \
  "-jar", "app.jar"]
