# Backend observability and privacy

The backend uses the same Sentry SDK release as the API gateway and Spring Boot's
OpenTelemetry integration. Observability is optional and must not be configured
with credentials committed to the repository.

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SENTRY_DSN` | Backend Sentry project DSN. Leave unset/empty to disable reporting. | empty |
| `EMS_ENVIRONMENT` | Deployment environment attached to telemetry. | `local` |
| `SENTRY_TRACES_SAMPLE_RATE` | Sentry transaction sampling. | `0.0` |
| `EMS_TRACING_SAMPLING_PROBABILITY` | Micrometer/OpenTelemetry trace sampling. | `0.0` in dev; `0.05` in prod |
| `EMS_TRACING_EXPORT_ENABLED` | Enables OTLP export when an exporter endpoint is configured. | `false` |
| `OTEL_SERVICE_NAME` | OpenTelemetry service name. | `ems-backend` |

Configure `SENTRY_DSN` as a backend runtime environment variable in the hosting
platform. It is not the Sentry authentication token and must never be placed in
source control. A Sentry auth token is only for CI/release administration and is
not needed by the running application.

## Request correlation

The backend accepts `X-Correlation-ID` only when it is a canonical UUID. Otherwise
it generates a new UUID. The ID is returned in the response header and is placed
in the logging MDC. The gateway already generates this header, so the same value
can be followed across gateway and backend logs.

W3C `traceparent` is used for trace propagation. When an active Micrometer span is
available its trace ID is preferred; otherwise a valid W3C trace ID is used.
Malformed and all-zero trace IDs are ignored. OpenTelemetry sampling is controlled
by the environment-specific probability.

Request completion logs include only HTTP method, response status, duration,
correlation ID and trace ID. They deliberately omit URI/path, query string,
headers, cookies, request/response bodies and authenticated user identifiers.

## Sentry privacy controls

- `send-default-pii` is disabled and request-body capture is disabled.
- The before-send callback removes request/user objects, breadcrumbs, extras,
  tags, transaction names, server name, contexts, thread data and arbitrary
  metadata.
- Exception type and source frames are retained; exception messages, local
  variables, absolute source paths and register values are removed.
- The REST catch-all handler reports unexpected failures to Sentry but logs only
  the exception class. Handled validation, authentication and authorization
  failures are not manually reported as unexpected server failures.
- Actuator exposes only health and info. Health details are never exposed.

## Sentry verification

1. Create/use a Sentry project for the Spring Boot backend.
2. Add its DSN as the runtime variable `SENTRY_DSN` in the backend hosting service.
3. Deploy and trigger a controlled server-side test exception in a non-production
   environment through an approved test-only route or test fixture.
4. Confirm the event appears in the backend Sentry project and contains the
   exception type/stack frames but no request, user, message, token, cookie,
   employee, leave or payroll data.
5. Remove the DSN to verify the service starts and runs without Sentry.

Do not add a public diagnostic endpoint solely to generate Sentry events.
