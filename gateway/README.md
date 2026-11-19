# EMS API Gateway

The EMS API Gateway is a separately deployable Spring Boot application. It will become the single external entry point for the existing Employee Management System backend as routing and security features are added in the subsequent gateway issues.

## Architecture

- Existing EMS backend: Java 21, Spring Boot 4.1.1 and Spring MVC.
- Gateway: Java 21, Spring Boot 4.1.1 and Spring Cloud Gateway Server WebFlux.
- The gateway is isolated under this directory so its reactive WebFlux runtime does not alter the existing servlet-based backend.
- The gateway does not connect directly to the EMS database.

## Version alignment

The gateway imports the Spring Cloud 2025.1.4 release-train BOM, which is compatible with the Spring Boot 4.1.x line. Keep Spring Boot and Spring Cloud versions aligned through the official compatibility matrix; do not independently override managed Spring Cloud component versions.

## Run locally

From the repository root:

**Linux / macOS**
```bash
./mvnw --batch-mode -f gateway/pom.xml verify
./mvnw -f gateway/pom.xml spring-boot:run
```

**Windows PowerShell**
```powershell
.\mvnw.cmd --batch-mode -f gateway/pom.xml verify
.\mvnw.cmd -f gateway/pom.xml spring-boot:run
```

The gateway listens on port `8081` by default. Override it with `GATEWAY_PORT`.

## Health

- Health: `http://localhost:8081/actuator/health`
- Info: `http://localhost:8081/actuator/info`

Only the health and info actuator endpoints are exposed over HTTP by this baseline configuration. Detailed health information is not publicly returned.

## Current scope

This first delivery establishes the independently deployable application, reactive runtime, version alignment, baseline health configuration and a startup test. Route forwarding, JWT validation, authorization, rate limiting and observability are tracked in their own issues and will be added in separate pull requests.
