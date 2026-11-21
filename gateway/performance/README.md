# Gateway performance smoke test

This k6 script provides a small, repeatable authenticated read-path smoke test. It is not a capacity benchmark or a production SLA. Run it against a local gateway and a non-production EMS backend while deployment remains paused.

## Prerequisites

- Gateway running at `http://localhost:8081`.
- Local Redis running for rate limiting and readiness.
- Short-lived JWTs for distinct synthetic employee accounts; the capacity profile requires at least one distinct account/token per target virtual user.
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

## Progressive employee self-service capacity profile

`gateway-capacity.js` adds a read-only workload across the authenticated employee profile, leave, attendance, payroll-history and unread-notification endpoints. It uses a synthetic employee account and does not create or modify records.

Start with a small local/staging baseline and increase only after reviewing gateway, backend, Redis and PostgreSQL saturation:

| Stage | Target virtual users | Minimum observation |
| --- | ---: | --- |
| Baseline | 25 | 5-minute steady state |
| Step 1 | 100 | 10-minute steady state |
| Step 2 | 1,000 | 15-minute steady state |
| Step 3 | 10,000 | Distributed runner; 15-minute steady state |
| Step 4 | 50,000+ | Distributed runner and production-like infrastructure |

These are proposed progression checkpoints, not measured capacity or promised performance. A million-user target must be reached with distributed generators and sufficient isolated staging infrastructure; do not run one million VUs from a developer workstation.

**PowerShell (local or isolated staging only)**

```powershell
$env:EMS_TOKENS = "<jwt-account-1>,<jwt-account-2>,<jwt-account-3>,..."
$env:LOAD_TEST_ENV = "staging" # or local
$env:LOAD_TEST_ACK = "I_CONFIRM_NON_PRODUCTION"
k6 run --env BASE_URL=https://<isolated-staging-gateway> --env TARGET_VUS=100 --env RAMP_DURATION=2m --env HOLD_DURATION=10m --env DRAIN_DURATION=2m gateway/performance/gateway-capacity.js
```

For a local gateway use `BASE_URL=http://localhost:8081` and `LOAD_TEST_ENV=local`. The script blocks Render hosts and production-like hostnames. A single k6 generator is capped at 1,000 VUs; larger runs require an explicitly configured distributed runner and `DISTRIBUTED_LOAD_TEST=true`. That flag is not a substitute for staging isolation or capacity planning.

The gateway applies its general API rate limit per verified JWT subject. Reusing one token across all virtual users would put every VU in the same 20-requests/second bucket and produce an artificial 429 bottleneck. Supply a separate synthetic employee identity/token for each VU; distributed workers must use disjoint token sets. Do not disable rate limiting to make a benchmark pass.

The initial thresholds (less than 1% failed requests, p95 below 1 second and p99 below 2 seconds) are provisional guardrails. Agree on service-level objectives before treating a run as acceptance evidence. Record achieved request rate, p50/p95/p99, errors, gateway/backend CPU and memory, JVM GC, database pool wait/active connections, slow queries/locks, Redis latency and resource saturation for every run.

**Stop the run** if error rate exceeds 2% for 60 seconds, p95 exceeds twice the agreed target for 2 minutes, database connections remain exhausted, Redis becomes unhealthy, or any service approaches its configured memory/CPU limit. Never bypass rate limits, authorization, or payroll correctness to improve throughput.
