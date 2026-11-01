#!/usr/bin/env node
// Captures the Market Digest page showing today's $1.35B funding wave entries.
// Logs in, opens the market digest, waits for the entries to render, captures.
//
// Usage:
//   node linkedin-posts/135b-funding-wave-screenshot.js <email> <password>
//
// Output: linkedin-posts/shots/2026-10-01-135b-funding-wave-market.png

const { chromium } = require('playwright');
const path = require('path');

const BASE = 'https://app.bigskylabs.ai';
const OUT  = path.join('C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots',
                       '2026-10-01-135b-funding-wave-market.png');

const email    = process.argv[2];
const password = process.argv[3];

if (!email || !password) {
    console.error('Usage: node 135b-funding-wave-screenshot.js <email> <password>');
    process.exit(1);
}

async function main() {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1400, height: 900 },
        deviceScaleFactor: 2,
    });
    const page = await context.newPage();

    // 1. Log in
    console.log('Logging in...');
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.fill('input[name="username"], input[type="email"]', email);
    await page.fill('input[name="password"], input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL('**/dashboard**', { timeout: 20000 }).catch(() => {});
    console.log(`  After login → ${page.url()}`);

    // 2. Open the Market Digest page
    console.log('Loading Market Digest...');
    await page.goto(`${BASE}/dashboard/market`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(2000);

    // 3. Scroll down slightly to show ranked entries below the header
    await page.evaluate(() => window.scrollTo(0, 180));
    await page.waitForTimeout(500);

    // 4. Capture the top portion showing today's funding headlines
    console.log(`Capturing screenshot → ${OUT}`);
    await page.screenshot({ path: OUT, fullPage: false });
    console.log(`  ✓ Saved: ${OUT}`);

    await browser.close();
    console.log('\nDone. Attach linkedin-posts/shots/2026-10-01-135b-funding-wave-market.png to the post.');
}

main().catch(e => { console.error(e); process.exit(1); });
