# Employee Management System — Project Status

**Overall status: IN PROGRESS**  
**Last updated: 19 November 2026**  
**Integration branch: `develop`**

## Project overview

The Employee Management System combines a Spring MVC HR backend, a separate React frontend and an independently deployable reactive Spring Cloud Gateway under `gateway/`. The backend remains the source of truth for current account state, role authorization and employee/resource-level permissions.

## Delivered and merged into develop

| Issue | Delivery | Result |
| --- | --- | --- |
| #18 | Standalone Java 21 / Spring Boot 4.1.1 reactive gateway foundation | Completed |
| #19 | Explicit centralized API routing to the existing backend | Completed |
| #26 | Correlation ID validation/propagation and privacy-safe structured completion logs | Completed |
| #20 | Existing EMS JWT signature, issuer, expiry and token-structure validation at the gateway | Completed |
| #21 | Signed role claims and gateway route authorization matching the existing backend policy | Completed |

### Security contract

- Backend tokens use JJWT 0.12.7, a Base64-encoded HMAC signing key, issuer `employee-management-system`, username/email subject, `iat`, `exp` and a signed `roles` authority list.
- The gateway requires the same `JWT_SECRET` environment value as the backend; it has no fallback secret.
- Gateway checks are defense in depth. The backend continues to validate tokens, reload current user/account state and apply its own authorization on every request.
- Existing tokens issued before the signed `roles` claim was added are denied by protected gateway routes until the user signs in again. The normal token lifetime is one hour.
- Only POST `/api/auth/login` and CORS OPTIONS preflight requests are unauthenticated at the gateway.
- Current route policy: registration requires `ROLE_ADMIN`; departments allow any known EMS role; other routed API paths require `ROLE_ADMIN` or `ROLE_HR_MANAGER`.

## Next implementation milestones

| Issue | Priority | Scope | Status |
| --- | --- | --- | --- |
| #22 | P1 | Gateway CORS policy and secure response headers | Completed — PR #44 |
| #23 | P1 | Consistent gateway error responses and global exception handling | Completed — PR #41 |
| #24 | P1 | Redis-backed gateway rate limiting | Completed — PR #47 |
| #25 | P1 | Resilience4j circuit breakers, timeouts and controlled retries | Completed — PR #49 |
| #27 | P1 | Actuator health probes and Micrometer gateway metrics | In progress — PR pending CI |
| #27 | P1 | Actuator health and Micrometer metrics | Open |
| #28 | P2 | Distributed tracing with Micrometer and OpenTelemetry | Open |
| #29 | P2 | Prometheus metrics and Grafana dashboard | Open |
| #30 | P2 | Sentry error tracking | Open |
| #31 | P2 | Centralized gateway OpenAPI documentation | Open |
| #32 | P1 | Gateway containerization and local/Render deployment documentation | Open |
| #33 | P0 | Gateway end-to-end, security and performance verification | Open |

Deployment work is not being started as part of the current feature sequence. Issue #32 contains the later deployment/containerization scope; it will be handled only after the gateway's core security, resilience and verification work is complete.

## Latest change log

### 19 November 2026
- Merged PR #34: standalone reactive gateway foundation.
- Merged PR #35: explicit API route table and request-forwarding tests.
- Merged PR #36: correlation ID validation/propagation and privacy-safe structured logs, including tests that exclude query values, authorization credentials, cookies and payloads.
- Merged PR #37: shared EMS JWT signature/issuer/expiry validation, authenticated API forwarding and CORS preflight handling.
- Merged PR #38: signed role claims and gateway route policy matching the existing backend SecurityConfig.
- Merged PR #44: exact-origin gateway CORS, edge-handled preflight, duplicate-header prevention and browser security headers.
- Merged PR #41: standardized privacy-safe JSON gateway errors, stable status mapping and correlation-aware responses.
- Merged PR #47: distributed Redis token-bucket rate limiting, hashed identity keys, trusted-proxy validation, separate API policies and fail-closed Redis outage handling.
- Merged PR #49: per-route Resilience4j circuit breakers, environment-backed response timeouts, safe GET/HEAD retries, controlled fallbacks and circuit-state transition logging. Gateway tests, backend tests and Docker image build all passed.
- Issue #27 is now implementing explicit liveness/readiness probes, protected Micrometer metrics, bounded route/method/outcome tags and operational endpoint tests.

## Delivery rules

1. Each feature has its own issue, branch and pull request targeting `develop`.
2. PRs include design/security rationale, tests, documentation and code comments where decisions are non-obvious.
3. Do not merge until gateway CI, backend tests and Docker checks are green.
4. Never log tokens, credentials, cookies, employee personal data or request/response bodies.
5. Do not weaken backend authorization; gateway checks are additional protection.
6. Update this file, the root README and gateway README after every meaningful delivery.
7. Keep the project labelled IN PROGRESS until all required acceptance criteria and end-to-end verification are complete.
