# Header-driven WireMock Service

A standalone Spring Boot 4.1.2 / Java 21 mock REST application with embedded WireMock 3.13.2, generic header-driven fixture resolution, endpoints discovered automatically from the `mock-data` folder tree (including subfolders), an administration API, a Gitleaks secret-scanning pre-commit hook, and Docker Compose.

## Prerequisites

- Java 21 and Maven 3.9+, **or** Docker with the Docker Compose plugin.
- Run commands from the **project root** (fixture paths are relative to working directory).
- After cloning, enable the secret-scanning commit hook once with `bash scripts/install-gitleaks.sh` (Linux, macOS, Git Bash) or `powershell -ExecutionPolicy Bypass -File scripts/install-gitleaks.ps1` (Windows). See [Secret scanning on commit](#secret-scanning-on-commit).

## Run

```bash
mvn clean test
mvn spring-boot:run
```

Or:

```bash
docker compose up --build -d
docker compose logs -f mock-service
```

## Try it

```bash
curl -i -H 'X-Products-Ref: 2000' http://localhost:9090/products
curl -i -H 'X-Products-Ref: 2001' http://localhost:9090/products
curl -i -H 'X-Products-Ref: 2999' http://localhost:9090/products
curl -i -H 'X-Customers-Ref: 2305' http://localhost:9090/customers
curl -i -H 'X-Orders-Ref: 7001' http://localhost:9090/orders
curl -i -H 'X-Categories-Ref: 3001' http://localhost:9090/categories
curl -i -H 'X-Email-Ref: 3011' http://localhost:9090/email
# Subfolders: URL includes the subfolder, header keeps the top-level folder name
curl -i -H 'X-Products-Ref: 2100' http://localhost:9090/products/electronics
curl -i -H 'X-Categories-Ref: 3001' http://localhost:9090/categories/temp
# Error cases: missing header (400), unknown reference (404)
curl -i http://localhost:9090/products
curl -i -H 'X-Products-Ref: 9999' http://localhost:9090/products
curl -s http://localhost:8080/admin/status
curl -X POST http://localhost:8080/admin/reload
```

PowerShell:

```powershell
Invoke-RestMethod -Uri http://localhost:9090/products -Headers @{ 'X-Products-Ref' = '2000' }
```

## Add or change mock data

Put a `<reference>.json` file under `mock-data/<folder>/` (or a subfolder such as `mock-data/<folder>/<sub>/`).
For example, `mock-data/products/2010.json` immediately enables `X-Products-Ref: 2010`; no Java code, restart, or reload required. JSON files are returned verbatim.

Optional `<reference>.properties` next to the JSON controls the HTTP response:

```properties
status=200
delay-ms=500
content-type=application/json
```

The valid delay range is 0–60000 ms. If no fixture exists, 404 is returned; if the selector header is absent, 400 is returned. Invalid or unreadable fixture metadata results in 500. Simulated failure: product reference `2999` returns 503.

## Configure new endpoints

Endpoints are discovered from the data directory; there is no per-endpoint configuration. Every folder under `mock-data/`, including nested subfolders (up to 10 levels deep), becomes one endpoint. The URL mirrors the folder path, and the selector header is always named after the **top-level** folder:

| Folder | URL | Selector header |
|---|---|---|
| `mock-data/products/` | `GET /products` | `X-Products-Ref` |
| `mock-data/products/electronics/` | `GET /products/electronics` | `X-Products-Ref` |
| `mock-data/customers/` | `GET /customers` | `X-Customers-Ref` |
| `mock-data/<folder>/<sub>/` | `GET /<folder>/<sub>` | `X-<Folder>-Ref` |

Each endpoint serves only the fixtures directly inside its own folder (`/products` does not see `products/electronics/2100.json`).

To add an endpoint, create a folder or subfolder (letters, digits, underscore, hyphen only; others and everything below them are ignored), put fixtures in it, then call `POST /admin/reload` (or restart). Reload rescans the folders, recreates the WireMock stubs and clears the request journal. Header names are case-insensitive. Fixture file changes inside an existing folder require **no** reload.

Only the ports and data directory are configured in `src/main/resources/application.yml` (or via `WIREMOCK_PORT`, `MOCK_DATA_DIR`, `ADMIN_PORT`, `ADMIN_BIND_ADDRESS`).

## How it works

```
Startup / POST /admin/reload:
    StubRegistry scans ./mock-data/** (folders and subfolders)
    -> one WireMock stub per folder: GET /<folder>[/<sub>...], header X-<Folder>-Ref

Client :9090/products + X-Products-Ref:2000
    -> WireMock stub /products
    -> ReferenceResponseTransformer (per-stub, non-global)
    -> MockDataRepository -> FileMockDataRepository
    -> ./mock-data/products/2000.json (+ optional 2000.properties)
    -> HTTP 200 and JSON

Admin :8080/admin/{status,reload} -> StubRegistry -> WireMock
```

## Security and deployment

- **Development/test use only**. No authentication is implemented for the admin API or WireMock's built-in `__admin` API. Do not expose either to untrusted networks.
- By default the Spring administration server listens on `127.0.0.1:8080`. Docker binds the published admin port to `127.0.0.1` on the host. Inside Compose it binds to `0.0.0.0` for Docker port forwarding.
- WireMock listens on port 9090 and exposes its standard administrative endpoints (`/__admin`), so restrict access to 9090 as appropriate.
- Fixture filenames and every folder path segment accept only letters, digits, underscore, and hyphen; resolved paths must stay inside the data directory to prevent directory traversal.
- Keep secrets out of `application.yml`; use `${ENV_VAR}` placeholders. The pre-commit hook below enforces this.
- A bind-mounted fixture directory (`:ro`) allows updating fixtures in Docker without rebuilding the image.
- No datastore, authentication integration, external API calls, or dynamic SQL is included in this starter.

## Secret scanning on commit

A git pre-commit hook (`.githooks/pre-commit`) runs [Gitleaks](https://github.com/gitleaks/gitleaks) on the staged changes and blocks the commit if a secret is found in code or configuration files.

Rules (`.gitleaks.toml`):
- Gitleaks' built-in rules (cloud keys, tokens, private keys, high-entropy API keys, ...).
- `hardcoded-config-secret`: any literal value for a password/secret/token/key setting in `.yml`, `.yaml`, `.properties`, `.env`, `.xml`, etc. This catches simple passwords that the built-in entropy-based rule misses (e.g. `password: 6Hgdt%$dsfdf`). `${ENV_VAR}` placeholders are allowed.
- `hardcoded-code-secret`: string literals assigned to secret-like names in source code (e.g. `String apiKey = "..."`).
- `target/`, `.idea/` and `.tools/` are excluded.

One-time setup per clone:

```bash
bash scripts/install-gitleaks.sh                                  # Linux, macOS, Git Bash on Windows
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/install-gitleaks.ps1   # Windows PowerShell
```

| Script | Platform |
|---|---|
| `scripts/install-gitleaks.sh` | Linux (x64, arm64, armv6/v7, x32), macOS (Intel, Apple Silicon), Git Bash on Windows |
| `scripts/install-gitleaks.ps1` | Windows PowerShell (x64) |

Both scripts do the same thing. They download a pinned, checksum-verified Gitleaks (v8.30.1, matching your OS and CPU) into `.tools/gitleaks/` (gitignored) and run `git config --local core.hooksPath .githooks`, so the setup affects this repository only. Re-running them is safe. The bash script needs `curl` or `wget`, `tar` (or `unzip` on Windows), and `sha256sum` or `shasum`. Alternatively, install `gitleaks` on your `PATH` and run `git config --local core.hooksPath .githooks`. `.gitattributes` keeps the shell scripts and hooks on LF line endings. For a false positive, add a `gitleaks:allow` comment on that line or an allowlist entry in `.gitleaks.toml`.

Scan on demand:

```powershell
.tools\gitleaks\gitleaks.exe dir . --config .gitleaks.toml --redact   # whole working tree
.tools\gitleaks\gitleaks.exe git . --config .gitleaks.toml --redact   # full git history
```

## Testing

`mvn test` runs HTTP integration tests against an embedded WireMock instance on a random port. Tests assume the project root is the current working directory and use the real `./mock-data` fixtures. They cover folder and subfolder endpoints, header naming, error cases, path traversal, and activating a new subfolder through `POST /admin/reload`. That last test creates and removes a temporary folder under `mock-data/customers/`.

## Implementation notes

- Uses `wiremock-standalone` to isolate WireMock's transitive runtime dependencies from Spring Boot / Jackson.
- WireMock 3.x is used instead of the WireMock 4.x beta series.
- Spring Boot's `spring-boot-starter-webmvc` supports REST admin endpoints.
- Run-time property values and startup registration are Spring-managed.
