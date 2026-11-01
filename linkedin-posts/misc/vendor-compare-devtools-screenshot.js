/**
 * Captures a vendor comparison screenshot for the developer tools LinkedIn post.
 *
 * Navigates to the pre-filled vendor compare URL (OpenAI, Anthropic, Amazon, Google)
 * with the developer tools query, waits for all four vendor cards to render, then
 * takes a viewport screenshot showing the form header + full 2x2 vendor card grid.
 *
 * Usage:
 *   E2E_ADMIN_PASSWORD=<password> node linkedin-posts/vendor-compare-devtools-screenshot.js
 *
 * Output:
 *   linkedin-posts/shots/2026-10-09-developer-tools-vendor-compare.png
 *
 * @author  Bill Blackmon
 * @since   2026-10-09
 */
const { chromium } = require('playwright');
const path = require('path');

const BASE_URL  = 'https://app.bigskylabs.ai';
const EMAIL     = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
const PASSWORD  = process.env.E2E_ADMIN_PASSWORD;
const OUT_DIR   = path.join(__dirname, 'shots');
const OUT_FILE  = path.join(OUT_DIR, '2026-10-09-developer-tools-vendor-compare.png');

const COMPARE_URL = BASE_URL + '/research/vendors'
    + '?vendors=OpenAI+Healthcare'
    + '&vendors=Anthropic+Healthcare'
    + '&vendors=Amazon+Connect+Health'
    + '&vendors=Google+Healthcare'
    + '&focusArea='
    + '&query=please+compare+developer+tools+for+each+vendor'
    + '&maxSources=20'
    + '&scoring=DOC_FREQUENCY';

if (!PASSWORD) {
    console.error('ERROR: E2E_ADMIN_PASSWORD is not set.');
    console.error('Run: E2E_ADMIN_PASSWORD=yourpassword node linkedin-posts/vendor-compare-devtools-screenshot.js');
    process.exit(1);
}

(async () => {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1440, height: 1100 },
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

    // --- Navigate to vendor compare with developer tools query ---
    console.log('Navigating to vendor compare page...');
    await page.goto(COMPARE_URL, { waitUntil: 'networkidle', timeout: 60_000 });

    // Wait for all four vendor cards to render
    console.log('Waiting for vendor cards...');
    await page.waitForSelector('[data-vendor]', { timeout: 30_000 });
    const cardCount = await page.locator('[data-vendor]').count();
    console.log(`Vendor cards found: ${cardCount}`);

    // Extra settle time for the relevance bars and score chips to paint
    await page.waitForTimeout(1500);

    // Scroll to the results section so vendor cards fill the viewport
    await page.evaluate(() => {
        const results = document.querySelector('[data-vendor]');
        if (results) results.scrollIntoView({ block: 'start' });
    });
    // Small nudge upward so the "Found N vendor(s)" summary line is visible above the cards
    await page.evaluate(() => window.scrollBy(0, -40));
    await page.waitForTimeout(300);

    // --- Screenshot: viewport showing vendor card grid ---
    await page.screenshot({ path: OUT_FILE, fullPage: false });
    console.log(`Saved: ${OUT_FILE}`);

    // Log vendor names and scores for verification
    const vendors = await page.locator('[data-vendor]').all();
    for (const v of vendors) {
        const name  = await v.getAttribute('data-vendor');
        const score = await v.locator('.rounded-full').first().textContent();
        console.log(`  ${name}: ${score?.trim()}`);
    }

    await browser.close();
    console.log('Done.');
})();
