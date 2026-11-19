# EMS API Gateway

The EMS API Gateway is a separately deployable Spring Boot application and the external entry point for the Employee Management System REST API.

## Architecture

- Existing EMS backend: Java 21, Spring Boot 4.1.1 and Spring MVC.
- Gateway: Java 21, Spring Boot 4.1.1 and Spring Cloud Gateway Server WebFlux.
- The gateway is isolated under this directory so its reactive WebFlux runtime does not alter the existing servlet-based backend.
- The gateway does not connect directly to the EMS database.

## Version alignment

The gateway imports the Spring Cloud 2025.1.4 release-train BOM. Keep Spring Boot and Spring Cloud versions aligned through the official compatibility matrix; do not independently override managed Spring Cloud component versions.

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

## Backend destination

Set `EMS_BACKEND_URI` to the base URL of the existing EMS backend. It defaults to `http://localhost:8080` for local development. Configure the backend destination through the deployment environment; do not commit production hostnames or credentials.

## Public API route table

All listed paths are forwarded to the same configured backend destination. The gateway preserves the incoming HTTP method, path, query string, request body and backend response.

| Gateway path | Backend API area |
| --- | --- |
| `/api/auth/**` | Login, registration, identity and password changes |
| `/api/employees/**` | Employee management and employee photos |
| `/api/departments/**` | Department management |
| `/api/attendance/**` | Attendance operations |
| `/api/leaves/**` | Leave applications and decisions |
| `/api/payroll/**` | Payroll operations |
| `/api/payslips/**` | Payslip PDF downloads |
| `/api/reports/**` | Management reports |
| `/api/performance/**` | Performance reviews |
| `/api/announcements/**` | Announcements |
| `/api/notifications/**` | Notifications |
| `/api/dashboard/**` | Dashboard statistics |
| `/api/users/**` | User administration |
| `/api/me/**` | Authenticated employee self-service |
| `/api/system-logs/**` | System audit logs |

Only the API paths above are routed. Other paths are not proxied and return a gateway 404 response. Backend authorization remains authoritative; gateway authentication and authorization are delivered in separate security issues.

## Health

- Health: `http://localhost:8081/actuator/health`
- Info: `http://localhost:8081/actuator/info`

Only the health and info actuator endpoints are exposed over HTTP. Detailed health information is not publicly returned.

## Tests

The gateway tests start an ephemeral local HTTP backend and verify GET forwarding, query-string preservation, POST body/method forwarding, backend status propagation and unmatched-path handling. They do not require production credentials or a hosted EMS backend.

## Current scope

This delivery adds explicit centralized API routing while leaving the existing MVC backend unchanged. JWT validation, role authorization, CORS/security headers, resilience, rate limiting and observability are tracked in separate issues and pull requests.


## Request correlation and logging

The gateway accepts a canonical UUID in `X-Correlation-ID`. Missing or malformed values are replaced with a server-generated UUID. The value is returned in the response and forwarded to the backend so services can correlate related events.

Gateway completion logs contain only the route ID, HTTP method, response status, elapsed milliseconds and correlation ID. Request/response bodies, query strings, authorization headers, cookies, credentials and employee identifiers are deliberately excluded. Client-provided correlation values are validated before use to prevent log injection.
