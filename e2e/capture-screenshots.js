/**
 * Standalone screenshot capture script for weekly social post assets.
 * Logs in as admin, navigates to 4 pages, saves full-page screenshots.
 *
 * Usage: node e2e/capture-screenshots.js
 *
 * @author  Bill Blackmon
 * @since   2026-09-30
 * @updated 2026-09-30
 */

const { chromium } = require('@playwright/test');
const path = require('path');

try { require('dotenv').config(); } catch (_) {}

const BASE_URL = process.env.BASE_URL || 'https://app.bigskylabs.ai';
const EMAIL    = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
const PASSWORD = process.env.E2E_ADMIN_PASSWORD;
const SHOTS_DIR = path.resolve(__dirname, '../linkedin-posts/shots');

const PAGES = [
    {
        url:  '/dashboard/trends',
        file: '2026-09-30-ambient-ai-trends.png',
        label: 'Trend Detection',
    },
    {
        url:  '/directory',
        file: '2026-10-01-epic-gatekeeper-directory.png',
        label: 'Company Directory',
    },
    {
        url:  '/dashboard/market',
        file: '2026-10-02-pilot-purgatory-market.png',
        label: 'Market Digest',
    },
    {
        url:  '/legislation',
        file: '2026-10-03-medicare-cuts-legislation.png',
        label: 'State Legislation',
    },
];

(async () => {
    if (!PASSWORD) {
        console.error('ERROR: E2E_ADMIN_PASSWORD not set');
        process.exit(1);
    }

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();

    // Login
    console.log(`Logging in at ${BASE_URL}/login ...`);
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', EMAIL);
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 15_000 });
    console.log('  Login OK');

    for (const { url, file, label } of PAGES) {
        const fullUrl = `${BASE_URL}${url}`;
        const outPath = path.join(SHOTS_DIR, file);

        console.log(`Capturing: ${label} (${fullUrl})`);
        await page.goto(fullUrl, { waitUntil: 'networkidle', timeout: 30_000 });

        // Give charts / lazy content a moment to render
        await page.waitForTimeout(1500);

        await page.screenshot({ path: outPath, fullPage: true });
        console.log(`  Saved → ${outPath}`);
    }

    await browser.close();
    console.log('\nAll screenshots captured.');
})();
