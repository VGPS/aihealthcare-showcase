/**
 * Captures a screenshot of the live Wiki hybrid search page at bigskylabs.ai.
 * Logs in as admin, navigates to /wiki, performs a sample search, saves screenshot.
 *
 * Usage: node screenshot-wiki.js
 * Requires: SCREENSHOT_EMAIL and SCREENSHOT_PASSWORD env vars (or hardcode for local use)
 */
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'https://app.bigskylabs.ai';
const OUT_DIR = path.join(__dirname, 'linkedin-posts', 'shots');

(async () => {
    if (!fs.existsSync(OUT_DIR)) fs.mkdirSync(OUT_DIR, { recursive: true });

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1280, height: 900 },
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120'
    });
    const page = await context.newPage();

    // --- 1. Log in ---
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', process.env.SCREENSHOT_EMAIL || '');
    await page.fill('input[name="password"]', process.env.SCREENSHOT_PASSWORD || '');
    await page.click('button[type="submit"]');
    await page.waitForURL(u => !u.includes('/login'), { timeout: 10000 });
    console.log('Logged in, current URL:', page.url());

    // --- 2. Wiki index — blank state ---
    await page.goto(`${BASE_URL}/wiki`);
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(OUT_DIR, 'wiki-index-blank.png'), fullPage: false });
    console.log('Saved wiki-index-blank.png');

    // --- 3. Wiki search — hybrid query ---
    await page.fill('input[name="query"]', 'AI misdiagnosis liability');
    await page.click('button[type="submit"]');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(OUT_DIR, 'wiki-hybrid-search-results.png'), fullPage: false });
    console.log('Saved wiki-hybrid-search-results.png');

    // --- 4. Second search to show breadth ---
    await page.goto(`${BASE_URL}/wiki?query=hospital+billing+AI`);
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: path.join(OUT_DIR, 'wiki-hybrid-search-billing.png'), fullPage: false });
    console.log('Saved wiki-hybrid-search-billing.png');

    await browser.close();
    console.log('Done. Screenshots saved to', OUT_DIR);
})().catch(err => {
    console.error('Screenshot failed:', err.message);
    process.exit(1);
});
