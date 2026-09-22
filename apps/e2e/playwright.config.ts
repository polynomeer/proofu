import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against a real stack (Postgres, api, worker, web) plus the mock IdP — see scripts/e2e.sh.
 * Tests are serial: the mock IdP has one "current user" and the journeys build on each other.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3111",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "ko-KR",
    ...devices["Desktop Chrome"],
  },
});
