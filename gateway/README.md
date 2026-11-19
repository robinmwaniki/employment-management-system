# EMS API Gateway

> **Status: IN PROGRESS.** The gateway foundation, centralized routing, correlation logging, JWT validation, role-aware authorization, CORS and secure response headers are merged into `develop`. Standardized gateway error responses are implemented. The current milestone is Redis-backed rate limiting (issue #24).
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

Centralized routing, JWT validation and route-role checks are implemented while leaving the existing MVC runtime separate. The backend remains authoritative for current account state and resource-level permissions. Resilience, rate limiting, metrics, tracing, dashboards and additional observability are tracked in separate issues and pull requests.


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



## Redis-backed rate limiting

The gateway uses an atomic Redis Lua token-bucket filter backed by Spring Data Redis Reactive. The script performs refill, consumption and expiry in one Redis operation using Redis server time. Redis is shared across gateway instances, so quotas remain consistent when the gateway is horizontally scaled. The backend remains unchanged and does not need a Redis dependency.

### Quota policies

The defaults are deliberately conservative starting values and can be tuned per environment:

| Request category | Refill rate | Burst capacity | Environment variables |
| --- | ---: | ---: | --- |
| General routed API | 10 tokens/second | 20 | `EMS_API_RATE_LIMIT_REPLENISH_RATE`, `EMS_API_RATE_LIMIT_BURST_CAPACITY` |
| Login | 1 token/second | 5 | `EMS_LOGIN_RATE_LIMIT_REPLENISH_RATE`, `EMS_LOGIN_RATE_LIMIT_BURST_CAPACITY` |
| Registration | 1 token/second | 3 | `EMS_REGISTER_RATE_LIMIT_REPLENISH_RATE`, `EMS_REGISTER_RATE_LIMIT_BURST_CAPACITY` |
| Reports | 2 tokens/second | 4 | `EMS_REPORTS_RATE_LIMIT_REPLENISH_RATE`, `EMS_REPORTS_RATE_LIMIT_BURST_CAPACITY` |

Login, registration and report requests use independent Redis key namespaces and policies. General API requests use the general bucket. This prevents one category from consuming another category's token state. CORS preflight requests are handled before route filters and do not consume API quota.

### Identity and proxy trust

Authenticated requests are keyed by a SHA-256 hash of the verified JWT subject. Anonymous requests, including login, are keyed by a SHA-256 hash of the client IP. Raw usernames, email addresses, bearer tokens and IP addresses are not used as Redis keys or written to rate-limit logs.

The resolver uses the socket peer address by default. It reads `X-Forwarded-For` only when the immediate peer belongs to `EMS_TRUSTED_PROXY_CIDRS`. Configure this variable as a comma-separated list of exact proxy CIDR ranges, for example:

```text
EMS_TRUSTED_PROXY_CIDRS=10.20.0.0/16,192.0.2.10/32
```

The resolver walks the forwarded chain from right to left and selects the first address outside the trusted proxy ranges. Do not add broad public networks to this allowlist. When no trusted proxy range is configured, forwarded headers are ignored.

### Redis connection settings

| Variable | Default | Purpose |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | empty | Redis password, if configured |
| `REDIS_CONNECT_TIMEOUT` | `2s` | Connection timeout |
| `REDIS_TIMEOUT` | `2s` | Command timeout |

For local development, start Redis from the repository root:

```bash
docker compose -f gateway/docker-compose.redis.yml up -d
```

Stop the local Redis service with:

```bash
docker compose -f gateway/docker-compose.redis.yml down
```

Set the same Redis environment variables for each gateway instance. The gateway CI workflow starts an isolated Redis 7 service for deterministic integration tests.

### Rejection and Redis outage behavior

When a quota is exhausted, the gateway returns HTTP `429 Too Many Requests` with `Retry-After: 1`. Clients should respect the header and use exponential backoff for repeated failures.

Rate limiting fails closed. The custom Lua filter propagates Redis command failures instead of using Spring Cloud Gateway's built-in Redis limiter behavior that can fail open. If Redis cannot be reached, the global error handler returns a generic HTTP `503 Service Unavailable` response with `Retry-After: 5`; the gateway does not silently bypass protection. The response body follows the shared privacy-safe gateway error schema. Redis connection details and exception messages are never returned to clients.

### Verification

The automated suite covers hashed JWT identities, anonymous client-IP keys, ignored spoofed forwarding headers, trusted-proxy chain resolution, malformed proxy headers, invalid CIDR configuration, login quota exhaustion, HTTP 429 retry guidance and Redis failure-to-503 mapping.
