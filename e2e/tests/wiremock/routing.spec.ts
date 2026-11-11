import { test, expect } from '@playwright/test';
import { discoverEndpoints, discoverFixtures, unknownReference } from '../../support/mock-data';

const endpoints = discoverEndpoints();
const fixtures = discoverFixtures();

test.describe('header and URL rules for every endpoint', () => {
  for (const endpoint of endpoints) {
    test(`GET ${endpoint.path} without ${endpoint.header} -> 400 MISSING_REFERENCE`, async ({ request }) => {
      const response = await request.get(endpoint.path);
      expect(response.status()).toBe(400);
      expect(await response.json()).toEqual({
        error: 'MISSING_REFERENCE',
        message: `Required header: ${endpoint.header}`,
      });
    });

    test(`GET ${endpoint.path} with unknown reference -> 404 FIXTURE_NOT_FOUND`, async ({ request }) => {
      const response = await request.get(endpoint.path, { headers: { [endpoint.header]: unknownReference() } });
      expect(response.status()).toBe(404);
      expect((await response.json()).error).toBe('FIXTURE_NOT_FOUND');
    });

    test(`GET ${endpoint.path} with a blank ${endpoint.header} -> 400`, async ({ request }) => {
      const response = await request.get(endpoint.path, { headers: { [endpoint.header]: ' ' } });
      expect(response.status()).toBe(400);
    });
  }
});

test.describe('selector header naming', () => {
  const sample = fixtures.find((f) => f.status === 200)!;

  test('header name is case-insensitive', async ({ request }) => {
    const response = await request.get(sample.endpoint.path, {
      headers: { [sample.endpoint.header.toLowerCase()]: sample.reference },
    });
    expect(response.status()).toBe(200);
    expect(await response.text()).toBe(sample.body);
  });

  for (const fixture of fixtures.filter((f) => f.endpoint.folder.includes('/'))) {
    const { endpoint, reference } = fixture;
    const leaf = endpoint.folder.split('/').pop()!;
    const leafHeader = `X-${leaf.charAt(0).toUpperCase()}${leaf.slice(1)}-Ref`;

    test(`subfolder ${endpoint.path} uses ${endpoint.header}, not ${leafHeader}`, async ({ request }) => {
      const response = await request.get(endpoint.path, { headers: { [leafHeader]: reference } });
      expect(response.status()).toBe(400);
      expect((await response.json()).message).toBe(`Required header: ${endpoint.header}`);
    });
  }
});

test.describe('folder isolation', () => {
  // A reference only resolves in its own folder, not in a parent or child folder.
  for (const fixture of fixtures) {
    const others = endpoints.filter(
      (e) =>
        e.header === fixture.endpoint.header &&
        e.folder !== fixture.endpoint.folder &&
        !fixtures.some((f) => f.endpoint.folder === e.folder && f.reference === fixture.reference),
    );
    for (const other of others) {
      test(`${fixture.endpoint.folder}/${fixture.reference} is not served at ${other.path}`, async ({ request }) => {
        const response = await request.get(other.path, { headers: { [other.header]: fixture.reference } });
        expect(response.status()).toBe(404);
      });
    }
  }
});

test.describe('unsafe input', () => {
  const sample = fixtures.find((f) => f.endpoint.folder.includes('/')) ?? fixtures[0];

  for (const reference of ['../2000', '..\\2000', '2000.json', '%2e%2e%2f2000', 'a/b', '']) {
    test(`reference ${JSON.stringify(reference)} is not resolved outside its folder`, async ({ request }) => {
      const response = await request.get(sample.endpoint.path, { headers: { [sample.endpoint.header]: reference } });
      expect([400, 404]).toContain(response.status());
    });
  }

  test('unknown top-level path is not stubbed', async ({ request }) => {
    const response = await request.get('/no-such-folder', { headers: { 'X-No-such-folder-Ref': '1' } });
    expect(response.status()).toBe(404);
  });

  test('only GET is stubbed', async ({ request }) => {
    const { endpoint, reference } = fixtures[0];
    const response = await request.post(endpoint.path, { headers: { [endpoint.header]: reference } });
    expect(response.status()).toBe(404);
  });
});
