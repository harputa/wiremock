# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4.1.2 / Java 21 service that embeds WireMock 3.13.2 (`wiremock-standalone`, used on purpose to keep WireMock's transitive deps away from Spring Boot/Jackson — don't swap to `wiremock` or the 4.x beta). It serves JSON fixtures picked by a request header. Two ports:
- **9090**: WireMock mock endpoints (plus WireMock's own `/__admin`)
- **8080**: Spring MVC admin API (`/admin/status`, `POST /admin/reload`) and actuator `health,info`. By default it binds to `127.0.0.1`.

## Commands

Run every command from the project root: `mock.data-directory` defaults to the relative path `./mock-data`, and the tests depend on that too.

```bash
mvn clean test                                   # all integration tests
mvn test -Dtest=MockServerIntegrationTest#productSelection   # single test
mvn spring-boot:run                              # run locally
mvn -DskipTests package                          # build jar
docker compose up --build -d                     # run in Docker (mock-data bind-mounted read-only)
```

Environment overrides: `ADMIN_PORT`, `ADMIN_BIND_ADDRESS`, `WIREMOCK_PORT`, `MOCK_DATA_DIR`.

On this machine the jenv `java.exe` shim fails under PowerShell. Run Maven with an explicit JDK 21 instead: `$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.9.10-hotspot"; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"`. Git Bash commands may also hang here, so prefer PowerShell.

## Architecture

Request flow: `GET :9090/<folder>` + `X-<Folder>-Ref` header → WireMock stub → `ReferenceResponseTransformer` → `MockDataRepository` (`FileMockDataRepository`) → `mock-data/<folder>/<ref>.json` (+ optional `<ref>.properties`).

How the pieces connect:
- Endpoints are **discovered from the data directory**, not configured. `FileMockDataRepository.responseFolders()` walks the directory tree (max depth 10) and returns `/`-separated relative paths such as `products` and `products/electronics`. A path is kept only if every segment matches the safe-name pattern. `MockDomain.forFolder` derives the URL `/<full/folder/path>` and a header named after the **top-level** folder only, `X-<Folder>-Ref` (first letter capitalized; HTTP header lookup ignores case). Each endpoint reads fixtures only from its own folder, not from its parent or child folders. `MockProperties` (`mock.*`) holds only `wiremock-port` and `data-directory`.
- `WireMockConfiguration` creates the `WireMockServer` bean and registers the transformer as an extension. The transformer is **not global** (`applyGlobally=false`).
- `StubRegistry` (an `ApplicationRunner`) rescans the folders, calls `resetAll()`, and registers one `GET urlPathEqualTo("/<folder>")` stub per folder, passing the folder name as the transformer parameter `"folder"`. `/admin/reload` reruns the same logic, so new folders are picked up without a restart. It also clears the request journal.
- `ReferenceResponseTransformer` rebuilds the `MockDomain` from the `"folder"` parameter, reads the header, and maps outcomes to JSON errors: missing header → 400 `MISSING_REFERENCE`, no fixture → 404 `FIXTURE_NOT_FOUND`, bad metadata or IO error (`MockFixtureException`) → 500 `FIXTURE_READ_ERROR`, missing folder parameter → 500 `MOCK_CONFIGURATION_ERROR`.
- `FileMockDataRepository` reads files from disk **on every request**, so you can add or edit fixtures without a restart or reload. Both the reference and the folder must match `[A-Za-z0-9_-]{1,100}`, and resolved paths must stay under the base directory (path-traversal guard). Anything invalid becomes "not found" (404), not an error.

Fixture metadata (`<ref>.properties`): `status` (100–599, default 200), `delay-ms` (0–60000, default 0), `content-type` (default `application/json`). Values out of range → 500. The JSON body is returned verbatim.

## Tests

`MockServerIntegrationTest` is a `@SpringBootTest` that sets `mock.wiremock-port=0` (random port) and uses the real `./mock-data` fixtures. It sends real HTTP requests to `wireMock.port()`. The assertions check fixture contents (e.g. product `2000` contains "Laptop Pro 14", `2999` returns 503 via its `.properties`), so changing those fixtures will break tests.

## Playwright e2e suite (`e2e/`)

API-only Playwright tests in TypeScript. They don't use a browser. Run them from `e2e/` with `npm install`, then `npm test`, or for a single file, `npx playwright test tests/wiremock/routing.spec.ts`. `npm run typecheck` runs `tsc`. The config auto-starts `mvn spring-boot:run` from the repo root unless `:8080/actuator/health` is already up, so `JAVA_HOME` must point at JDK 21 (see above).
- Tests are **generated from `mock-data/`**. `support/mock-data.ts` mirrors the server's discovery rules: the safe-name pattern, max depth 10, the `.properties` metadata, and the top-level-folder header. If you change those rules in `FileMockDataRepository` or `MockDomain`, change them there too.
- Project order is `setup` → `wiremock` (parallel) → `admin` (serial). Reload calls `resetAll()` and then re-adds the stubs, so mock requests made during a reload get 404. Keep reload tests in the `admin` project.

## Commit hook

Commits run Gitleaks on the staged changes through `.githooks/pre-commit`. It is enabled by `scripts/install-gitleaks.sh` (Linux, macOS, Git Bash) or `scripts/install-gitleaks.ps1` (Windows). Both install the pinned version into `.tools/gitleaks/` and set local `core.hooksPath`, so keep `VERSION` in sync between the two scripts. A blocked commit means a secret was found. Fix the secret; don't bypass the hook with `--no-verify`. To run the hook manually: `& "C:\Program Files\Git\bin\sh.exe" .githooks/pre-commit`.

## Security constraints

This is for dev/test only: neither the admin API nor WireMock `__admin` has authentication. Keep the admin bind on `127.0.0.1` by default. Compose binds `0.0.0.0` inside the container but publishes 8080 only on host `127.0.0.1`. Keep the filename/path validation in `FileMockDataRepository` intact.
