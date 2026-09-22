#!/usr/bin/env node
// Captures screenshots for Medium article
// Output: linkedin-posts/shots/medium-*.png

const { chromium } = require('playwright');
const path = require('path');

const OUT = 'C:/workspaces/SpringAIClaude/AIHealthcare/linkedin-posts/shots';

const SHOTS = [
    {
        file: 'medium-01-directory-hero.png',
        url:  'https://app.bigskylabs.ai/directory',
        desc: 'Company directory — hero image (top of article)',
        // Show top fold: sort tabs, signal badges, company cards
        scrollY: 0,
        wait: 3000,
    },
    {
        file: 'medium-02-directory-cards.png',
        url:  'https://app.bigskylabs.ai/directory',
        desc: 'Company directory — card detail (mid-article)',
        scrollY: 400,
        wait: 3000,
    },
    {
        file: 'medium-03-wiki-index.png',
        url:  'https://app.bigskylabs.ai/wiki',
        desc: 'Wiki index — 840 pages visible',
        scrollY: 0,
        wait: 3000,
    },
    {
        file: 'medium-04-legislation-map.png',
        url:  'https://app.bigskylabs.ai/legislation',
        desc: 'State health-AI legislation map — eye-catching visual',
        scrollY: 0,
        wait: 4000,
    },
];

async function main() {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1400, height: 800 },
        deviceScaleFactor: 2,  // retina quality
    });

    for (const shot of SHOTS) {
        console.log(`Capturing: ${shot.file}`);
        const page = await context.newPage();
        await page.goto(shot.url, { waitUntil: 'networkidle', timeout: 30000 });
        await page.waitForTimeout(shot.wait);
        if (shot.scrollY > 0) {
            await page.evaluate(y => window.scrollTo(0, y), shot.scrollY);
            await page.waitForTimeout(500);
        }
        const outPath = path.join(OUT, shot.file);
        await page.screenshot({ path: outPath, fullPage: false });
        console.log(`  ✓ Saved: ${outPath}`);
        console.log(`  → ${shot.desc}`);
        await page.close();
    }

    await browser.close();
    console.log('\nAll screenshots captured.');
    console.log(`Output: ${OUT}`);
}

main().catch(e => { console.error(e); process.exit(1); });
