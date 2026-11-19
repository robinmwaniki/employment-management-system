# EMS API Gateway

> **Status: IN PROGRESS.** The gateway foundation, centralized routing, correlation logging, JWT validation, role-aware authorization, CORS, secure response headers and standardized JSON errors are merged into `develop`. Redis-backed rate limiting is merged into `develop` (issue #24). Resilience4j circuit breakers, safe retries and route timeouts are merged into `develop` (issue #25). Actuator health probes and protected Micrometer metrics are merged in PR #50 (issue #27). P0 end-to-end, security and performance verification is in progress (issue #33).
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

See [TESTING.md](TESTING.md) for the automated verification commands, coverage boundaries, performance smoke test and deferred deployment checklist.

## Run locally

For a complete local stack, including PostgreSQL, the existing EMS backend, Redis and the gateway, configure the required shared secrets in the root `.env` file and start the stack:

```bash
docker compose up --build -d
docker compose ps
docker compose logs -f gateway
```

The gateway is available at `http://localhost:8081`; the backend remains available at `http://localhost:8080`. Redis is internal to the Compose network and is not published to the host. The gateway waits for Redis health and for the backend container to start. The gateway's `/livez` health check is used by Docker; backend availability is deliberately not coupled to process liveness.

To run only the gateway and its Redis dependency while running the backend separately, set `EMS_BACKEND_URI` to a reachable backend URL and use the standalone gateway Docker build command below.

Then run the gateway from the repository root:

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

Only the health, info and metrics Actuator endpoints are exposed over HTTP, and all are read-only. Environment, beans, gateway route configuration, Prometheus and other operational endpoints are not exposed. Health details and component names are hidden.


## Actuator probes and Micrometer metrics

The gateway exposes a deliberately small, read-only Actuator surface:

| Endpoint | Purpose |
| --- | --- |
| `/actuator/health` | Sanitized aggregate health status |
| `/actuator/health/liveness` and `/livez` | Process liveness for container restart decisions |
| `/actuator/health/readiness` and `/readyz` | Gateway readiness for traffic admission |
| `/actuator/info` | Non-sensitive application information |
| `/actuator/metrics` | Available Micrometer metric names |
| `/actuator/metrics/spring.cloud.gateway.requests` | Gateway request count, duration and outcome measurements |

Liveness and readiness groups include only Spring Boot's application availability state. They do not include backend connectivity or circuit-breaker health: an unavailable EMS backend must not cause the gateway process to be restarted or removed from service discovery solely because a downstream dependency is down. The aggregate `/actuator/health` endpoint may still reflect configured circuit-breaker health, but never reveals component details or exception messages.

Spring Cloud Gateway's Micrometer filter records request count and latency through the `spring.cloud.gateway.requests` timer. Its bounded tags include route ID, route URI, outcome, HTTP status and method. Path tags are explicitly disabled to avoid high-cardinality metrics and employee identifiers appearing in telemetry. The metrics endpoint is read-only. The Prometheus scrape endpoint is intentionally not enabled here; exporter and Grafana integration are tracked separately in issue #29.

For Kubernetes probes, use `/livez` as the liveness probe and `/readyz` as the readiness probe. For Docker Compose or other orchestrators, use the same paths as the gateway container health check. Do not use the aggregate health endpoint as liveness because it can include downstream circuit-breaker state.


## Tests

The gateway tests start an ephemeral local HTTP backend and verify GET forwarding, query-string preservation, POST body/method forwarding, backend status propagation and unmatched-path handling. JWT tests cover valid HS256/HS384 tokens, missing, malformed, expired, wrong-issuer and incorrectly signed tokens, weak configuration, public login/CORS preflight, role-specific route access, admin-only registration, legacy tokens without roles and unchanged bearer forwarding. Tests use isolated test keys and do not require production credentials or a hosted EMS backend.

## Current scope

Centralized routing, JWT validation, route-role checks, CORS, secure response headers, standardized gateway errors, Redis rate limiting and Resilience4j circuit breakers/retries are implemented while leaving the existing MVC runtime separate. The backend remains authoritative for current account state and resource-level permissions. Distributed tracing, Prometheus/Grafana dashboards and additional observability are tracked in separate issues and pull requests.


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


## Upstream resilience: timeouts, retries and circuit breakers

Each API route has its own Resilience4j circuit breaker and an independently configurable response timeout. The gateway also applies a shared connection timeout. Tune `EMS_GATEWAY_CONNECT_TIMEOUT_MS`, `EMS_GATEWAY_DEFAULT_RESPONSE_TIMEOUT` and the route-specific variables such as `EMS_GATEWAY_AUTH_RESPONSE_TIMEOUT_MS`, `EMS_GATEWAY_REPORTS_RESPONSE_TIMEOUT_MS` and `EMS_GATEWAY_PAYSLIPS_RESPONSE_TIMEOUT_MS`. The reports, payroll, payslip and system-log routes have longer defaults because they can perform heavier work.

The gateway retries only `GET` and `HEAD` requests; CORS `OPTIONS` preflight is handled at the edge and is not retried, and only for transient 502, 503, 504 responses or I/O/timeout exceptions. POST, PUT, PATCH and DELETE are never retried. Configure the retry count and bounded exponential backoff with `EMS_GATEWAY_RETRY_MAX_ATTEMPTS`, `EMS_GATEWAY_RETRY_FIRST_BACKOFF` and `EMS_GATEWAY_RETRY_MAX_BACKOFF`.

Each route's circuit breaker uses a count-based sliding window, a configurable failure threshold and a timed open state before half-open probes are allowed. The Resilience4j time limiter bounds reactive execution. Circuit transitions are logged with the breaker name and state transition only; request bodies, tokens and personal data are not logged. Circuit-breaker health indicators are enabled under the existing Actuator health endpoint.

When an upstream circuit is open, a request times out or a configured upstream failure status is returned, the gateway forwards internally to `/gateway/fallback`. The fallback returns the standard JSON error schema with a safe 503 or 504 message and preserves the request correlation ID. It never returns exception messages or stack traces.

The gateway integration tests use an isolated local backend and verify transient GET retries, the configured retry ceiling, no retries for POST, per-route timeout handling, closed/open circuit behavior, fallback status/body and correlation propagation. No live downstream service or production credentials are required.



## Gateway verification strategy

Run the focused gateway suite (includes deterministic mock-backend integration tests and Redis-backed rate-limit tests):

```bash
./mvnw --batch-mode --no-transfer-progress -f gateway/pom.xml verify
```

Run the complete EMS backend suite from the repository root:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Build the standalone gateway container from the repository root:

```bash
docker build --file gateway/Dockerfile --tag ems-api-gateway:local .
```

### Automated coverage boundaries

- **Gateway unit tests:** JWT parsing and issuer/signature/expiry checks; role policy; CORS origin validation; response security headers; correlation-ID validation; privacy-safe log fields; Redis rate-limit policy, trusted proxy identity and fail-closed behavior; resilience fallback mapping; Actuator access controls and metrics registration.
- **Gateway integration tests:** an ephemeral local HTTP backend verifies method/path/query/body preservation, backend status propagation, CORS deduplication, retry limits, no retries for unsafe methods, route timeouts, circuit-breaker fallback, backend authorization denials, oversized-request rejection and management endpoint protection. No hosted Render service is called.
- **Redis integration:** gateway CI starts a disposable Redis 7 service and sets `EMS_GATEWAY_REDIS_INTEGRATION_TESTS=true` to run a real token-bucket integration test. The same test is skipped in ordinary local builds unless that flag is explicitly enabled and a local Redis service is available. Unit tests also use mocks for error-path determinism; no production Redis credentials are needed.
- **Backend authorization:** the root Maven suite runs the existing backend tests, including Spring Security/JWT and resource authorization. Gateway tests verify the original bearer token is forwarded unchanged and backend 401/403 responses are not replaced by gateway success responses. The gateway remains an additional edge check, not a replacement for backend authorization.
- **Sensitive logging:** tests capture gateway completion logs and assert that bearer tokens, cookies, query values, request bodies and employee identifiers are absent.

### Manual deployment smoke-test checklist (deferred — deployment is on hold)

1. Start the EMS backend and Redis, then configure the gateway with the same `JWT_SECRET`, `EMS_BACKEND_URI`, Redis connection settings and a separate `EMS_MANAGEMENT_API_KEY`.
2. Start the gateway container and confirm `/livez`, `/readyz` and `/actuator/health` respond as documented.
3. Authenticate through the gateway's public login route and use the returned bearer token on a protected API request.
4. Verify an invalid/expired token receives 401, an insufficient role receives 403, and a backend resource-level 403 remains a 403 through the gateway.
5. Verify an oversized body receives 413 without reaching the backend (`EMS_GATEWAY_MAX_REQUEST_SIZE`, default `10MB`); exercise login/general/report limits and confirm 429 plus `Retry-After`.
6. Stop Redis in a controlled test environment and confirm rate-limited API traffic fails closed with 503 rather than bypassing the limiter.
7. Confirm a valid `X-Correlation-ID` is returned and propagated, while gateway logs contain no token, cookie, query string or body data.
8. Confirm `/actuator/metrics` requires the management key and that `/actuator/env`, `/actuator/beans`, `/actuator/gateway/routes` and `/actuator/prometheus` are not publicly exposed.
9. Restore Redis/backend availability and verify the circuit breaker closes after its configured recovery window.

The CI workflow runs gateway Maven verification, uses a disposable Redis service, builds the gateway Docker image and runs the root backend tests plus the existing backend production-image build. A manual smoke test against a deployed environment is still required before production traffic is switched to the gateway.


## Docker Compose and independent deployment

The root `docker-compose.yml` includes four services:

- `db`: PostgreSQL with a persistent `pgdata` volume.
- `app`: the existing EMS MVC backend with its existing uploads volume.
- `redis`: Redis 7 with append-only persistence on the internal Compose network; its port is not published to the host.
- `gateway`: the standalone multi-stage gateway image, running as UID/GID 10001 and exposing port 8081.

The gateway uses `EMS_BACKEND_URI=http://app:8080` inside Compose and connects to Redis using the service name `redis`. The backend and gateway share the exact same `JWT_SECRET`; never generate separate signing keys. Set `EMS_MANAGEMENT_API_KEY` to a long random value for protected operational metrics.

Required root `.env` entries for the gateway-enabled Compose stack:

```dotenv
JWT_SECRET=<same-strong-base64-secret-used-by-the-backend>
EMS_MANAGEMENT_API_KEY=<long-random-management-key>
EMS_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

Do not commit the root `.env` file. Keep production secrets in the deployment platform's secret/environment configuration. Redis is private to the Docker network and should not be exposed publicly. The gateway health check calls only the read-only liveness endpoint; readiness additionally checks Redis. This avoids restarting the gateway just because the EMS backend is temporarily unavailable.

Build and start:

```bash
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 gateway
```

Stop without deleting persistent data:

```bash
docker compose down
```

To remove local persistent data as well (destructive):

```bash
docker compose down --volumes
```

### Render deployment topology

Deploy the backend and gateway as separate Render services. Keep PostgreSQL and Redis private; configure the gateway's `EMS_BACKEND_URI` using the backend's private service URL and set the same `JWT_SECRET` on both services. Configure `REDIS_HOST`, `REDIS_PORT`, and optional `REDIS_PASSWORD` using the private Redis connection details. Set `EMS_MANAGEMENT_API_KEY` only on the gateway. Use Render's assigned `PORT`/gateway port setting and the `/livez` health check path. Do not route public traffic to the backend directly once the gateway is ready; retain the backend private URL for gateway-to-backend traffic.

For a safe rollout, deploy the gateway independently, verify health, login, protected API forwarding, CORS, 429 handling and backend 401/403 propagation, then switch the frontend's API base URL to the gateway. Roll back by restoring the previous frontend API base URL; the backend service and database schema are not changed by gateway deployment. Confirm current Render plan limits and private-service availability in the Render dashboard before production deployment.
