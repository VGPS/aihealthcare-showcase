#!/usr/bin/env node
// Captures a curated view of the Company Relationship Graph for the 2026-10-07
// "entity extraction cleanup" post — sorts by confidence descending, then keeps
// only the clean, high-confidence real-company rows so the screenshot shows the
// post-fix state rather than the residual pre-LLM-era rows still in the table.
//
// Usage: node linkedin-posts/relationship-cleanup-screenshot.js <email> <password>
// Output: linkedin-posts/shots/2026-10-07-relationship-cleanup.png

const { chromium } = require('playwright');
const path = require('path');

const BASE = 'https://app.bigskylabs.ai';
const OUT  = path.join('C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots',
                        '2026-10-07-relationship-cleanup.png');

const email    = process.argv[2];
const password = process.argv[3];

if (!email || !password) {
    console.error('Usage: node relationship-cleanup-screenshot.js <email> <password>');
    process.exit(1);
}

// Keep only rows whose Source cell contains one of these (case-insensitive) —
// the clean, verified, high-confidence relationships from this session's fix.
const KEEP_SOURCES = [
    'r1', 'university of texas medical branch', 'parakeet health',
    'qc healthcare', 'openevidence', 'kaiser permanente', 'yale university',
];

async function main() {
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

    console.log('Loading relationships table...');
    await page.goto(`${BASE}/dashboard/relationships`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(1500);

    console.log('Sorting by confidence (high-low)...');
    await page.click('span[onclick*="sortTable(this, 4, \'desc\')"]');
    await page.waitForTimeout(500);

    console.log('Filtering to clean, verified rows...');
    await page.evaluate((keepSources) => {
        const rows = Array.from(document.querySelectorAll('table tbody tr'));
        // Rows come in pairs: a visible summary row + a hidden detail row right after it.
        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            if (row.id && row.id.startsWith('detail-')) continue; // handled alongside its summary row
            const sourceCell = row.querySelector('td');
            const text = (sourceCell ? sourceCell.textContent : '').trim().toLowerCase();
            const keep = keepSources.some((s) => text.includes(s));
            if (!keep) {
                row.style.display = 'none';
                const next = rows[i + 1];
                if (next && next.id && next.id.startsWith('detail-')) {
                    next.style.display = 'none';
                }
            }
        }
    }, KEEP_SOURCES);
    await page.waitForTimeout(300);

    console.log('Scrolling to table...');
    await page.evaluate(() => {
        const heading = Array.from(document.querySelectorAll('h2'))
            .find((h) => h.textContent.includes('Detected Relationships'));
        if (heading) heading.scrollIntoView({ block: 'start' });
        window.scrollBy(0, -40);
    });
    await page.waitForTimeout(400);

    console.log(`Capturing screenshot → ${OUT}`);
    await page.screenshot({ path: OUT, fullPage: false });
    console.log(`  ✓ Saved: ${OUT}`);

    await browser.close();
    console.log('\nDone. Attach linkedin-posts/shots/2026-10-07-relationship-cleanup.png to the post.');
}

main().catch(e => { console.error(e); process.exit(1); });
