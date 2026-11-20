# Backend performance and concurrency assessment

## Safe cache candidates

No employee, leave, attendance, or payroll response is cached by this change. Those records are user- and role-scoped, and caching them before cache-key isolation and mutation invalidation are measured would introduce avoidable data-leakage and staleness risk.

| Candidate | Expected benefit | Proposed policy |
|---|---|---|
| Department reference list | Reduce repeated reads of relatively stable lookup data | Consider a short 5-minute TTL only after query metrics confirm repeated reads; evict on department create, update, and delete |
| Static role/permission metadata | Avoid repeated reads of immutable application metadata | Prefer application-level constants/security configuration; no distributed cache needed |
| Dashboard aggregates | Reduce expensive repeated aggregate queries | Do not cache yet; first measure query latency and define a user/role-aware cache key and mutation invalidation rules |

## Concurrency controls delivered in this increment

- Leave requests carry a JPA optimistic-lock version. Concurrent decisions against the same request cannot silently overwrite a newer status.
- Attendance rows are unique by employee and attendance date. Concurrent check-ins cannot create duplicate daily records.
- Stale writes and database uniqueness violations return HTTP 409 with a generic message that does not expose SQL, entity identifiers, or personal data.
- The migration checks for existing duplicate attendance rows before creating the unique index and fails with an actionable message instead of deleting or merging records.

## Follow-up measurement

Before introducing caching, capture dashboard/report query counts and p50/p95 latency under representative load. Add caching only when the measured benefit justifies the invalidation and authorization complexity. Payroll and personal employee data remain uncached.
