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

## React route inventory verified on frontend branch `Develop`

The React router in `ems_frontend/src/App.tsx` contains these protected routes:

| Workspace | Routes present |
| --- | --- |
| Staff | `/dashboard`, `/employees`, `/departments`, `/leave`, `/payroll`, `/announcements`, `/notifications`, `/attendance`, `/performance`, `/reports`, `/company-profile`, `/users`, `/system-logs` |
| Employee | `/employee/dashboard`, `/employee/profile`, `/employee/leave`, `/employee/attendance`, `/employee/payroll`, `/employee/notifications`, `/employee/change-password` |
| Public | `/login`; `/` redirects to login; unknown routes redirect to login |

Staff routes are wrapped by a guard allowing ADMIN and HR_MANAGER. Employee routes allow EMPLOYEE, ADMIN and HR_MANAGER. These client-side guards are not a security boundary; backend authorization remains mandatory.

## Backend REST contracts verified

| Contract | Verified behavior |
| --- | --- |
| `POST /api/auth/login` | Public JWT login endpoint. |
| `POST /api/auth/register` | ADMIN-only registration. |
| `GET /api/auth/me` | Returns authenticated account and linked employee summary. |
| `POST /api/auth/change-password` | Changes the authenticated account password. |
| `/api/me/**` | Self-service controller derives employee ID from the authenticated username; profile, photo, leave, attendance, payroll, payslip PDF and notification actions are present. |
| `/api/reports/**` | Staff report endpoints for employees, payroll, leave, attendance, performance and statutory remittances. |
| `/api/company-profile` | ADMIN-only company profile read/update. |

The self-service controller explicitly resolves the employee linked to the authenticated principal. Leave creation overwrites the request employee ID with that resolved ID; payslip download verifies payroll ownership before generating the PDF. This is the required pattern to preserve.

## Required route and workflow parity audit

The following must still be checked against all page-controller mappings, all staff API controllers, frontend API modules, role rules and automated integration tests before deletion:

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
- [ ] Verify React API base URL and CORS configuration point to the current production backend/gateway intentionally.

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

The React frontend already has distinct staff and employee route groups, and the backend exposes a JWT authentication contract plus self-scoped employee endpoints. However, route presence alone does not prove workflow parity: staff API coverage, page-controller/template inventory, frontend API wiring and negative authorization tests still need verification.

**No production code or database schema has been removed or changed in this phase.** The next phase is a complete controller/template inventory matched to frontend API modules, followed by targeted integration tests. Only then can the web chain and Thymeleaf artifacts be retired safely.


## Cross-repository deployment integration finding

The frontend Axios client uses `import.meta.env.VITE_API_URL` and falls back to an empty base URL. The frontend `Develop` branch CI workflow currently builds with `VITE_API_URL=https://employment-management-system-49la.onrender.com`, while the active backend Render service URL verified for this release is `https://ems-backend-vo1z.onrender.com`.

The Render static-site service metadata confirms the frontend build command and publish directory, but does not expose its configured environment-variable values. Therefore this audit cannot infer whether the live Render frontend was built with the current backend URL. Backend request logs show successful traffic after the deployment, but the privacy-safe request logs omit request paths, so they do not prove that every current React API module is reaching the intended backend.

**Required release check:** verify the Render static-site environment has `VITE_API_URL=https://ems-backend-vo1z.onrender.com`, trigger a frontend rebuild only if it is wrong, then perform authenticated smoke tests for login, employee self-service, company profile and statutory remittance report/CSV. Do not change production environment variables without confirming the existing configuration first.


## Detailed API-to-client parity findings

The following API module paths in the React `Develop` branch were compared with controller mappings on backend `develop`:

| React API module | Backend controller / route | Finding |
| --- | --- | --- |
| `auth.ts` | `AuthController`: `/api/auth/login`, `/api/auth/me`, `/api/auth/change-password` | Route paths align. Registration is ADMIN-only and is not part of the public login screen. |
| `me.ts` | `MeController`: `/api/me/**` | Self-service routes align; identity is resolved from JWT principal. |
| `employees.ts` | `EmployeeController`: `/api/employees/**` | CRUD, search and photo routes exist; verify every mutation is protected by staff role and service-level constraints. |
| `departments.ts` | `DepartmentController`: `/api/departments/**` | Paths align. Read access includes employees; create/update/delete have narrower method-level role rules. |
| `attendance.ts` | `AttendanceController`: `/api/attendance/**` | Paths align. Staff check-out includes employee ID in the URL; employee self-service correctly uses `/api/me/attendance/check-out`. |
| `leave.ts` | `LeaveController`: `/api/leaves/**` | List/approve/reject paths align. Employee apply/cancel uses `/api/me/leave`. |
| `payroll.ts` | `PayrollController` and `PdfController`: `/api/payroll/**`, `/api/payslips/{id}` | Staff paths align. Employee payroll and payslip downloads use the ownership-scoped `/api/me/**` routes. |
| `performance.ts` | `PerformanceController`: `/api/performance/**` | Paths align; employee-ID query endpoint is a staff API and must remain inaccessible to employee tokens. |
| `announcements.ts` | `AnnouncementController`: `/api/announcements/**` | Paths align; mutation methods are ADMIN/HR_MANAGER-only. |
| `notifications.ts` | `NotificationApiController`: `/api/notifications/**` | Paths align and are explicitly ADMIN/HR_MANAGER-only. Employee notifications use `/api/me/notifications/**`. |
| `dashboard.ts` | `DashboardRestController`: `/api/dashboard` | Base path aligns; confirm response fields against dashboard widgets in integration tests. |
| `reports.ts` | `ReportController`: `/api/reports/**` | Report paths align, including statutory remittance JSON and CSV. |
| `companyProfile.ts` | `CompanyProfileController`: `/api/company-profile` | Paths align; controller is ADMIN-only. |
| `systemLogs.ts` | `SystemLogController`: `/api/system-logs` | Path aligns; controller is ADMIN-only. |
| `users.ts` | `UserController`: `/api/users/**` | Paths align; controller is ADMIN-only. |

### Security-sensitive observations

- The API security chain authenticates `/api/me/**` and `/api/auth/me` for any signed-in role. The controller then resolves the employee record from the authenticated username.
- All other `/api/**` routes default to ADMIN/HR_MANAGER. Department routes are authenticated at the filter-chain level and narrowed by `@PreAuthorize` on individual methods.
- The frontend has both staff and employee attendance/payroll/notification modules. The employee workspace should continue to call only `/api/me/**` for its own records.
- The staff payroll and performance APIs accept employee IDs. They are currently inside the staff-only API boundary; do not widen them to employee tokens during cutover.
- The old Thymeleaf page controllers and REST controllers coexist. The presence of a matching REST endpoint does not prove that validation, response semantics, pagination, file handling, or authorization are identical.

### Confirmed production integration configuration drift

The frontend `.github/workflows/ci.yml` currently sets `VITE_API_URL=https://employment-management-system-49la.onrender.com`, while the active backend Render service metadata reports `https://ems-backend-vo1z.onrender.com`.

The Render static-site metadata confirms its build command and branch but does not expose the configured environment-variable values. Therefore:
- Update the CI build URL to the current backend hostname.
- Separately verify the Render static site's `VITE_API_URL` environment value before changing it.
- Rebuild the static site after correcting the Render value, because Vite embeds `VITE_*` values into the browser bundle at build time.
- Smoke-test login, staff dashboard, employee self-service, company profile and statutory remittance CSV using real role-separated test accounts.

No live Render environment variable has been changed as part of this audit.
