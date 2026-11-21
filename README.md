# Employee Management System

A role-based HR and workforce management application built with **Java 21, Spring Boot, Spring Security, PostgreSQL, and Thymeleaf**. It combines server-rendered pages with REST endpoints for client integrations.

> **Project status: IN PROGRESS.** The Employee Management System backend, React frontend and standalone reactive API Gateway are under active development. Feature work integrates through `develop`; each feature has its own issue and pull request, and merges are gated on passing CI.
>
> **Latest update — 19 November 2026:** the gateway foundation, centralized routing, correlation IDs/privacy-safe logs, EMS JWT and role authorization, CORS/security headers, standardized JSON errors and Redis-backed rate limiting (issues #18–#24) have been merged into `develop`. Resilience controls were merged in PR #49 (issue #25). Actuator health probes and protected Micrometer metrics were merged in PR #50 (issue #27). P0 gateway end-to-end, security and performance verification was completed in PR #52. Distributed tracing with Micrometer/OpenTelemetry is now in progress (issue #28).

## Overview

The system separates administration and HR workflows from employee self-service. A separate React client is maintained in [ems_frontend](https://github.com/robinmwaniki/ems_frontend).

## Current delivery status

| Area | Status | Notes |
| --- | --- | --- |
| Existing HR/employee management backend | In progress | Spring MVC backend remains the source of truth for account state, role checks and resource authorization |
| Standalone reactive gateway foundation | Completed | Isolated Spring Boot 4.1.1 / Spring Cloud Gateway WebFlux application under `gateway/` |
| Centralized API routing | Completed | Explicit API route table and deterministic forwarding tests |
| JWT authentication | Completed | Shared EMS HMAC signing secret, issuer/expiry validation and backend revalidation |
| Gateway role authorization | Completed | Signed `roles` claims mirror current backend route rules; backend still rechecks current roles |
| Correlation IDs and privacy-safe logging | Completed | Validated UUID propagation and redaction tests |
| CORS and secure response headers | Completed | Exact-origin policy, edge preflight and browser security headers (PR #44) |
| Global error responses | Completed | Stable JSON error contract, safe status mapping and correlation IDs (PR #41) |
| Redis rate limiting | Completed | Distributed token buckets, hashed identities and trusted-proxy validation (PR #47) |
| Resilience4j | Completed | Per-route circuit breakers, timeouts, safe retries and controlled fallback (PR #49) |
| Actuator health and Micrometer metrics | Completed | Liveness/readiness probes, protected operational metrics and bounded-cardinality request measurements (PR #50) |
| Gateway end-to-end, security and performance verification | Completed | 89 gateway tests, Redis integration, backend token/authorization contract, request-size controls, k6 smoke script and gateway image validation (PR #52) |
| Distributed tracing | In progress | W3C context propagation, OTLP export and span privacy controls (issue #28) |
| Prometheus/Grafana and Sentry | Planned | Issues #29–#30 |
| OpenAPI documentation | Planned | Issue #31 |
| Gateway containerization and deployment documentation | On hold | Issue #32 — deployment remains paused |

See [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for the detailed change log and next steps. The gateway implementation details are in [gateway/README.md](gateway/README.md).

## Features

- Employee and department management.
- Role-based user administration.
- Attendance records and leave management.
- Payroll records and payroll views.
- Announcements and notifications.
- Performance records and reporting.
- PDF-oriented report views and system activity logs.
- Employee dashboard, profile, attendance, leave, payroll, notifications, and password-change workflows.
- JWT authentication, request validation, DTOs, and role-based authorization.
- PostgreSQL persistence with Flyway migrations.
- Docker image, Docker Compose, Actuator health endpoints, and OpenAPI integration.

## Architecture

The application follows a layered Spring architecture: controllers handle HTTP requests, services implement business rules, repositories access the database, and JPA entities represent persisted data.

```mermaid
flowchart TD
    U[Staff / Employee] --> UI[Thymeleaf Web UI]
    C[React Client] --> API[REST API]
    UI --> SEC[Spring Security]
    API --> SEC
    SEC --> CTRL[Controllers]
    CTRL --> SVC[Service Layer]
    SVC --> REPO[Spring Data JPA]
    REPO --> DB[(PostgreSQL)]
    MIG[Flyway Migrations] --> DB
```

### Request lifecycle

```mermaid
sequenceDiagram
    actor User
    participant Client
    participant Security as Spring Security
    participant Controller
    participant Service
    participant DB as PostgreSQL
    User->>Client: Submit request
    Client->>Security: HTTP request / bearer token
    Security->>Security: Authenticate and authorize
    Security->>Controller: Authorized request
    Controller->>Service: Execute business operation
    Service->>DB: Read or write data
    DB-->>Service: Result
    Service-->>Client: DTO, response, or rendered view
```

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Web | Spring MVC, Thymeleaf |
| Security | Spring Security, JJWT |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL 17 |
| Migrations | Flyway |
| API docs | Springdoc OpenAPI |
| Operations | Spring Boot Actuator |
| Build / containers | Maven Wrapper, Docker, Docker Compose |
| Testing | Spring Boot Test, Testcontainers |

## Getting started

### Prerequisites

- Git
- Docker Engine and Docker Compose plugin
- Java 21 and PostgreSQL 17 if running outside Docker

### 1. Clone

```bash
git clone https://github.com/robinmwaniki/employment-management-system.git
cd employment-management-system
```

### 2. Create a local .env file

The Compose configuration expects a root-level `.env` file. Create it locally and do not commit it:

```dotenv
DB_USERNAME=ems
DB_PASSWORD=replace-with-a-strong-local-password
JWT_SECRET=REPLACE_WITH_BASE64_ENCODED_32_BYTE_OR_LONGER_SECRET
JWT_EXPIRATION_MS=3600000
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
CORS_ALLOWED_ORIGIN=http://localhost:5173
```

Generate a strong Base64 secret instead of using the placeholder:

```bash
openssl rand -base64 32
```

### 3. Start the stack

```bash
docker compose up --build
```

The Compose stack starts PostgreSQL and the Spring Boot application. Wait for the database health check and application startup to complete.

### 4. Open the application

- Application: [http://localhost:8080](http://localhost:8080)
- Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

Stop the stack with `docker compose down`. Use `docker compose down -v` only when you intend to delete the local database and upload volumes.

## Configuration

| Variable | Purpose |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials |
| `JWT_SECRET` | Base64 HMAC signing key of at least 256 bits |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds |
| `PORT` | HTTP port; defaults to 8080 |
| `CORS_ALLOWED_ORIGIN` | Allowed browser origin |
| `MAIL_HOST`, `MAIL_PORT` | SMTP server settings |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials |

Compose configures the database URL for its internal network. For a locally installed database, use a JDBC URL reachable from the host.

## Database migrations

Flyway migration scripts live in `src/main/resources/db/migration/`. Flyway owns schema changes; Hibernate is configured with `ddl-auto: validate` and checks the mapped schema. Add a migration whenever the database structure changes.

## Security model

| Role | Intended access |
| --- | --- |
| `ROLE_ADMIN` | Administrative access |
| `ROLE_HR_MANAGER` | HR and workforce management |
| `ROLE_EMPLOYEE` | Employee self-service |

API clients authenticate with:

```http
Authorization: Bearer <access-token>
```

Do not commit real credentials, JWT secrets, SMTP passwords, or production tokens.

## Testing

**macOS / Linux**
```bash
./mvnw test
```

**Windows**
```powershell
.\mvnw.cmd test
```

The repository includes security-rule, migration, page-rendering, and Spring Boot tests, plus Testcontainers-based PostgreSQL test support. Docker is required for tests that start database containers.

## API documentation and health

Actuator exposes health and info endpoints. OpenAPI is disabled by default and can be enabled with `SWAGGER_ENABLED` in a trusted development environment. The Swagger UI path is typically `/swagger-ui/index.html`.

## Isolated read-only demo

The backend includes an opt-in `demo` Spring profile for a **separate demo deployment and database**. It is not enabled by default and must never point at production data.

The demo profile:
- Seeds synthetic departments, employees, attendance, leave, announcements, performance reviews and one illustrative payroll snapshot.
- Creates the demo roles `ADMIN`, `HR_MANAGER` and `EMPLOYEE` with synthetic accounts.
- Issues short-lived JWTs through `POST /api/demo/login?role=ADMIN`, `HR_MANAGER` or `EMPLOYEE`; no demo password is embedded in the React bundle.
- Allows read-only API requests and denies all other API write methods. The demo login token exchange is the only permitted POST.
- Disables the legacy Thymeleaf UI and keeps health probes available.

**Never activate this profile against production resources.** Provision a dedicated PostgreSQL database, use a dedicated JWT secret and set a dedicated CORS origin before deploying with `SPRING_PROFILES_ACTIVE=demo`. The sample payroll row is synthetic demonstration data and is not a statutory payroll reference or a government filing.

Local profile example (after creating an isolated demo database and environment variables):

```bash
SPRING_PROFILES_ACTIVE=demo \
DB_URL=jdbc:postgresql://localhost:5432/ems_demo \
DB_USERNAME=ems_demo \
DB_PASSWORD=replace-me \
JWT_SECRET="$(openssl rand -base64 48)" \
CORS_ALLOWED_ORIGIN=http://localhost:5173 \
./mvnw spring-boot:run
```

Demo role token exchange examples:

```http
POST /api/demo/login?role=ADMIN
POST /api/demo/login?role=HR_MANAGER
POST /api/demo/login?role=EMPLOYEE
```

All records use reserved `.test` email addresses and synthetic contact details. Resetting the demo means recreating its database from migrations and allowing the demo profile to seed it again.

## Deployment

For a hosted environment, provision managed PostgreSQL, configure secrets through the host's secret manager, use HTTPS, restrict CORS to trusted frontend origins, and provide persistent or object storage for uploaded files. Do not reuse local Compose credentials or development settings in production.

## Project structure

```text
src/
├── main/java/com/ems/
│   ├── config/       # Security, CORS and application configuration
│   ├── controller/   # HTTP and page controllers
│   ├── dto/          # Request and response models
│   ├── entity/       # JPA entities
│   ├── repository/   # Persistence interfaces
│   └── service/      # Business logic
├── main/resources/
│   ├── db/migration/ # Flyway migrations
│   ├── static/       # Static assets
│   └── templates/    # Thymeleaf pages
└── test/             # Automated tests
Dockerfile
docker-compose.yml
pom.xml
mvnw / mvnw.cmd
```

## Security and license

This project is not a certified HR or payroll platform. Conduct a security review and validate applicable privacy and employment-data requirements before processing real employee information.

No license file is currently declared. Unless a license is added, standard copyright restrictions apply.

## Maintainer

**Robin Mwaniki** · [GitHub](https://github.com/robinmwaniki)
