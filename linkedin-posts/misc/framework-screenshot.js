/**
 * Captures framework analysis screenshots for LinkedIn/Facebook/Substack posts.
 *
 * Usage: E2E_ADMIN_PASSWORD=<password> node linkedin-posts/framework-screenshot.js
 *
 * Outputs:
 *   linkedin-posts/shots/framework-overview.png   — multi-company radar chart + cards
 *   linkedin-posts/shots/framework-detail.png     — top-scoring company detail page
 *
 * @author  Bill Blackmon
 * @since   2026-09-25
 */
const { chromium } = require('playwright');
const path = require('path');

const BASE_URL = 'https://app.bigskylabs.ai';
const EMAIL    = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
const PASSWORD = process.env.E2E_ADMIN_PASSWORD;
const OUT_DIR  = path.join(__dirname, 'shots');

if (!PASSWORD) {
    console.error('ERROR: E2E_ADMIN_PASSWORD is not set.');
    console.error('Run: E2E_ADMIN_PASSWORD=yourpassword node linkedin-posts/framework-screenshot.js');
    process.exit(1);
}

(async () => {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
    });
    const page = await context.newPage();

    // --- Login ---
    console.log('Logging in...');
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', EMAIL);
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 15_000 });
    console.log('Logged in.');

    // --- Overview page ---
    console.log('Navigating to /dashboard/frameworks...');
    await page.goto(`${BASE_URL}/dashboard/frameworks`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500); // let Chart.js render

    const overviewPath = path.join(OUT_DIR, 'framework-overview.png');
    await page.screenshot({ path: overviewPath, fullPage: false });
    console.log(`Saved: ${overviewPath}`);

    // --- Find the top-scored company link ---
    // Company cards have an overall score; pick the first card (highest ranked)
    const firstCompanyLink = await page.locator('a[href^="/dashboard/frameworks/"]').first().getAttribute('href');
    if (!firstCompanyLink) {
        console.warn('No company links found on overview page — skipping detail screenshot.');
    } else {
        console.log(`Navigating to detail page: ${firstCompanyLink}...`);
        await page.goto(`${BASE_URL}${firstCompanyLink}`, { waitUntil: 'networkidle' });
        await page.waitForTimeout(1500);

        const detailPath = path.join(OUT_DIR, 'framework-detail.png');
        await page.screenshot({ path: detailPath, fullPage: false });
        console.log(`Saved: ${detailPath}`);

        // Also grab the company name for reference
        const companyName = await page.locator('h1').first().textContent();
        console.log(`Top company: ${companyName?.trim()}`);
    }

    await browser.close();
    console.log('Done.');
})();
