#!/usr/bin/env node
// Captures a curated view of the AI Healthcare Company Directory's top relevance
// tier for the 2026-09-29 "weren't built for healthcare" post — real live product
// data, filtered down to the 7 named companies referenced in the post copy so the
// screenshot isn't buried in junk/duplicate directory entries tied at the same score.
//
// Usage: node linkedin-posts/bigtech-directory-screenshot.js
// Output: linkedin-posts/shots/2026-09-29-bigtech-directory-tier.png

const { chromium } = require('playwright');
const path = require('path');

const BASE = 'https://app.bigskylabs.ai';
const OUT  = path.join('C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots',
                        '2026-09-29-bigtech-directory-tier.png');

// exact: match card name exactly (case-insensitive, trimmed) — for short/ambiguous names
// contains: match if card name contains this substring (case-insensitive) — safe for longer names
const TARGETS = [
    { match: 'exact',    value: 'ada health gmbh' },
    { match: 'exact',    value: 'eko health' },
    { match: 'exact',    value: 'ge healthcare' },
    { match: 'exact',    value: 'google' },
    { match: 'exact',    value: 'k health' },
    { match: 'contains', value: 'microsoft corporation' },
    { match: 'contains', value: 'intel corporation' },
];

async function main() {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1400, height: 900 },
        deviceScaleFactor: 2,
    });
    const page = await context.newPage();

    console.log('Loading directory (trending sort)...');
    await page.goto(`${BASE}/directory?sort=trending`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(1500);

    const result = await page.evaluate((targets) => {
        const isMatch = (name) => {
            const n = name.trim().toLowerCase();
            return targets.some(t => t.match === 'exact' ? n === t.value : n.includes(t.value));
        };

        const candidates = Array.from(document.querySelectorAll('div'))
            .filter(d => d.className && typeof d.className === 'string'
                && d.className.includes('grid-cols-1') && d.className.includes('lg:grid-cols-3'));
        if (candidates.length === 0) return { error: 'grid container not found' };
        const grid = candidates[0];

        const kept = [];
        Array.from(grid.children).forEach(card => {
            const nameEl = card.querySelector('a.font-display');
            const name = nameEl ? nameEl.textContent.trim() : '';
            if (name && isMatch(name)) {
                kept.push(name);
            } else {
                card.style.display = 'none';
            }
        });

        grid.style.padding = '20px';
        grid.setAttribute('data-shot-target', '1');
        return { kept };
    }, TARGETS);

    if (result.error) {
        console.error('ERROR:', result.error);
        await browser.close();
        process.exit(1);
    }

    console.log('Matched companies:', result.kept);
    if (!result.kept || result.kept.length === 0) {
        console.error('No matching companies found on page — aborting screenshot.');
        await browser.close();
        process.exit(1);
    }
    if (result.kept.length !== TARGETS.length) {
        console.warn(`WARNING: expected ${TARGETS.length} matches, found ${result.kept.length}. Check names before using this screenshot.`);
    }

    await page.waitForTimeout(500);
    const target = page.locator('[data-shot-target="1"]');
    await target.screenshot({ path: OUT });
    console.log(`Saved: ${OUT}`);

    await browser.close();
}

main().catch(e => { console.error(e); process.exit(1); });
