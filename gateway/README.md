# EMS API Gateway

> **Status: IN PROGRESS.** The gateway foundation, centralized routing, correlation logging, JWT validation, role-aware authorization, CORS and secure response headers are merged into `develop`. The next milestone is standardized gateway error handling (issue #23).
>
> **Last updated: 19 November 2026.** Feature PRs target `develop` and merge only after gateway CI, backend tests and Docker checks pass.

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
- Claims: `iss` is `employee-management-system`; `sub` is the existing username/email; `iat` is the issue time; `exp` is the expiration; `roles` is the signed list of Spring Security authorities (for example, `ROLE_ADMIN`).
- Expiration: the backend default is 3,600,000 ms (one hour); JJWT enforces the `exp` claim.
- The signing key must decode from Base64 to at least 32 bytes.

Set `JWT_SECRET` to exactly the same value used by the backend. The gateway has no default key and fails startup if the value is missing, invalid or too weak. Do not create a second key for the gateway. Generate a strong shared key for new environments with `openssl rand -base64 48`, then configure the same secret in both services through environment variables. Never commit the value.

Only `POST /api/auth/login` and CORS `OPTIONS` preflight requests are allowed without a bearer token. The gateway answers valid preflight requests at the edge; the backend continues to enforce authorization for actual API calls. Other routed API requests require a valid bearer token and a signed role claim matching the backend's current route policy:

| Route | Gateway role policy |
| --- | --- |
| `POST /api/auth/login` | Public |
| `OPTIONS` preflight | Handled at gateway edge |
| `/api/auth/register` | `ROLE_ADMIN` |
| `/api/departments/**` | Any known EMS role (`ROLE_ADMIN`, `ROLE_HR_MANAGER`, `ROLE_EMPLOYEE`) |
| Other routed `/api/**` paths | `ROLE_ADMIN` or `ROLE_HR_MANAGER` |

The role claim is a signed snapshot from login. When an employee's role changes, the backend immediately rechecks the current database role, while the gateway role snapshot is refreshed at the next login. Tokens minted before the roles claim was introduced are denied on protected gateway routes (403); users must sign in again to obtain a token with roles. The gateway forwards the original Authorization header unchanged; the backend independently validates the token and remains authoritative for account status, role checks and resource authorization. Role-aware gateway rules are tracked separately in issue #21.

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

The gateway tests start an ephemeral local HTTP backend and verify GET forwarding, query-string preservation, POST body/method forwarding, backend status propagation and unmatched-path handling. JWT tests cover valid HS256/HS384 tokens, missing, malformed, expired, wrong-issuer and incorrectly signed tokens, weak configuration, public login/CORS preflight, role-specific route access, admin-only registration, legacy tokens without roles and unchanged bearer forwarding. Tests use isolated test keys and do not require production credentials or a hosted EMS backend.

## Current scope

Centralized routing, JWT validation, route-role checks, CORS and browser security headers are implemented while leaving the existing MVC runtime separate. The backend remains authoritative for current account state and resource-level permissions. Resilience, rate limiting and additional observability are tracked in separate issues and pull requests.


## Request correlation and logging

The gateway accepts a canonical UUID in `X-Correlation-ID`. Missing or malformed values are replaced with a server-generated UUID. The value is returned in the response and forwarded to the backend so services can correlate related events.

Gateway completion logs contain only the route ID, HTTP method, response status, elapsed milliseconds and correlation ID. Request/response bodies, query strings, authorization headers, cookies, credentials and employee identifiers are deliberately excluded. Client-provided correlation values are validated before use to prevent log injection.

## CORS and browser security headers

The gateway owns the browser-facing CORS policy. Configure `EMS_CORS_ALLOWED_ORIGINS` as a comma-separated list of exact origins (scheme, host and optional port), for example:

```text
EMS_CORS_ALLOWED_ORIGINS=https://ems.example.com,https://admin.example.com
```

The local-development default is `http://localhost:3000`. Do not use `*`: credentialed requests are enabled, and wildcard origins are not valid for that policy. Allowed methods and request headers are intentionally restricted; `X-Correlation-ID` is exposed to browser clients. Preflight requests are handled by the gateway, including requests that do not match a backend route.

The gateway's `DedupeResponseHeader` filter retains the gateway's first value for `Access-Control-Allow-Origin` and `Access-Control-Allow-Credentials`, preventing a conflicting legacy backend value from reaching the browser. Keep the gateway allowlist and any backend allowlist aligned during migration; do not widen either to a wildcard.

The gateway also applies baseline API response headers: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `X-Permitted-Cross-Domain-Policies` and a restrictive `Content-Security-Policy`. Existing downstream values are preserved.

## Gateway error responses

Uncaught gateway failures and gateway authentication/authorization rejections are returned as JSON with a stable schema:

```json
{
  "timestamp": "2026-11-19T10:00:00Z",
  "status": 502,
  "code": "UPSTREAM_BAD_GATEWAY",
  "message": "The gateway could not complete the request to the upstream service.",
  "correlationId": "c2d5a0a1-94e7-4f8f-a8c9-10dcb13e7412"
}
```

The `correlationId` is included when available. Responses use `application/json` and `Cache-Control: no-store`. The gateway never returns exception messages, stack traces, backend addresses, credentials or tokens.

| HTTP status | Error code | Meaning |
| --- | --- | --- |
| 401 | `GATEWAY_UNAUTHORIZED` | Missing or invalid bearer token |
| 403 | `GATEWAY_FORBIDDEN` | Authenticated caller is not permitted |
| 404 | `GATEWAY_NOT_FOUND` | Gateway route/resource was not found |
| 429 | `GATEWAY_RATE_LIMITED` | Request limit exceeded |
| 500 | `GATEWAY_ERROR` | Unexpected gateway failure |
| 502 | `UPSTREAM_BAD_GATEWAY` | Upstream connection or name-resolution failure |
| 503 | `UPSTREAM_UNAVAILABLE` | Upstream work cannot currently be accepted |
| 504 | `UPSTREAM_TIMEOUT` | Upstream request timed out |

Connection failures map to 502, rejected work maps to 503, and timeout failures map to 504. API clients should branch on the numeric `status` or stable `code`, display the safe `message`, and include `correlationId` when reporting a failure. Do not parse internal exception details from the response.
