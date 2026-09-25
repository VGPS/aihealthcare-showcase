/**
 * Walks every top-level nav menu page as the admin user, screenshots each
 * one, and (if ffmpeg is installed) stitches the screenshots into an MP4
 * slideshow. Output lives alongside other marketing/social assets in
 * linkedin-posts/.
 *
 * Reuses the login flow from e2e/admin.setup.js but keeps its own saved
 * session (.auth/admin.json under this folder) so it's fully self-contained
 * and doesn't depend on the e2e/ test suite.
 *
 * Page list is hand-maintained from templates/fragments/nav.html — update
 * both together when the nav changes.
 *
 * Usage (run from repo root):
 *   npx playwright install chromium   (one-time, if not already installed)
 *   node linkedin-posts/slideshow/slideshow.js                            # localhost:8080
 *   BASE_URL=https://app.bigskylabs.ai node linkedin-posts/slideshow/slideshow.js
 *   SLIDE_SECONDS=4 node linkedin-posts/slideshow/slideshow.js            # slower pacing
 *
 * Requires E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD in .env.
 * ffmpeg is optional — if it's not on PATH, screenshots are still produced
 * and the script prints the ffmpeg command to run manually later.
 *
 * @author  Bill Blackmon
 * @since   2026-09-25
 */
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

try { require('dotenv').config(); } catch (_) {}

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const SLIDE_SECONDS = Number(process.env.SLIDE_SECONDS || 1.75);
const STORAGE_STATE = path.join(__dirname, '.auth', 'subscriber.json');
const FRAMES_DIR = path.join(__dirname, 'frames');
const OUTPUT_DIR = path.join(__dirname, 'output');
const OUTPUT_FILE = path.join(OUTPUT_DIR, 'app-walkthrough.mp4');

// Mirrors templates/fragments/nav.html, in visual left-to-right / dropdown order.
const PAGES = [
    { group: 'Dashboard', label: 'Dashboard', path: '/dashboard' },

    { group: 'Content', label: 'Deal Signals', path: '/dashboard/deals' },
    { group: 'Content', label: 'Market Digest', path: '/dashboard/market' },
    { group: 'Content', label: 'Market Enrichment', path: '/dashboard/market/enrichment' },
    { group: 'Content', label: 'Market History', path: '/dashboard/market/history' },
    { group: 'Content', label: 'News Listing', path: '/dashboard/news' },
    { group: 'Content', label: 'Trend History', path: '/dashboard/trends/history' },
    { group: 'Content', label: 'Trends', path: '/dashboard/trends' },

    { group: 'Research', label: 'AI Search', path: '/research/ai-search' },
    { group: 'Research', label: 'Find Articles', path: '/dashboard/search' },
    { group: 'Research', label: 'Framework Analysis', path: '/dashboard/frameworks' },
    { group: 'Research', label: 'Intel Reports', path: '/research/intel' },
    { group: 'Research', label: 'Research Runs', path: '/research/runs' },
    { group: 'Research', label: 'Vendor Compare', path: '/research/vendors' },

    { group: 'Legal', label: 'Legal Timeline', path: '/dashboard/legal' },
    { group: 'Legal', label: 'Legal Trends', path: '/dashboard/legal/trends' },
    { group: 'Legal', label: 'Regulatory', path: '/dashboard/regulatory' },
    { group: 'Legal', label: 'Legislation', path: '/legislation' },

    { group: 'Reference', label: 'Clinical Trials', path: '/dashboard/clinical-trials' },
    { group: 'Reference', label: 'Companies', path: '/dashboard/companies' },
    { group: 'Reference', label: 'Company Directory', path: '/directory' },
    { group: 'Reference', label: 'Relationships', path: '/dashboard/relationships' },
    { group: 'Reference', label: 'Sentiment & Risk', path: '/dashboard/risk' },
    { group: 'Reference', label: 'Wiki', path: '/wiki' },

    { group: 'Account', label: 'Social Posts', path: '/dashboard/social' },
    { group: 'Account', label: 'Weekly Roundup', path: '/dashboard/weekly-roundup' },
    { group: 'Account', label: 'About', path: '/about' },
    { group: 'Account', label: 'Notes', path: '/notes' },
    { group: 'Account', label: 'Pricing', path: '/pricing' },
    { group: 'Account', label: 'Watchlist', path: '/watchlist' },
    { group: 'Account', label: 'Developer', path: '/developer' },
    { group: 'Account', label: 'Profile', path: '/profile' },
    { group: 'Account', label: 'Webhooks', path: '/settings/webhooks' },
    // Admin-only pages (Pipelines, Admin, Newsletter Runs, Document Library,
    // Wiki Gaps, SSO Providers) are intentionally excluded — a SUBSCRIBER
    // account never sees them in nav, and a customer-facing demo shouldn't
    // show internal ops tooling.
];

