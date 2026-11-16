// @ts-check
/**
 * Playwright configuration for AIHealthcare E2E tests.
 *
 * Runs against localhost:8080 by default.
 * Override with: BASE_URL=https://app.bigskylabs.ai npx playwright test
 *
 * Required environment variables (set in .env or shell):
 *   E2E_ADMIN_EMAIL    — admin user email
 *   E2E_ADMIN_PASSWORD — admin user password
 */
const { defineConfig, devices } = require('@playwright/test');

// Load .env if dotenv is available (optional convenience)
try { require('dotenv').config(); } catch (_) {}

module.exports = defineConfig({
    testDir: './e2e',
    timeout: 30_000,
    expect: { timeout: 8_000 },
    fullyParallel: false,   // serial — tests share a login session file
    retries: 1,
    reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e/report' }]],
    use: {
        baseURL: process.env.BASE_URL || 'http://localhost:8080',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
    },
    projects: [
        // Setup: log in once and save session state
        {
            name: 'setup',
            testMatch: /.*\.setup\.js/,
        },
        // Enterprise access tests reuse the saved session
        {
            name: 'enterprise-access',
            testMatch: /.*\.spec\.js/,
            dependencies: ['setup'],
            use: {
                ...devices['Desktop Chrome'],
                storageState: 'e2e/.auth/admin.json',
            },
        },
    ],
});
