/**
 * Enterprise access regression tests — confirms that the admin user
 * (wgblackmonall@gmail.com) never sees an upgrade wall on any
 * enterprise-gated page.
 *
 * Tests cover every page that previously showed
 * "Enterprise Feature / Upgrade to unlock this analysis" incorrectly.
 *
 * Run locally:  npx playwright test --project=enterprise-access
 * Run on prod:  BASE_URL=https://app.bigskylabs.ai npx playwright test
 *
 * @author  Bill Blackmon
 * @since   2026-09-23
 */
const { test, expect } = require('@playwright/test');

// ── helpers ────────────────────────────────────────────────────────────────

/** Assert neither upgrade wall appears anywhere on the current page. */
async function expectNoUpgradeWall(page) {
    await expect(page.locator('body')).not.toContainText('Enterprise Feature');
    await expect(page.locator('body')).not.toContainText('Upgrade to unlock');
    await expect(page.locator('body')).not.toContainText('View Enterprise Plans');
    await expect(page.locator('body')).not.toContainText('Start 7-Day Trial');
}

/** Assert the page loaded (200) and does not show an error / 404. */
async function expectPageLoaded(page) {
    await expect(page.locator('body')).not.toContainText('Page Not Found');
    await expect(page.locator('body')).not.toContainText('Whitelabel Error Page');
    await expect(page.locator('body')).not.toContainText('There was an unexpected error');
}

// ── deal signals ───────────────────────────────────────────────────────────

test('deal list page loads without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/deals');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    // List page should render the deal signals table or "no signals" message
    await expect(page.locator('body')).toContainText(/deal|signal|acquisition|funding|partnership/i);
});

test('deal detail page opens without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/deals');
    await expectPageLoaded(page);

    // If any deal rows exist, click the first clickable deal title
    const dealLink = page.locator('a[href*="/dashboard/deals/"]').first();
    const hasDeals = await dealLink.count() > 0;

    if (hasDeals) {
        await dealLink.click();
        await page.waitForLoadState('networkidle');
        await expectPageLoaded(page);
        await expectNoUpgradeWall(page);
        // Detail page should show deal content, not a gate
        await expect(page.locator('main')).toContainText(/deal|analysis|company|signal/i);
    } else {
        // No deals in DB — at minimum the list page itself must not show a gate
        await expectNoUpgradeWall(page);
        test.info().annotations.push({ type: 'warning', description: 'No deal rows found — DB may be empty' });
    }
});

// ── framework analysis ─────────────────────────────────────────────────────

test('framework analysis list page loads without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/frameworks');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    await expect(page.locator('body')).toContainText(/framework|analysis|competitive|company/i);
});

test('framework detail page opens without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/frameworks');
    await expectPageLoaded(page);

    const detailLink = page.locator('a[href*="/dashboard/frameworks/"]').first();
    const hasFrameworks = await detailLink.count() > 0;

    if (hasFrameworks) {
        await detailLink.click();
        await page.waitForLoadState('networkidle');
        await expectPageLoaded(page);
        await expectNoUpgradeWall(page);
        await expect(page.locator('main')).toContainText(/dimension|score|strength|weakness/i);
    } else {
        await expectNoUpgradeWall(page);
        test.info().annotations.push({ type: 'warning', description: 'No framework rows found — analysis may not have run yet' });
    }
});

// ── sentiment & risk ───────────────────────────────────────────────────────

test('sentiment risk dashboard loads without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/risk');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    await expect(page.locator('body')).toContainText(/sentiment|risk|company/i);
});

test('sentiment risk detail page opens without upgrade wall', async ({ page }) => {
    await page.goto('/dashboard/risk');
    await expectPageLoaded(page);

    const detailLink = page.locator('a[href*="/dashboard/risk/"]').first();
    const hasSentiment = await detailLink.count() > 0;

    if (hasSentiment) {
        await detailLink.click();
        await page.waitForLoadState('networkidle');
        await expectPageLoaded(page);
        await expectNoUpgradeWall(page);
        await expect(page.locator('main')).toContainText(/sentiment|article|score/i);
    } else {
        await expectNoUpgradeWall(page);
        test.info().annotations.push({ type: 'warning', description: 'No sentiment rows found — analysis may not have run yet' });
    }
});

// ── ai search ─────────────────────────────────────────────────────────────

test('AI search page loads the search form (no access denied)', async ({ page }) => {
    await page.goto('/research/ai-search');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    // Should see the search form, not an access-denied banner
    await expect(page.locator('body')).not.toContainText('accessDenied');
    await expect(page.locator('input[name="q"], input[placeholder*="search" i]')).toBeVisible();
});

// ── company articles topic ─────────────────────────────────────────────────

test('"New AI Healthcare Companies" articles topic loads without access denied', async ({ page }) => {
    await page.goto('/dashboard/articles?topic=New+AI+Healthcare+Companies');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    // Should see articles or an empty state — not an upgrade/access wall
    await expect(page.locator('body')).not.toContainText('accessDenied');
    await expect(page.locator('body')).not.toContainText('upgrade required');
});

// ── profile tier display ──────────────────────────────────────────────────

test('profile page shows ENTERPRISE tier for admin (not SUBSCRIBER)', async ({ page }) => {
    await page.goto('/profile');
    await expectPageLoaded(page);
    // Tier badge should read ENTERPRISE, not SUBSCRIBER or FREE
    await expect(page.locator('body')).toContainText(/enterprise/i);
    await expect(page.locator('body')).not.toContainText(/subscriber\s+tier|tier.*subscriber/i);
});

// ── pages that must always be accessible to admin ─────────────────────────

test('market digest page loads for admin', async ({ page }) => {
    await page.goto('/dashboard/market');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
});

test('trends page loads for admin', async ({ page }) => {
    await page.goto('/dashboard/trends');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
});

test('watchlist page loads for admin', async ({ page }) => {
    await page.goto('/watchlist');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
});

test('regulatory page loads for admin', async ({ page }) => {
    await page.goto('/dashboard/regulatory');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
});

test('admin panel loads for admin', async ({ page }) => {
    await page.goto('/admin');
    await expectPageLoaded(page);
    await expectNoUpgradeWall(page);
    // Admin panel should show user management content
    await expect(page.locator('body')).toContainText(/user|admin|system/i);
});
