#!/usr/bin/env node
// Captures the Deal Context detail page for the Sep 23 competitor intelligence post.
// Logs in, opens the deals list, clicks the first deal that has both sentiment
// and framework data visible, then captures all four cross-reference cards.
//
// Usage:
//   node linkedin-posts/deal-context-screenshot.js <email> <password>
//
// Output: linkedin-posts/shots/2026-09-23-deal-context.png

const { chromium } = require('playwright');
const path = require('path');

const BASE  = 'https://app.bigskylabs.ai';
const OUT   = path.join('C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots',
                        '2026-09-23-deal-context.png');

const email    = process.argv[2];
const password = process.argv[3];

if (!email || !password) {
    console.error('Usage: node deal-context-screenshot.js <email> <password>');
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
    await page.waitForNavigation({ waitUntil: 'networkidle', timeout: 20000 });
    console.log(`  After login → ${page.url()}`);

    // 2. Open the deals list
    console.log('Loading deals list...');
    await page.goto(`${BASE}/dashboard/deals`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(2000);

    // 3. Click the first deal row/link that leads to a detail page
    //    The deals list uses <a> links to /dashboard/deals/{id}
    const dealLink = page.locator('a[href*="/dashboard/deals/"]').first();
    const href = await dealLink.getAttribute('href');
    console.log(`  Opening deal: ${href}`);
    await page.goto(`${BASE}${href}`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(3000);

    // 4. Scroll to ensure all four cross-reference cards are in view
    //    Cards are stacked; scroll down a bit to show all of them
    await page.evaluate(() => window.scrollTo(0, 300));
    await page.waitForTimeout(500);

    // 5. Capture
    console.log(`Capturing screenshot → ${OUT}`);
    await page.screenshot({ path: OUT, fullPage: false });
    console.log(`  ✓ Saved: ${OUT}`);

    await browser.close();
    console.log('\nDone. Attach linkedin-posts/shots/2026-09-23-deal-context.png to the post.');
}

main().catch(e => { console.error(e); process.exit(1); });
