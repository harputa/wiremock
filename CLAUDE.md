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

## Architecture

Request flow: `GET :9090/<path>` + selector header → WireMock stub → `ReferenceResponseTransformer` → `MockDataRepository` (`FileMockDataRepository`) → `mock-data/<response-folder>/<ref>.json` (+ optional `<ref>.properties`).

How the pieces connect:
- `MockProperties` (`mock.*`) holds a map of **domains**. Each domain has `path`, `selector-header` and `response-folder`, and they are defined in `src/main/resources/application.yml`. Adding an endpoint means adding a domain here; no Java code is needed. Config is read once at startup.
- `WireMockConfiguration` creates the `WireMockServer` bean and registers the transformer as an extension. The transformer is **not global** (`applyGlobally=false`).
- `StubRegistry` (an `ApplicationRunner`) calls `resetAll()` and then registers one `GET urlPathEqualTo(path)` stub per domain, passing the domain name as the transformer parameter `"domain"`. `/admin/reload` reruns the same logic, which re-creates stubs from the *already loaded* config and clears the request journal. It does not re-read `application.yml`.
- `ReferenceResponseTransformer` resolves the domain, reads the header, and maps outcomes to JSON errors: missing header → 400 `MISSING_REFERENCE`, no fixture → 404 `FIXTURE_NOT_FOUND`, bad metadata or IO error (`MockFixtureException`) → 500 `FIXTURE_READ_ERROR`, unknown domain → 500 `MOCK_CONFIGURATION_ERROR`.
- `FileMockDataRepository` reads files from disk **on every request**, so you can add or edit fixtures without a restart or reload. Both the reference and the folder must match `[A-Za-z0-9_-]{1,100}`, and resolved paths must stay under the base directory (path-traversal guard). Anything invalid becomes "not found" (404), not an error.

Fixture metadata (`<ref>.properties`): `status` (100–599, default 200), `delay-ms` (0–60000, default 0), `content-type` (default `application/json`). Values out of range → 500. The JSON body is returned verbatim.

## Tests

`MockServerIntegrationTest` is a `@SpringBootTest` that sets `mock.wiremock-port=0` (random port) and uses the real `./mock-data` fixtures. It sends real HTTP requests to `wireMock.port()`. The assertions check fixture contents (e.g. product `2000` contains "Laptop Pro 14", `2999` returns 503 via its `.properties`), so changing those fixtures will break tests.

## Security constraints

This is for dev/test only: neither the admin API nor WireMock `__admin` has authentication. Keep the admin bind on `127.0.0.1` by default. Compose binds `0.0.0.0` inside the container but publishes 8080 only on host `127.0.0.1`. Keep the filename/path validation in `FileMockDataRepository` intact.
