import { test as setup, expect } from '@playwright/test';
import { env } from '../support/env';
import { discoverEndpoints } from '../support/mock-data';

// Actuator health can report UP slightly before StubRegistry has registered the WireMock stubs,
// so wait until /admin/status lists every folder from mock-data.
setup('service is up and all mock-data folders are registered', async ({ request }) => {
  const expected = discoverEndpoints().map((e) => e.path);
  expect(expected.length, 'mock-data should contain at least one folder').toBeGreaterThan(0);

  await expect
    .poll(
      async () => {
        const response = await request.get(`${env.adminUrl}/admin/status`).catch(() => null);
        if (!response?.ok()) return [];
        const status = await response.json();
        return (status.domains as { path: string }[]).map((d) => d.path);
      },
      { timeout: 60_000, message: `endpoints from ${env.mockDataDir} were not registered` },
    )
    .toEqual(expect.arrayContaining(expected));
});
