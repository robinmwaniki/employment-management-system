# Build the standalone gateway with the repository's pinned Maven wrapper.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw .
COPY gateway/pom.xml gateway/pom.xml
COPY gateway/src/ gateway/src/

RUN chmod +x ./mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -f gateway/pom.xml -DskipTests package

# Keep the runtime image small and run as a non-root user.
FROM eclipse-temurin:21-jre

# curl is used only by the container health check; no shell-based health probe
# or application endpoint is exposed beyond the existing read-only /livez route.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app

WORKDIR /app
COPY --from=build --chown=10001:10001 \
    /workspace/gateway/target/ems-api-gateway.jar /app/app.jar

USER 10001:10001
EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD ["curl", "--fail", "--silent", "--show-error", "http://127.0.0.1:8081/livez"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
