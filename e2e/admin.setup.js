/**
 * Login setup — runs once before all tests, saves session cookie to
 * e2e/.auth/admin.json so every subsequent test starts already logged in.
 *
 * @author  Bill Blackmon
 * @since   2026-09-23
 */
const { test: setup, expect } = require('@playwright/test');
const path = require('path');

const STORAGE_STATE = path.join(__dirname, '.auth', 'admin.json');

setup('authenticate as admin', async ({ page }) => {
    const email    = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
    const password = process.env.E2E_ADMIN_PASSWORD;

    if (!password) {
        throw new Error(
            'E2E_ADMIN_PASSWORD environment variable is not set.\n' +
            'Add it to your .env file: E2E_ADMIN_PASSWORD=<your admin password>'
        );
    }

    await page.goto('/login');
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');

    // Confirm successful login — should land on dashboard, not stay on /login
    await expect(page).not.toHaveURL(/\/login/, { timeout: 10_000 });
    await expect(page.locator('body')).not.toContainText('Bad credentials');

    await page.context().storageState({ path: STORAGE_STATE });
});
