/**
 * Captures the Frontier Claim Tracker dashboard as a 7 Day Trial (DEMO) user
 * and surfaces the latest Marketing Hype claim + running tally for post use.
 *
 * Outputs produced (both date-stamped with today's date):
 *   1. linkedin-posts/shots/YYYY-MM-DD-claims-dashboard-demo.png
 *      Full-page screenshot, All Verdicts / All Types, no Admin Actions column.
 *
 *   2. linkedin-posts/text/YYYY-MM-DD-claim-of-the-week.txt
 *      Pre-formatted "Claim of the Week" section ready to paste into any
 *      LinkedIn post body, LinkedIn comment, or Substack post. Includes:
 *        - The latest Marketing Hype claim (company, text, type, source)
 *        - Running tally: total Marketing Hype flags since tracking began
 *        - Total claims rated and tracker URL
 *
 * Login strategy: uses admin credentials but injects CSS to hide the
 * admin-only Actions/Generate Post column — accurate DEMO tier rendering.
 *
 * Usage:
 *   node e2e/capture-claims-dashboard.js
 *
 * @author  Bill Blackmon
 * @since   2026-10-06
 * @updated 2026-10-06
 */

const { chromium } = require('@playwright/test');
const path = require('path');
const fs   = require('fs');

try { require('dotenv').config(); } catch (_) {}

const BASE_URL   = process.env.BASE_URL            || 'https://app.bigskylabs.ai';
const ADMIN_EMAIL    = process.env.E2E_ADMIN_EMAIL    || 'wgblackmonall@gmail.com';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD;

// Date stamp for filenames — YYYY-MM-DD in local time
const TODAY = new Date().toLocaleDateString('en-CA'); // 'en-CA' gives ISO YYYY-MM-DD

const SHOTS_DIR = path.resolve(__dirname, '../linkedin-posts/shots');
const TEXT_DIR  = path.resolve(__dirname, '../linkedin-posts/text');

