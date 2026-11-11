import path from 'node:path';

/** Where the service under test listens. Override to test a Docker or remote instance. */
export const env = {
  /** WireMock mock endpoints (mock.wiremock-port). */
  wiremockUrl: process.env.WIREMOCK_URL ?? 'http://127.0.0.1:9090',
  /** Spring Boot admin API and actuator (server.port). */
  adminUrl: process.env.ADMIN_URL ?? 'http://127.0.0.1:8080',
  /** The fixture directory the tests read their expectations from; must be the one the server uses. */
  mockDataDir: path.resolve(__dirname, '..', process.env.E2E_MOCK_DATA_DIR ?? '../mock-data'),
  /**
   * Set E2E_READONLY_DATA=1 when the tests cannot write into the server's data directory
   * (e.g. Docker Compose mounts it read-only); tests that create folders are then skipped.
   */
  readOnlyData: process.env.E2E_READONLY_DATA === '1',
};
