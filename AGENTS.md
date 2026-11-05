# AGENTS.md

Guidance for AI coding agents working on the Employee Management System.

## Project overview

A Spring Boot backend for managing employees and departments. It provides JWT-based authentication and role-based authorization for three roles: **Admin**, **HR Manager** and **Employee**. Data is stored in PostgreSQL and schema changes are handled through database migrations.

The project is under active development. Prefer small, focused changes over large rewrites.

## Tech stack

- Java and Spring Boot (use the versions already declared in `pom.xml`)
- Spring Web (REST), Spring Data JPA, Spring Security with JWT
- PostgreSQL
- Maven
- JUnit 5 and Mockito for tests

Do not upgrade Spring Boot, Java or any dependency as part of an unrelated task. If an upgrade is needed, say so in the pull request instead of doing it silently.

## Build and test commands

Use the Maven wrapper (`./mvnw`) if the repository contains it, otherwise `mvn`.

```bash
mvn --batch-mode verify        # compile, run all tests, package (same as CI)
mvn --batch-mode test          # run unit tests only
mvn spring-boot:run            # start the application locally
```

Always run `mvn verify` before opening a pull request. CI runs the same command on every push and pull request, so a change that fails locally will fail there too.

## Architecture

Follow the existing layered structure and package layout:

```
Controller  ->  Service  ->  Repository  ->  PostgreSQL
(HTTP, validation)  (business rules)  (Spring Data JPA)
```

- **Controllers** handle HTTP concerns only: mapping, request validation and status codes. No business logic.
- **Services** hold business rules and transaction boundaries (`@Transactional` where data changes).
- **Repositories** are Spring Data JPA interfaces. Keep queries simple and parameterized. Never build SQL by string concatenation.
- Use **DTOs** for requests and responses. Never expose JPA entities directly from controllers, and never return password hashes or other sensitive fields.
- Use **constructor injection**. Do not use field injection with `@Autowired`.
- Handle errors through the existing global exception handler. Return consistent error responses with correct HTTP status codes.

## Security rules (important)

This project controls access to sensitive employee data. Treat security as a hard requirement.

- Every new endpoint must have an explicit authorization rule. Endpoints are secured by default. Never open an endpoint to the public unless the task says so.
- Role rules: **Admin** has full access. **HR Manager** manages employee and department records. **Employee** can only access their own data. Do not weaken these rules.
- Passwords must be hashed with the configured `PasswordEncoder` (BCrypt). Never store, log or return plain-text passwords.
- Never log JWTs, passwords, secrets or personal data such as full employee records.
- Never hard-code secrets, keys or credentials. Read them from configuration or environment variables.
- Validate all input (`@Valid` with Jakarta Validation annotations) and do not trust client-supplied IDs when ownership matters.
- Do not disable CSRF, CORS or other security settings to make a test pass. Fix the cause instead.

## Database and migrations

- Schema changes must go through the project's migration tool. Add a new migration file for each change.
- **Never edit a migration that has already been applied or merged.** Create a new one.
- Do not rely on Hibernate auto-DDL (`ddl-auto=update` or `create`) for schema changes.
- Migrations must be safe to run on an existing database with data.

## Code conventions

- Match the formatting and naming style of the surrounding code.
- Write Javadoc for every public class and public method. Describe what it does, its parameters, return value and any exceptions.
- Add short inline comments only where the logic is not obvious, and explain why, not what.
- Keep methods small and single-purpose. Prefer clear names over clever code.
- Use `Optional` and meaningful exceptions instead of returning `null`.
- Avoid adding new dependencies unless the task requires it.

## Testing

- Every change that affects behavior needs tests. A bug fix needs a test that fails without the fix.
- **Service layer:** unit tests with JUnit 5 and Mockito.
- **Controller layer:** `@WebMvcTest` tests that cover both the success path and the security rules (unauthenticated returns 401, wrong role returns 403).
- Name tests clearly, for example `createEmployee_withDuplicateEmail_throwsConflict`.
- Tests must be independent and repeatable. They must not depend on execution order, real network calls or data left by other tests.
- Do not delete, skip or weaken existing tests to make a build pass.

## Pull request rules

- Keep each pull request focused on one task.
- Use clear commit messages that describe the change.
- In the pull request description, explain what changed, why, and how it was tested. Mention any risks, security impact or follow-up work.
- Do not modify `.github/workflows/ci.yml`, build configuration or security configuration unless the task explicitly asks for it.

## Ask before doing

Stop and ask (comment on the issue) instead of guessing when a task would:

- change authentication, JWT handling or role permissions
- change the database schema in a way that could lose or rewrite existing data
- add or upgrade a major dependency
- change a public API contract that other clients may rely on

## Definition of done

- [ ] The code compiles and `mvn verify` passes
- [ ] New or changed behavior has tests
- [ ] New endpoints have explicit authorization rules
- [ ] Public classes and methods have Javadoc
- [ ] No secrets, tokens or personal data in code or logs
- [ ] The pull request description is complete
