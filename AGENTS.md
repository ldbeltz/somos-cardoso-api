## Project

`somos-cardoso-api` is the project responsible for the backend of the website somoscardoso.

- **RestAPI** (`src/main/kotlin/com/api/somoscardoso/SomoscardosoApplication.kt`) — Kotlin with Spring boot Application.

## Project Setup and Management
- Kotlin version: 2.3.21. Don't use newer syntax.
- Add dependencies in `build.gradle.kts` file.
- build the app with `./gradlew clean build`.
- Use `./gradlew clean test` to run tests.
- Use `./gradlew ktfmtFormat` to format code.

CI runs on each push to main — see `.github/workflows/ci-build.yaml`.

## When testing

- Coverage threshold is **100%**.
- Tests mirror the `src/` path (`src/main/kotlin/com/api/somoscardoso/auth/Xcomponent.kt` 
 → `test/main/kotlin/com/api/somoscardoso/auth/XcomponentTests.kt`).
- Collaborators are mocked with `MockK`; 
- tests don't need a running database or broker.
- Test names follow `should <behavior>`.
- Keep existing tests intact, and fix the code to make them pass.

## Database migrations

- Any change to a JPA Entity needs a flyway revision in `resources/db/migration/`.
- Keep migrations backward compatible.

## Workflow

- Don't commit secrets; `.env` is local only.

## Definition of Done

A task is complete when ALL the following pass:
1. `./gradlew ktfmtCheck` reports no errors.
2. `./gradlew ktfmtFormat` leaves the code unchanged.
3. `./gradlew clean test` passes with 100% coverage.

## When Blocked

- If tests fail after 3 attempts: stop and report the failing test with full output.
- If a dependency is missing: check `build.gradle.kts` first, then ask.
- If you encounter merge conflicts: stop and show the conflicting files.
- Never: delete files to resolve errors, force push, or skip tests.

## Ignore

Treat everything in `.gitignore` as off-limits to read or edit. On top of
that, never open:
- Secrets and `.env` files.
- Large data files unrelated to the current task.
- Vendored or generated code.
