#!/usr/bin/env node
/**
 * Captures a representative view of the Company Relationship Graph
 * for the 2026-10-07 "feature value" social post.
 *
 * Shows the table sorted by confidence descending — the clean, live
 * production state after the three-stage pipeline improvements.
 *
 * Output: docs/shots/2026-10-07-relationships-feature.png
 *
 * Usage:
 *   node linkedin-posts/relationship-feature-screenshot.js <email> <password>
 */

const { chromium } = require('playwright');
const path  = require('path');
const fs    = require('fs');

const BASE = 'https://app.bigskylabs.ai';
const OUT  = path.resolve(
    'C:/workspaces/SpringAIClaude/AIHealthcare/docs/shots',
    '2026-10-07-relationships-feature.png'
);

const email    = process.argv[2];
const password = process.argv[3];

if (!email || !password) {
    console.error('Usage: node relationship-feature-screenshot.js <email> <password>');
    process.exit(1);
}

async function main() {
    // Ensure output directory exists
    fs.mkdirSync(path.dirname(OUT), { recursive: true });

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1400, height: 900 },
        deviceScaleFactor: 2,
    });
    const page = await context.newPage();

    console.log('Logging in...');
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.fill('input[name="username"], input[type="email"]', email);
    await page.fill('input[name="password"], input[type="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 20000 });
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    console.log(`  After login → ${page.url()}`);

    console.log('Loading relationships page...');
    await page.goto(`${BASE}/dashboard/relationships`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(1500);

    console.log('Sorting by confidence high-to-low...');
    await page.click('span[onclick*="sortTable(this, 4, \'desc\')"]');
    await page.waitForTimeout(500);

    // Scroll to the table heading so the screenshot shows table + explanation banner
    await page.evaluate(() => {
        const heading = Array.from(document.querySelectorAll('h2'))
            .find(h => h.textContent.includes('Detected Relationships'));
        if (heading) {
            heading.scrollIntoView({ block: 'start' });
            window.scrollBy(0, -160); // pull up to show explanation banner above table
        }
    });
    await page.waitForTimeout(400);

    console.log(`Saving screenshot → ${OUT}`);
    await page.screenshot({ path: OUT, fullPage: false });
    console.log(`  ✓ ${OUT}`);

    await browser.close();
    console.log('\nDone. Use docs/shots/2026-10-07-relationships-feature.png for social posts.');
}

main().catch(e => { console.error(e); process.exit(1); });
