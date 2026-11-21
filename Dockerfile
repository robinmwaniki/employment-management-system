# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Use the repository-pinned Maven distribution for reproducible builds.
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 chmod +x ./mvnw \
    && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user. The application keeps no files on disk.
RUN groupadd --system --gid 10001 ems \
    && useradd --system --uid 10001 --gid ems --home-dir /app ems \
    && chown -R ems:ems /app

COPY --from=build --chown=ems:ems /workspace/target/employee-management-system.jar /app/app.jar

USER ems
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Africa/Nairobi"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
