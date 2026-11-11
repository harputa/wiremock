import { defineConfig } from '@playwright/test';
import path from 'node:path';
import { env } from './support/env';

/**
 * API-only Playwright suite (no browsers needed).
 *
 * Projects run in this order:
 *   setup    - waits until the service is up and has registered its endpoints
 *   wiremock - mock endpoints on :9090, generated from ../mock-data (runs in parallel)
 *   admin    - Spring Boot admin API / actuator on :8080; runs last and serially because
 *              POST /admin/reload briefly removes all WireMock stubs.
 *
 * If nothing is listening on ADMIN_URL, the service is started with `mvn spring-boot:run`
 * from the project root (set E2E_NO_SERVER=1 to never start it).
 */
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'on-failure' }]],
  use: {
    extraHTTPHeaders: { Accept: 'application/json' },
  },
  projects: [
    {
      name: 'setup',
      testMatch: /global\.setup\.ts/,
    },
    {
      name: 'wiremock',
      testDir: './tests/wiremock',
      dependencies: ['setup'],
      use: { baseURL: env.wiremockUrl },
    },
    {
      name: 'admin',
      testDir: './tests/admin',
      dependencies: ['wiremock'],
      fullyParallel: false,
      use: { baseURL: env.adminUrl },
    },
  ],
  workers: process.env.CI ? 2 : undefined,
  webServer: process.env.E2E_NO_SERVER
    ? undefined
    : {
        command: 'mvn -B -ntp spring-boot:run',
        cwd: path.resolve(__dirname, '..'),
        url: `${env.adminUrl}/actuator/health`,
        reuseExistingServer: !process.env.CI,
        timeout: 180_000,
        stdout: 'ignore',
        stderr: 'pipe',
      },
});
