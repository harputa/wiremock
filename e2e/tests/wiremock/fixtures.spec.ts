import { test, expect } from '@playwright/test';
import { discoverFixtures } from '../../support/mock-data';

/**
 * One test per fixture file in ../mock-data. Adding a fixture adds a test automatically:
 * the expected status, delay and content type come from the optional <ref>.properties file,
 * and the expected body is the JSON file itself (the server returns it verbatim).
 */
const fixtures = discoverFixtures();

test.describe('every mock-data fixture is served', () => {
  test('mock-data contains fixtures', () => {
    expect(fixtures.length).toBeGreaterThan(0);
  });

  for (const fixture of fixtures) {
    const { endpoint, reference } = fixture;

    test(`GET ${endpoint.path} with ${endpoint.header}: ${reference} -> ${fixture.status}`, async ({ request }) => {
      const started = Date.now();
      const response = await request.get(endpoint.path, { headers: { [endpoint.header]: reference } });
      const elapsed = Date.now() - started;

      expect(response.status()).toBe(fixture.status);
      expect(response.headers()['content-type']).toContain(fixture.contentType);
      expect(await response.text()).toBe(fixture.body);
      if (fixture.contentType.includes('json')) {
        expect(await response.json()).toEqual(JSON.parse(fixture.body));
      }
      if (fixture.delayMs > 0) {
        // Allow a little timer slack below the configured fixed delay.
        expect(elapsed).toBeGreaterThanOrEqual(fixture.delayMs - 25);
      }
    });
  }
});
