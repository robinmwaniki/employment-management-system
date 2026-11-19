# Gateway performance smoke test

This k6 script provides a small, repeatable authenticated read-path smoke test. It is not a capacity benchmark or a production SLA. Run it against a local gateway and a non-production EMS backend while deployment remains paused.

## Prerequisites

- Gateway running at `http://localhost:8081`.
- Local Redis running for rate limiting and readiness.
- A short-lived JWT for a dedicated test account with permission to list employees.
- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) installed.

## Run

**Linux / macOS**

```bash
export EMS_TOKEN="<short-lived-test-jwt>"
k6 run --env BASE_URL=http://localhost:8081 --env VUS=5 --env DURATION=30s gateway/performance/gateway-smoke.js
```

**PowerShell**

```powershell
$env:EMS_TOKEN = "<short-lived-test-jwt>"
k6 run --env BASE_URL=http://localhost:8081 --env VUS=5 --env DURATION=30s gateway/performance/gateway-smoke.js
```

The default smoke profile uses five virtual users for 30 seconds and checks:

- Less than 1% failed requests.
- p95 response time below one second.

These are conservative smoke-test guardrails, not production capacity targets. Tune the thresholds only after collecting a baseline on the target environment.

## Safety

- Use a dedicated non-production account and a short-lived token.
- Never commit the token, add it to command history, or include it in CI logs.
- Do not run load tests against production or Render while deployment/performance testing is not explicitly approved.
- The script sends only authenticated `GET /api/employees` requests; it does not create, update or delete employee records.
- The gateway's metrics tags remain bounded and do not include the token, user identity or raw URL path.