function slugify(label) {
    return label.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

async function ensureLoggedIn(browser) {
    if (fs.existsSync(STORAGE_STATE)) {
        console.log(`Reusing saved session: ${STORAGE_STATE}`);
        return browser.newContext({ storageState: STORAGE_STATE, viewport: { width: 1920, height: 1080 } });
    }

    // Deliberately NOT the admin account — this captures what a paying
    // SUBSCRIBER sees, since the resulting video is meant for prospects.
    const email = process.env.SLIDESHOW_EMAIL || 'showcase-subscriber@bigskylabs.ai';
    const password = process.env.SLIDESHOW_PASSWORD;
    if (!password) {
        throw new Error(
            'No saved session and SLIDESHOW_PASSWORD is not set.\n' +
            'Add SLIDESHOW_PASSWORD=<subscriber password> to your .env file (quote it if it contains "#").'
        );
    }

    console.log('No saved session found — logging in fresh...');
    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
    const page = await context.newPage();
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', email);
    await page.fill('input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 10_000 });
    await page.close();

    fs.mkdirSync(path.dirname(STORAGE_STATE), { recursive: true });
    await context.storageState({ path: STORAGE_STATE });
    console.log(`Session saved: ${STORAGE_STATE}`);
    return context;
}

async function captureFrames(context) {
    fs.rmSync(FRAMES_DIR, { recursive: true, force: true });
    fs.mkdirSync(FRAMES_DIR, { recursive: true });

    const page = await context.newPage();
    page.setDefaultTimeout(20_000);

    let frame = 0;
    const skipped = [];

    for (const item of PAGES) {
        try {
            const response = await page.goto(`${BASE_URL}${item.path}`, { waitUntil: 'domcontentloaded' });
            await page.waitForTimeout(1200); // let charts/Alpine/Chart.js settle

            const status = response ? response.status() : 0;
            const bodyText = await page.locator('body').innerText().catch(() => '');
            const looksBroken = status >= 400
                || bodyText.includes('Whitelabel Error Page')
                || bodyText.includes('There was an unexpected error');

            if (looksBroken) {
                console.warn(`  SKIP  ${item.path}  (status=${status})`);
                skipped.push({ ...item, status });
                continue;
            }

            await page.evaluate(({ text }) => {
                const bar = document.createElement('div');
                bar.textContent = text;
                Object.assign(bar.style, {
                    position: 'fixed', left: '0', right: '0', bottom: '0',
                    background: 'rgba(15,23,42,0.85)', color: '#fff',
                    font: '600 22px -apple-system, Segoe UI, sans-serif',
                    padding: '14px 24px', zIndex: 2147483647,
                });
                document.body.appendChild(bar);
            }, { text: `${item.group} — ${item.label}  (${item.path})` });

            frame += 1;
            const file = path.join(FRAMES_DIR, `${String(frame).padStart(3, '0')}-${slugify(item.label)}.png`);
            await page.screenshot({ path: file, fullPage: false });
            console.log(`  OK    ${item.path}  -> ${path.basename(file)}`);
        } catch (err) {
            console.warn(`  SKIP  ${item.path}  (${err.message.split('\n')[0]})`);
            skipped.push({ ...item, status: 'error' });
        }
    }

    await page.close();
    return { captured: frame, total: PAGES.length, skipped };
}

function buildVideo() {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    try {
        execFileSync('ffmpeg', ['-version'], { stdio: 'ignore' });
    } catch (_) {
        console.log('\nffmpeg not found on PATH — skipping video assembly.');
        console.log('Install it (e.g. `winget install ffmpeg` or `choco install ffmpeg`), then run:');
        console.log(`  ffmpeg -y -framerate 1/${SLIDE_SECONDS} -i "${FRAMES_DIR}\\%03d-*.png" -vf "scale=1920:1080,format=yuv420p" -r 30 "${OUTPUT_FILE}"`);
        console.log('(ffmpeg\'s %03d pattern needs a plain numeric glob — see note below the summary.)');
        return false;
    }

    // ffmpeg's image2 demuxer needs a strict numeric pattern, not a glob with
    // the label suffix, so build a concat list instead — works with any filenames.
    const files = fs.readdirSync(FRAMES_DIR).filter((f) => f.endsWith('.png')).sort();
    const listPath = path.join(FRAMES_DIR, 'concat-list.txt');
    const lines = files.map((f) => `file '${path.join(FRAMES_DIR, f).replace(/'/g, "'\\''")}'\nduration ${SLIDE_SECONDS}`);
    // ffmpeg concat demuxer requires the last file listed twice (duration of last entry is otherwise ignored)
    if (files.length > 0) lines.push(`file '${path.join(FRAMES_DIR, files[files.length - 1]).replace(/'/g, "'\\''")}'`);
    fs.writeFileSync(listPath, lines.join('\n'));

    console.log('\nAssembling MP4 with ffmpeg...');
    execFileSync('ffmpeg', [
        '-y', '-f', 'concat', '-safe', '0', '-i', listPath,
        '-vf', 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,format=yuv420p',
        '-r', '30', OUTPUT_FILE,
    ], { stdio: 'inherit' });

    console.log(`\nSlideshow written to: ${OUTPUT_FILE}`);
    return true;
}

(async () => {
    // SKIP_CAPTURE=1 re-encodes the video from already-captured frames
    // (e.g. after changing SLIDE_SECONDS) without re-scraping every page.
    if (process.env.SKIP_CAPTURE) {
        console.log('SKIP_CAPTURE set — re-encoding existing frames only.\n');
        buildVideo();
        return;
    }

    console.log(`Base URL: ${BASE_URL}`);
    console.log(`Pages to capture: ${PAGES.length}\n`);

    const browser = await chromium.launch();
    const context = await ensureLoggedIn(browser);

    const result = await captureFrames(context);
    await browser.close();

    console.log(`\nCaptured ${result.captured}/${result.total} pages.`);
    if (result.skipped.length > 0) {
        console.log('Skipped:');
        for (const s of result.skipped) console.log(`  - ${s.path} (${s.status})`);
    }

    buildVideo();
})().catch((err) => {
    console.error('\nslideshow.js failed:', err.message);
    process.exit(1);
});
