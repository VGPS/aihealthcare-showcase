/**
 * Captures ALL screenshots required for the 2026-10-03 AI billing conflict LinkedIn post.
 *
 * Screenshots produced:
 *   1. 2026-10-03-ai-billing-conflict-search.png   — AI Search results (primary post image)
 *   2. 2026-10-03-ai-billing-conflict-regulatory.png — Regulatory dashboard (alternative image)
 *
 * Login is performed once; both pages are captured in the same browser session.
 * AI Search uses Perplexity Deep — allow up to 120 s for synthesis to render.
 *
 * Usage:
 *   node e2e/capture-ai-billing-conflict.js
 *
 * @author  Bill Blackmon
 * @since   2026-10-04
 * @updated 2026-10-04
 */

const { chromium } = require('@playwright/test');
const path = require('path');

try { require('dotenv').config(); } catch (_) {}

const BASE_URL  = process.env.BASE_URL  || 'https://app.bigskylabs.ai';
const EMAIL     = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
const PASSWORD  = process.env.E2E_ADMIN_PASSWORD;
const SHOTS_DIR = path.resolve(__dirname, '../linkedin-posts/shots');

const SHOTS = [
    {
        label:   'AI Search results',
        url:     `${BASE_URL}/research/ai-search?q=${encodeURIComponent('AI billing conflict hospitals vs insurers')}&models=Perplexity+Deep&topK=20`,
        file:    path.join(SHOTS_DIR, '2026-10-03-ai-billing-conflict-search.png'),
        timeout: 120_000,
        check:   async (page) => {
            const n = await page.locator('[data-model="Perplexity Deep"]').count();
            if (n === 0) console.warn('  WARNING: no Perplexity Deep card found — screenshot may show empty state');
            else         console.log(`  Synthesis card found (${n} card)`);
        },
    },
    {
        label:   'Regulatory dashboard',
        url:     `${BASE_URL}/dashboard/regulatory`,
        file:    path.join(SHOTS_DIR, '2026-10-03-ai-billing-conflict-regulatory.png'),
        timeout: 30_000,
        check:   async (page) => {
            const n = await page.locator('table tbody tr, .regulatory-event, [class*="event"]').count();
            console.log(`  Regulatory rows/events visible: ${n}`);
        },
    },
];

(async () => {
    if (!PASSWORD) {
        console.error('ERROR: E2E_ADMIN_PASSWORD not set in .env');
        process.exit(1);
    }

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page    = await context.newPage();

    // --- Login once ---
    console.log(`Logging in at ${BASE_URL}/login ...`);
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', EMAIL);
    await page.fill('input[name="password"]', PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 15_000 });
    console.log('  Login OK\n');

    // --- Capture each screenshot ---
    for (const shot of SHOTS) {
        console.log(`[${shot.label}]`);
        console.log(`  URL: ${shot.url}`);
        if (shot.timeout > 30_000) console.log(`  Waiting up to ${shot.timeout / 1000} s for synthesis...`);

        await page.goto(shot.url, { waitUntil: 'networkidle', timeout: shot.timeout });
        await page.waitForTimeout(2000);

        await shot.check(page);
        await page.screenshot({ path: shot.file, fullPage: true });
        console.log(`  Saved → ${shot.file}\n`);
    }

    await browser.close();

    console.log('Done. Screenshots saved:');
    for (const shot of SHOTS) console.log(`  ${path.basename(shot.file)}`);
})();
