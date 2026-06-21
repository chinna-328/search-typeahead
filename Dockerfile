# syntax=docker/dockerfile:1

# ---- Stage 1: build the fat jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Now build. Tests are run in CI; skip them here to keep image builds fast.
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Stage 2: minimal runtime ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# curl is used by the container HEALTHCHECK; create an unprivileged user to run as.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 appuser
USER appuser

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

COPY --from=build /workspace/target/search-typeahead-*.jar app.jar

EXPOSE 8080

# Let the JVM see the container's memory limits.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
