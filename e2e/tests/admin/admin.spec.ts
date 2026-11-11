import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { env } from '../../support/env';
import { discoverEndpoints } from '../../support/mock-data';

// POST /admin/reload resets all WireMock stubs, so these tests run one at a time (and after the
// wiremock project, see playwright.config.ts).
test.describe.configure({ mode: 'serial' });

test.describe('actuator', () => {
  test('GET /actuator/health -> UP', async ({ request }) => {
    const response = await request.get('/actuator/health');
    expect(response.status()).toBe(200);
    expect((await response.json()).status).toBe('UP');
  });

  test('GET /actuator/info is exposed', async ({ request }) => {
    expect((await request.get('/actuator/info')).status()).toBe(200);
  });

  test('other actuator endpoints are not exposed', async ({ request }) => {
    expect((await request.get('/actuator/env')).status()).toBe(404);
  });
});

test.describe('GET /admin/status', () => {
  test('reports WireMock running and lists every mock-data folder', async ({ request }) => {
    const response = await request.get('/admin/status');
    expect(response.status()).toBe(200);
    const status = await response.json();

    expect(status.running).toBe(true);
    expect(String(status.mockPort)).toBe(new URL(env.wiremockUrl).port || '80');
    expect(status.domains).toEqual(
      discoverEndpoints().map((e) => ({ folder: e.folder, path: e.path, selectorHeader: e.header })),
    );
  });
});

test.describe('POST /admin/reload', () => {
  test('re-registers one endpoint per mock-data folder', async ({ request }) => {
    const response = await request.post('/admin/reload');
    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ result: 'reloaded', registeredDomains: discoverEndpoints().length });
  });

  test('GET /admin/reload is not allowed', async ({ request }) => {
    expect((await request.get('/admin/reload')).status()).toBe(405);
  });

  test('activates a new subfolder, and removes it again after it is deleted', async ({ request }) => {
    test.skip(env.readOnlyData, 'E2E_READONLY_DATA=1: cannot create folders in the server data directory');

    const top = discoverEndpoints().find((e) => !e.folder.includes('/'))!;
    const name = `e2e${Date.now()}`;
    const folder = path.join(env.mockDataDir, top.folder, name);
    const url = `${env.wiremockUrl}${top.path}/${name}`;
    const headers = { [top.header]: '1' };

    try {
      fs.mkdirSync(folder, { recursive: true });
      fs.writeFileSync(path.join(folder, '1.json'), '{"source":"playwright"}');

      expect((await request.get(url, { headers })).status(), 'not active before reload').toBe(404);

      expect((await request.post('/admin/reload')).status()).toBe(200);
      const activated = await request.get(url, { headers });
      expect(activated.status()).toBe(200);
      expect(await activated.json()).toEqual({ source: 'playwright' });
    } finally {
      fs.rmSync(folder, { recursive: true, force: true });
      await request.post('/admin/reload');
    }

    expect((await request.get(url, { headers })).status(), 'removed after reload').toBe(404);
  });
});
