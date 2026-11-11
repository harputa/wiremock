# Header-driven WireMock Service

A standalone Spring Boot 4.1.2 / Java 21 mock REST application with embedded WireMock 3.13.2, generic header-driven fixture resolution, configurable endpoint domains, an administration API, and Docker Compose.

## Prerequisites

- Java 21 and Maven 3.9+, **or** Docker with the Docker Compose plugin.
- Run commands from the **project root** (fixture paths are relative to working directory).

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
curl -i -H 'X-Product-Ref: 2000' http://localhost:9090/products
curl -i -H 'X-Product-Ref: 2001' http://localhost:9090/products
curl -i -H 'X-Product-Ref: 2999' http://localhost:9090/products
curl -i -H 'X-Customer-Ref: 2305' http://localhost:9090/customers
curl -i -H 'X-Order-Ref: 7001' http://localhost:9090/orders
curl -i http://localhost:9090/products
curl -i -H 'X-Product-Ref: 9999' http://localhost:9090/products
curl -s http://localhost:8080/admin/status
curl -X POST http://localhost:8080/admin/reload
```

PowerShell:

```powershell
Invoke-RestMethod -Uri http://localhost:9090/products -Headers @{ 'X-Product-Ref' = '2000' }
```

## Add or change mock data

Put a `<reference>.json` file under `mock-data/<response-folder>/`.
For example, `mock-data/products/2010.json` immediately enables `X-Product-Ref: 2010`; no Java code, restart, or reload required. JSON files are returned verbatim.

Optional `<reference>.properties` next to the JSON controls the HTTP response:

```properties
status=200
delay-ms=500
content-type=application/json
```

The valid delay range is 0–60000 ms. If no fixture exists, 404 is returned; if the configured header is absent, 400 is returned. Invalid or unreadable fixture metadata results in 500. Simulated failure: product reference `2999` returns 503.

## Configure new endpoints

Edit `src/main/resources/application.yml` before rebuilding:

```yaml
mock:
  wiremock-port: 9090
  data-directory: ./mock-data
  domains:
    product:
      path: /products
      selector-header: X-Product-Ref
      response-folder: products
    customer:
      path: /customers
      selector-header: X-Customer-Ref
      response-folder: customers
```

You can also override properties via Spring environment variables. The domain names and route configuration are read at startup; changing `application.yml` requires restarting the application. The `/admin/reload` endpoint recreates WireMock route stubs from the **already loaded** configuration and clears the request journal. Fixture file changes require **no** reload.

## How it works

```
Client :9090/products + X-Product-Ref:2000
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
- Fixture filenames accept only letters, digits, underscore, and hyphen; server-side folder configuration is also validated to avoid directory traversal.
- A bind-mounted fixture directory (`:ro`) allows updating fixtures in Docker without rebuilding the image.
- No datastore, authentication integration, external API calls, or dynamic SQL is included in this starter.

## Testing

`mvn test` runs seven HTTP integration tests against an embedded WireMock instance on a random port. Tests assume the project root is the current working directory.

## Implementation notes

- Uses `wiremock-standalone` to isolate WireMock's transitive runtime dependencies from Spring Boot / Jackson.
- WireMock 3.x is used instead of the WireMock 4.x beta series.
- Spring Boot's `spring-boot-starter-webmvc` supports REST admin endpoints.
- Run-time property values and startup registration are Spring-managed.
