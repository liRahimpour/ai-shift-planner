import { defineConfig, devices } from '@playwright/test'

/**
 * End-to-end tests run against a *running stack* (backend + PostgreSQL + seeded demo data),
 * which is why they are not part of `npm test` and not part of the default CI job. Start the
 * stack first:
 *
 *   SEED_DEMO_DATA=true docker compose up --build
 *   npm run e2e
 *
 * Point them elsewhere with E2E_BASE_URL. Keeping them opt-in means the fast feedback loop
 * stays fast, and a failing e2e run always means "the system is broken", never "the
 * environment was not up".
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'de-DE',
    timezoneId: 'Europe/Berlin',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
