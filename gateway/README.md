# EMS API Gateway

> **Status: IN PROGRESS.** The gateway foundation, centralized routing, correlation logging, JWT validation, role-aware authorization, CORS, secure response headers and standardized JSON errors are merged into `develop`. The next milestone is Redis-backed request rate limiting (issue #24).
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

Only `POST /api/auth/login` and CORS `OPTIONS` preflight requests are allowed without a bearer token. Preflight requests are forwarded so the backend's existing CORS policy can answer them. Other routed API requests require a valid bearer token and a signed role claim matching the backend's current route policy:

| Route | Gateway role policy |
| --- | --- |
| `POST /api/auth/login` | Public |
| `OPTIONS` preflight | Forwarded to backend CORS handling |
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

Centralized routing, JWT validation, route-role checks, CORS, secure response headers and standardized gateway errors are implemented while leaving the existing MVC runtime separate. The backend remains authoritative for current account state and resource-level permissions. Resilience, metrics, tracing, dashboards and additional observability are tracked in separate issues and pull requests.


## Request correlation and logging

The gateway accepts a canonical UUID in `X-Correlation-ID`. Missing or malformed values are replaced with a server-generated UUID. The value is returned in the response and forwarded to the backend so services can correlate related events.

Gateway completion logs contain only the route ID, HTTP method, response status, elapsed milliseconds and correlation ID. Request/response bodies, query strings, authorization headers, cookies, credentials and employee identifiers are deliberately excluded. Client-provided correlation values are validated before use to prevent log injection.


## CORS configuration

The gateway owns the browser-facing CORS policy for routed API paths. Configure `EMS_CORS_ALLOWED_ORIGINS` as a comma-separated list of exact HTTP(S) origins (scheme, host and optional port), for example:

```text
EMS_CORS_ALLOWED_ORIGINS=https://ems.example.com,https://admin.example.com
```

Local development allows `http://localhost:3000` and `http://localhost:5173` by default. Wildcard origins, origin paths, credentials and query strings are rejected. Credentialed CORS is disabled because the React clients use Authorization bearer tokens rather than cross-origin cookies.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `EMS_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Exact permitted browser origins |
| `EMS_CORS_ALLOWED_METHODS` | `GET,POST,PUT,DELETE,OPTIONS` | Permitted request methods |
| `EMS_CORS_ALLOWED_HEADERS` | `Authorization,Content-Type,Accept,X-Correlation-ID` | Permitted request headers |
| `EMS_CORS_EXPOSED_HEADERS` | `Authorization,X-Correlation-ID` | Response headers readable by browser code |
| `EMS_CORS_MAX_AGE` | `3600` | Preflight cache duration in seconds |

The gateway handles valid preflight requests at the edge for `/api/**`, including paths that are not proxied. Preflight requests do not reach the backend or require a bearer token. Actual API calls still require JWT validation and signed role authorization.

The existing backend CORS policy remains available for direct backend access. Keep its `CORS_ALLOWED_ORIGIN` value aligned with the gateway allowlist during migration. The gateway deduplicates CORS response headers and retains the gateway's first values, preventing duplicate or conflicting origin headers when the legacy backend also emits CORS headers.

## Browser security response headers

A high-priority WebFilter adds these headers to successful responses, CORS preflight responses and gateway-generated 401/403 responses, while preserving any value already set by the downstream service:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: camera=(), geolocation=(), microphone=()`
- `X-Permitted-Cross-Domain-Policies: none`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'`
- `Strict-Transport-Security: max-age=31536000; includeSubDomains`

Browsers honor HSTS only when the response is received over HTTPS; the header is harmless in local HTTP development. The CSP is intentionally restrictive because the gateway serves API responses, not the React UI.

## CORS and security-header tests

Run the deterministic gateway suite with:

```bash
./mvnw --batch-mode -f gateway/pom.xml verify
```

Tests cover allowed and disallowed origins, edge-handled preflight, duplicate backend CORS headers, exact-origin validation, credential disabling, security headers on successful and unauthorized responses, and preservation of downstream header values. They use local mock exchanges and an ephemeral backend; no production credentials or hosted services are required.

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


## Redis-backed request rate limiting

The gateway applies a Redis-backed token bucket before forwarding API requests. The token bucket is updated by one atomic Lua script, so limits are shared across gateway instances. Start a local Redis instance with Docker:

```bash
docker run --name ems-gateway-redis --detach --publish 6379:6379 redis:7-alpine
```

Stop it when finished with `docker stop ems-gateway-redis`. Configure `REDIS_HOST`, `REDIS_PORT`, optional `REDIS_PASSWORD` and `REDIS_TIMEOUT` for deployment. Redis is required when rate limiting is enabled.

| Policy | Refill rate | Burst capacity | Identity | Routes |
| --- | ---: | ---: | --- | --- |
| Login | 2 tokens/second | 5 | Client IP | `POST /api/auth/login` |
| Registration | 1 token/second | 2 | Verified user subject | `POST /api/auth/register` |
| General API | 20 tokens/second | 40 | Verified user subject | Other `/api/**` routes |
| Reports | 2 tokens/second | 4 | Verified user subject | `/api/reports/**` |

Each request consumes the configured `requested-tokens` amount (1 by default). Refill rate, burst capacity and request cost can be overridden independently:

- Login: `EMS_RATE_LIMIT_LOGIN_PER_SECOND`, `EMS_RATE_LIMIT_LOGIN_BURST`, `EMS_RATE_LIMIT_LOGIN_TOKENS`.
- Registration: `EMS_RATE_LIMIT_REGISTRATION_PER_SECOND`, `EMS_RATE_LIMIT_REGISTRATION_BURST`, `EMS_RATE_LIMIT_REGISTRATION_TOKENS`.
- General API: `EMS_RATE_LIMIT_GENERAL_PER_SECOND`, `EMS_RATE_LIMIT_GENERAL_BURST`, `EMS_RATE_LIMIT_GENERAL_TOKENS`.
- Reports: `EMS_RATE_LIMIT_REPORTS_PER_SECOND`, `EMS_RATE_LIMIT_REPORTS_BURST`, `EMS_RATE_LIMIT_REPORTS_TOKENS`.

Set `EMS_RATE_LIMIT_ENABLED=false` only for isolated tests or controlled local troubleshooting. Production should leave it enabled. A rate-limited request receives HTTP 429, the standard `GATEWAY_RATE_LIMITED` JSON body, `X-RateLimit-Limit`, `X-RateLimit-Remaining` and a configurable `Retry-After` header (`EMS_RATE_LIMIT_RETRY_AFTER_SECONDS`, default 5). These response headers are exposed through gateway CORS for browser clients.

If Redis is unavailable, the gateway fails closed: it returns HTTP 503 with code `RATE_LIMITER_UNAVAILABLE` and does not forward the request. It never silently disables the limiter or falls back to an in-memory counter.

The identity resolver uses the verified JWT subject for authenticated requests and a socket-derived client IP for public login requests. Both identities are SHA-256 hashed before being used as Redis keys, so raw usernames and IP addresses are not stored in Redis. `X-Forwarded-For` is ignored by default. To use forwarded addresses, configure `EMS_TRUSTED_PROXY_CIDRS` with exact CIDR ranges for reverse proxies you control. The resolver checks the direct socket peer against that allowlist and walks the forwarded chain from right to left; client-supplied values cannot override the address appended by a trusted proxy. Missing client identity is denied rather than placed in a shared anonymous bucket.

The rate-limit tests use a mocked reactive Redis template and cover allowed requests, exhausted buckets, route policy selection, authenticated subject keys, spoofed forwarded headers, and Redis failure. They do not require a live Redis server or production credentials.
