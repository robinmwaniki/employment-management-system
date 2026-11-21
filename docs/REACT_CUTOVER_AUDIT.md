# React Cutover Audit — Phase 1

Issue: [#73](https://github.com/robinmwaniki/employment-management-system/issues/73)

## Purpose

Record the verified backend architecture and the safety gates required before removing the Thymeleaf UI. This is an incremental audit, not approval to delete server-rendered pages.

## Verified on `develop`

| Area | Verified state | Cutover implication |
| --- | --- | --- |
| Maven dependencies | `spring-boot-starter-thymeleaf` remains in `pom.xml`. | Keep the dependency until no page controller or template requires it. |
| Security | `SecurityConfig` declares two ordered chains. | The API chain is stateless JWT; the fallback web chain is session-based and form-login enabled. |
| API chain | Matches `/api/**` and OpenAPI docs; disables CSRF, form login, HTTP Basic and logout; uses stateless sessions and the JWT filter. | Preserve this contract and its role/ownership checks. |
| Web chain | Handles all requests not matched by the API chain; enables form login at `/login`, session logout and CSRF. | Removing it requires explicit stateless handling for SPA routes and a deliberate 401/403 policy. |
| Staff page authorization | Staff page patterns include dashboard, employees, departments, attendance, leave, payroll, performance, reports, announcements and notifications; ADMIN/HR_MANAGER only. | Confirm each workflow has a React route and matching protected API before removing the page route. |
| Employee page authorization | `/employee/**` requires authentication. | Confirm employee dashboard, profile, attendance, leave, payroll, notifications and password-change parity, including self-only data scoping. |
| Health endpoints | `/actuator/health`, its subpaths and `/actuator/info` are permitted by the web chain. | Preserve public health probes in the final JWT-only chain. |
| JPA open-in-view | `spring.jpa.open-in-view: true`; configuration comment says templates and mappers still rely on lazy loading. | Do not switch this off until DTO mapping and serialization tests prove service-layer fetch plans are complete. |
| Documentation | README still describes a combined Thymeleaf + REST backend and lists `templates/`. | Update architecture and setup docs only after cutover is complete. |

## Required route and workflow parity audit

The following must be checked against actual controller mappings, React routes, API methods, role rules and integration tests before deletion:

- [ ] Login, token refresh (if present), logout and invalid/expired token behavior.
- [ ] Admin and HR dashboard data.
- [ ] Employee create, edit, list, details, status changes and statutory identifiers.
- [ ] Departments and employee assignments.
- [ ] Attendance capture, correction, employee self-service and exports.
- [ ] Leave requests, approvals, balances and employee ownership.
- [ ] Payroll generation, review, employee payslip access and itemized PDF downloads.
- [ ] Performance records and review workflows.
- [ ] Announcements and staff/employee notifications, including read state.
- [ ] Employee profile, photo upload, password change and account recovery.
- [ ] User administration, role assignment and admin password reset.
- [ ] Company profile and statutory remittance reports/CSV exports.
- [ ] System logs, report downloads, upload/download authorization and SPA deep links.

## Non-negotiable security gates

1. Keep all existing REST URLs consumed by the React client stable.
2. Every employee self-service query must derive the employee identity from the authenticated principal, never from a client-supplied employee ID alone.
3. Preserve ADMIN versus HR_MANAGER permissions and employee-only restrictions.
4. Keep JWT authentication stateless; do not introduce cookie/session authentication as a replacement.
5. Keep CSRF disabled only for the bearer-token API contract. If the final chain accepts browser cookies, revisit CSRF rather than disabling it globally.
6. Keep health probes public and operational endpoints restricted.
7. Do not alter Flyway migrations or production data for this UI-only cutover.
8. Do not remove templates, page controllers or Thymeleaf until route parity and negative authorization tests pass.

## Phase 1 conclusion

The repository confirms that the API and Thymeleaf UI currently use separate security chains. The API chain is already stateless JWT, but the fallback chain still provides session-based form login and page authorization. The JPA open-in-view setting also explicitly retains template-related lazy-loading behavior.

**No production code or database schema has been removed or changed in this phase.** The next phase is a complete controller/template inventory matched to the React route and API client, followed by targeted integration tests. Only then can the web chain and Thymeleaf artifacts be retired safely.
