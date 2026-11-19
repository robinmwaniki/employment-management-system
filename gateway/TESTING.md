# EMS API Gateway verification

**Status: IN PROGRESS.** P0 gateway end-to-end, security and performance verification was completed in PR #52. Distributed tracing and OTLP integration are being expanded under issue #28. This document describes the repeatable checks and the manual smoke-test checklist to use when deployment is explicitly resumed.

## Local prerequisites

- Java 21.
- Docker Desktop (for local Redis).
- The repository Maven wrapper.
- No Render account, production credentials or hosted backend is required for automated tests.

Start the local Redis dependency:

```bash
docker compose -f gateway/docker-compose.redis.yml up -d
```

## Automated verification

Run the isolated gateway suite:

```bash
./mvnw --batch-mode --no-transfer-progress -f gateway/pom.xml verify
```

Run the existing backend tests independently:

```bash
mvn --batch-mode --no-transfer-progress verify
```

Build the gateway container image without publishing or deploying it:

```bash
docker build --file gateway/Dockerfile --tag ems-api-gateway:local .
```

The gateway CI workflow runs the gateway Maven suite with an ephemeral Redis 7 service and builds the gateway image. The repository Code Test workflow runs backend tests and builds the backend image. Both are required before merge.

## Coverage boundaries

| Area | Automated coverage |
| --- | --- |
| Routing and forwarding | Route registration, path/query/body/method forwarding, correlation header propagation and unmatched routes |
| Backend token contract | Valid bearer token is forwarded unchanged for backend revalidation; missing, malformed and expired tokens are rejected at the gateway |
| Role enforcement | HR/admin access, employee restrictions, admin-only registration and legacy tokens without signed role claims |
| CORS and browser security | Allowed/disallowed origins, edge preflight, response-header deduplication and security headers |
| Request-size protection | Oversized payloads are rejected with HTTP 413 before the backend receives them |
| Rate limiting | Login/registration/general/report policies, identity hashing, spoofed forwarding-header rejection, trusted proxy resolution, quota exhaustion and Redis fail-closed behavior |
| Resilience | Safe GET/HEAD retries, retry exhaustion, no POST retry, route timeouts, open/closed circuit behavior and controlled fallbacks |
| Error handling | Stable JSON schema, status mapping, correlation IDs and suppression of exception/credential details |
| Observability | Privacy-safe correlation logs, bounded Micrometer tags, metrics key protection, liveness/readiness and Actuator allowlist |
| Distributed tracing | W3C traceparent extraction/propagation, child-span creation, trace-disabled mode and sensitive span-attribute scrubbing |

Gateway integration tests use an in-process Reactor Netty downstream service. Redis-dependent behavior is verified with deterministic test doubles and an isolated Redis service in CI. The backend test suite separately verifies backend persistence and authorization rules. These tests do not claim to replace a production-like end-to-end environment.


## Distributed tracing verification

The automated gateway tests verify W3C trace context propagation to the mock downstream service, child-span creation when an incoming `traceparent` is present, new trace creation when it is absent, and removal of raw URLs, query strings, credentials and internal backend addresses from observation attributes. A separate Spring context test verifies OpenTelemetry can be disabled without an OTLP collector.

To inspect traces locally, start Jaeger:

```bash
docker compose -f gateway/docker-compose.tracing.yml up -d
```

Then start the gateway with:

```bash
EMS_TRACING_ENABLED=true
EMS_TRACING_EXPORT_ENABLED=true
EMS_TRACING_SAMPLING_PROBABILITY=1.0
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces
```

Open `http://localhost:16686`. Automated tests keep OTLP export disabled and do not require a collector. For normal development use the default 10% sampling rate; use 100% only for short local investigations.

## Performance smoke test

A small authenticated read-only k6 script is available at `gateway/performance/gateway-smoke.js`. Instructions and conservative local thresholds are in [performance/README.md](performance/README.md).

Use a dedicated test account and a short-lived JWT. Do not run load tests against production or Render while deployment remains paused.

## Manual deployment smoke-test checklist (deferred)

Run this checklist only after deployment has been explicitly approved. Do not deploy or call production services as part of this issue.

- [ ] Confirm `/actuator/health/liveness` and `/livez` return status only and remain healthy while the EMS backend is unavailable.
- [ ] Confirm `/actuator/health/readiness` and `/readyz` reflect the gateway readiness state and Redis availability.
- [ ] Confirm `/actuator/metrics` returns 401 without `X-EMS-Management-Key` and works with the dedicated management key.
- [ ] Confirm valid, expired, malformed and missing JWT behavior and that the original bearer token reaches the backend unchanged.
- [ ] Confirm employee, HR manager and administrator route permissions against a non-production backend.
- [ ] Confirm allowed and disallowed browser origins and that preflight does not require JWT or consume rate-limit quota.
- [ ] Confirm an oversized request is rejected with 413 before backend forwarding.
- [ ] Confirm login and general API quotas return 429 with `Retry-After`, and Redis outages fail closed with 503.
- [ ] Confirm circuit-open and timeout fallbacks return safe JSON with a correlation ID.
- [ ] Confirm logs and metrics contain no JWTs, cookies, request bodies, query strings or employee personal data.
- [ ] Run the k6 smoke profile at low concurrency and record p50/p95 latency, error rate and gateway resource usage.

## Security notes

- Never commit `JWT_SECRET`, `REDIS_PASSWORD`, `EMS_MANAGEMENT_API_KEY` or test-account tokens.
- Keep `EMS_TRUSTED_PROXY_CIDRS` limited to actual trusted reverse proxies.
- Do not expose environment, beans, route-inspection or heap-dump Actuator endpoints.
- The gateway performs edge authentication/authorization checks, but the existing EMS backend remains authoritative and revalidates the bearer token and current account permissions.
