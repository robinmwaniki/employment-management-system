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

## JWT authentication

The gateway validates the existing EMS backend JWT format before forwarding protected API requests. It uses JJWT 0.12.7, the same Base64-encoded HMAC signing key, issuer and token conventions as the backend:

- Issuer: `employee-management-system`.
- Subject: the existing EMS username/email.
- Signature: HMAC selected by JJWT from the configured key length (the backend uses `signWith(signingKey)`). A 32–47 byte key selects HS256, a 48–63 byte key selects HS384, and a key of at least 64 bytes selects HS512. The current checked-in development key decodes to 52 bytes, so it uses HS384.
- Claims: `iss` is `employee-management-system`; `sub` is the existing username/email; `iat` is the issue time; `exp` is the expiration. The backend does not embed role claims in these tokens.
- Expiration: the backend default is 3,600,000 ms (one hour); JJWT enforces the `exp` claim.
- The signing key must decode from Base64 to at least 32 bytes.

Set `JWT_SECRET` to exactly the same value used by the backend. The gateway has no default key and fails startup if the value is missing, invalid or too weak. Do not create a second key for the gateway. Generate a strong shared key for new environments with `openssl rand -base64 48`, then configure the same secret in both services through environment variables. Never commit the value.

Only `POST /api/auth/login` is public at the gateway. Other routed API requests require a valid bearer token. The gateway forwards the original Authorization header unchanged; the backend independently validates the token and remains authoritative for account status, role checks and resource authorization. Role-aware gateway rules are tracked separately in issue #21.

For local PowerShell runs, set the environment variable in the current shell from the local secret manager or `.env` value before starting the gateway:

```powershell
$secureSecret = Read-Host 'Enter the shared EMS JWT_SECRET' -AsSecureString
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
try {
    $env:JWT_SECRET = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    $secureSecret.Dispose()
}
.\\mvnw.cmd -f gateway/pom.xml spring-boot:run
Remove-Item Env:JWT_SECRET
```

The value is held only in the current shell session; do not paste it into terminal transcripts, issue comments or logs.

## Health

- Health: `http://localhost:8081/actuator/health`
- Info: `http://localhost:8081/actuator/info`

Only the health and info actuator endpoints are exposed over HTTP. Detailed health information is not publicly returned.

## Tests

The gateway tests start an ephemeral local HTTP backend and verify GET forwarding, query-string preservation, POST body/method forwarding, backend status propagation and unmatched-path handling. JWT tests cover valid HS256/HS384 tokens, missing, malformed, expired, wrong-issuer and incorrectly signed tokens, weak configuration, the public login route and unchanged bearer forwarding. Tests use isolated test keys and do not require production credentials or a hosted EMS backend.

## Current scope

Centralized routing and JWT signature/issuer/expiration validation are implemented while leaving the existing MVC backend unchanged. Role-aware gateway authorization, CORS/security headers, resilience, rate limiting and additional observability are tracked in separate issues and pull requests.


## Request correlation and logging

The gateway accepts a canonical UUID in `X-Correlation-ID`. Missing or malformed values are replaced with a server-generated UUID. The value is returned in the response and forwarded to the backend so services can correlate related events.

Gateway completion logs contain only the route ID, HTTP method, response status, elapsed milliseconds and correlation ID. Request/response bodies, query strings, authorization headers, cookies, credentials and employee identifiers are deliberately excluded. Client-provided correlation values are validated before use to prevent log injection.