(async () => {
    if (!ADMIN_PASSWORD) {
        console.error('ERROR: E2E_ADMIN_PASSWORD not set in .env');
        process.exit(1);
    }

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page    = await context.newPage();

    // ── Login ──────────────────────────────────────────────────────────────────
    console.log(`[Login] ${ADMIN_EMAIL}`);
    await page.goto(`${BASE_URL}/login`);
    await page.fill('input[name="username"]', ADMIN_EMAIL);
    await page.fill('input[name="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL(url => !url.toString().includes('/login'), { timeout: 15_000 });
    console.log('  OK\n');

    // ── Step 1: Full tracker — default view (all verdicts, all types) ──────────
    console.log('[Step 1] Claims dashboard — 7-Day Trial view');
    await page.goto(`${BASE_URL}/dashboard/claims`, { waitUntil: 'networkidle', timeout: 30_000 });
    await page.waitForTimeout(1500);

    // Hide Admin-only Actions column so screenshot matches DEMO tier exactly
    await page.addStyleTag({
        content: 'th:last-child, td:last-child { display: none !important; }'
    });

    // Confirm no Generate Post buttons visible
    const adminButtons = await page.locator('button:visible:has-text("Generate Post")').count();
    console.log(adminButtons === 0
        ? '  CONFIRMED: Actions column hidden — DEMO view active.'
        : `  WARNING: ${adminButtons} Generate Post button(s) still visible`);

    // ── Extract running tallies from summary pills ─────────────────────────────
    // Pills are rendered as colored spans containing the count followed by the label.
    // We read all pill containers and extract by label text.
    const pillData = await page.evaluate(() => {
        const result = {};
        // Each pill is a div.bg-*-50 containing a <span> count and a label span
        document.querySelectorAll('[class*="bg-"][class*="-50"]').forEach(el => {
            const text = el.innerText.trim();
            // e.g. "24\nAlleged/Unverified" or "1\nMarketing hype"
            const parts = text.split(/\s+/);
            if (parts.length >= 2 && /^\d+$/.test(parts[0])) {
                const count = parseInt(parts[0], 10);
                const label = parts.slice(1).join(' ').toLowerCase();
                result[label] = count;
            }
        });
        return result;
    });
    console.log('  Summary pills:', JSON.stringify(pillData));

    const totalClaims     = Object.values(pillData).reduce((a, b) => a + b, 0) || 0;
    const marketingHypeCount = pillData['marketing hype'] || pillData['marketing_hype'] || 0;
    const evidenceCount   = pillData['evidence-backed']   || pillData['evidence backed'] || 0;
    const contradictedCount = pillData['contradicted']    || 0;
    const retractedCount  = pillData['retracted']         || 0;

    // Fall back to row count if pill extraction yielded nothing
    const claimRows = await page.locator('table tbody tr').count();
    const resolvedTotal = totalClaims > 0 ? totalClaims : claimRows;
    console.log(`  Rows: ${claimRows}  |  Total from pills: ${resolvedTotal}`);

    // ── Screenshot: full-page default view ────────────────────────────────────
    const shotFile = path.join(SHOTS_DIR, `${TODAY}-claims-dashboard-demo.png`);
    await page.screenshot({ path: shotFile, fullPage: true });
    console.log(`  Screenshot → ${path.basename(shotFile)}\n`);

    // ── Step 2: Filter to Marketing Hype — extract top claim ──────────────────
    console.log('[Step 2] Extracting latest Marketing Hype claim');
    await page.goto(
        `${BASE_URL}/dashboard/claims?filterVerdict=MARKETING_HYPE`,
        { waitUntil: 'networkidle', timeout: 30_000 }
    );
    await page.waitForTimeout(1000);

    // Hide Actions column on filtered view too
    await page.addStyleTag({
        content: 'th:last-child, td:last-child { display: none !important; }'
    });

    // Extract first row: company · claim text · claim type · source URL
    const topHype = await page.evaluate(() => {
        const rows = document.querySelectorAll('table tbody tr');
        if (!rows.length) return null;
        const cells = rows[0].querySelectorAll('td');
        // Column order (Actions hidden): COMPANY · VERDICT · TYPE · CLAIM · DETECTED · SOURCE
        const company   = cells[0] ? cells[0].innerText.trim() : '';
        const claimType = cells[2] ? cells[2].innerText.trim() : '';
        // Claim cell may contain the text + a "Notes ↓" sub-row link
        const claimCell = cells[3] ? cells[3].innerText.trim() : '';
        const claimText = claimCell.split('\n')[0].trim(); // first line only
        const detected  = cells[4] ? cells[4].innerText.trim() : '';
        const sourceLink = cells[5] ? cells[5].querySelector('a')?.href || '' : '';
        const sourceLabel = cells[5] ? cells[5].innerText.trim() : '';
        return { company, claimType, claimText, detected, sourceLink, sourceLabel };
    });

    if (topHype) {
        console.log(`  Company   : ${topHype.company}`);
        console.log(`  Type      : ${topHype.claimType}`);
        console.log(`  Claim     : ${topHype.claimText.substring(0, 80)}...`);
        console.log(`  Detected  : ${topHype.detected}`);
        console.log(`  Source    : ${topHype.sourceLink}`);
    } else {
        console.log('  No Marketing Hype claims found.');
    }

    await browser.close();

    // ── Step 3: Write claim-of-the-week.txt ───────────────────────────────────
    console.log('\n[Step 3] Writing Claim of the Week post block');

    const noHype = !topHype || !topHype.company;

    const claimBlock = noHype
        ? `[No new Marketing Hype claims detected as of ${TODAY}. Use the most recent entry from the tracker.]`
        : `---

This week's Marketing Hype flag — #${marketingHypeCount} since tracking began.

The claim: "${topHype.claimText}"

Company: ${topHype.company}
Claim type: ${topHype.claimType}
Detected: ${topHype.detected}
${topHype.sourceLink ? `Source: ${topHype.sourceLink}` : ''}

The Frontier AI Claim Tracker has now rated ${resolvedTotal} healthcare AI claims from frontier AI companies.

→ ${evidenceCount} evidence-backed
→ ${marketingHypeCount} Marketing Hype — flagged for using regulatory or superlative language without supporting data
→ ${contradictedCount} contradicted by published evidence
→ ${retractedCount} retracted by the company

The full record: https://app.bigskylabs.ai/dashboard/claims
Methodology and dispute process: https://app.bigskylabs.ai/claims/methodology

---`;

    const tallySentence = noHype
        ? `The Frontier AI Claim Tracker has rated ${resolvedTotal} healthcare AI claims. ${marketingHypeCount} flagged as Marketing Hype since tracking began.`
        : `The Frontier AI Claim Tracker has now flagged ${marketingHypeCount} Marketing Hype claim${marketingHypeCount === 1 ? '' : 's'} out of ${resolvedTotal} healthcare AI claims rated since tracking began.`;

    const outputText = `=== CLAIM OF THE WEEK — ${TODAY} ===
Generated by: node e2e/capture-claims-dashboard.js

== RUNNING TALLY (paste into every post — update the numbers as they grow) ==

${tallySentence}

== CLAIM OF THE WEEK BLOCK (paste into post body after main content) ==

${claimBlock}

== POST BODY SNIPPET — LinkedIn / Substack ==
(Insert this block before the closing CTA in any post published this week)

${claimBlock}

== FIRST COMMENT SNIPPET ==
The Frontier AI Claim Tracker — ${resolvedTotal} claims rated, updated weekly:
https://app.bigskylabs.ai/dashboard/claims

Methodology and dispute process: https://app.bigskylabs.ai/claims/methodology

== SCREENSHOT ==
File: linkedin-posts/shots/${TODAY}-claims-dashboard-demo.png
Use as: Post body image when the post is primarily about the tracker.
        Optional secondary image in first comment for other post topics.

== NOTES FOR FUTURE POSTS ==
Marketing Hype count to watch:
  - At 10 flags: publish "10 Healthcare AI Claims That Couldn't Clear an Evidence Bar"
  - At 25 flags: publish pattern analysis — which language constructs predict a Hype verdict
  - First CONTRADICTED: standalone post — name the company and the contradicting study
  - First RETRACTED: standalone post — what was claimed, what changed, what the record shows

The running count is the story. Update it in every post.
`;

    const textFile = path.join(TEXT_DIR, `${TODAY}-claim-of-the-week.txt`);
    fs.writeFileSync(textFile, outputText, 'utf8');
    console.log(`  Written  → ${path.basename(textFile)}`);

    // ── Summary ───────────────────────────────────────────────────────────────
    console.log('\n=== Done ===');
    console.log(`  Screenshot : linkedin-posts/shots/${TODAY}-claims-dashboard-demo.png`);
    console.log(`  Post block : linkedin-posts/text/${TODAY}-claim-of-the-week.txt`);
    console.log(`  Total rated: ${resolvedTotal}  |  Mktg Hype: ${marketingHypeCount}  |  Evidence-backed: ${evidenceCount}  |  Contradicted: ${contradictedCount}`);
})();
