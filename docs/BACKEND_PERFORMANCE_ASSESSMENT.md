# Backend performance and concurrency assessment

## Safe cache candidates

No employee, leave, attendance, or payroll response is cached by this change. Those records are user- and role-scoped, and caching them before cache-key isolation and mutation invalidation are measured would introduce avoidable data-leakage and staleness risk.

| Candidate | Expected benefit | Proposed policy |
|---|---|---|
| Department reference list | Reduce repeated reads of relatively stable lookup data | Consider a short 5-minute TTL only after query metrics confirm repeated reads; evict on department create, update, and delete |
| Static role/permission metadata | Avoid repeated reads of immutable application metadata | Prefer application-level constants/security configuration; no distributed cache needed |
| Dashboard aggregates | Reduce expensive repeated aggregate queries | Do not cache yet; first measure query latency and define a user/role-aware cache key and mutation invalidation rules |

## Concurrency controls delivered

- Leave requests carry a JPA optimistic-lock version. Concurrent decisions against the same request cannot silently overwrite a newer status.
- Leave application takes a short pessimistic lock on the employee row before checking overlapping pending/approved leave. Two concurrent requests for the same employee therefore cannot both pass the overlap check.
- Attendance rows are unique by employee and attendance date. Concurrent check-ins cannot create duplicate daily records; attendance also has an optimistic-lock version to prevent competing check-outs from overwriting one another.
- Employees and payroll rows carry optimistic-lock versions so stale HR edits cannot silently overwrite newer values.
- Payroll is unique by employee and payroll month. The database constraint is the final idempotency guard when two generation requests race.
- Guarded mutations are flushed before audit/email side effects where a stale version or uniqueness conflict must be detected first.
- Stale writes and database uniqueness violations return HTTP 409 with a generic message that does not expose SQL, entity identifiers, or personal data.
- Migrations fail with actionable messages when existing attendance or payroll duplicates would prevent safe uniqueness enforcement; they never delete or merge records.

## Follow-up measurement

Before introducing caching, capture dashboard/report query counts and p50/p95 latency under representative load. Add caching only when the measured benefit justifies the invalidation and authorization complexity. Payroll and personal employee data remain uncached.
