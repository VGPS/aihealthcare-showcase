/**
 * Shared utility — fetches the live company count from the public API endpoint.
 *
 * Usage in any Playwright or post-generation script:
 *   const { getCompanyCount } = require('./get-company-count');
 *   const count = await getCompanyCount();   // e.g. 627
 *   const label = `${count}+ AI healthcare companies`;
 *
 * Falls back to null if the endpoint is unreachable, so callers can gracefully
 * degrade (e.g. omit the count or use a hardcoded floor).
 */

const https = require('https');

const COUNT_URL = 'https://app.bigskylabs.ai/api/v1/companies/count';

/**
 * @returns {Promise<number|null>} live directory count, or null on error
 */
function getCompanyCount() {
    return new Promise((resolve) => {
        https.get(COUNT_URL, (res) => {
            let data = '';
            res.on('data', chunk => { data += chunk; });
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    resolve(typeof json.count === 'number' ? json.count : null);
                } catch {
                    resolve(null);
                }
            });
        }).on('error', () => resolve(null));
    });
}

/**
 * Returns a formatted label like "627+ AI healthcare companies".
 * Uses the provided fallback string if the API is unreachable.
 *
 * @param {string} [fallback="600+ AI healthcare companies"]
 * @returns {Promise<string>}
 */
async function getCompanyCountLabel(fallback = '600+ AI healthcare companies') {
    const count = await getCompanyCount();
    return count !== null ? `${count}+ AI healthcare companies` : fallback;
}

module.exports = { getCompanyCount, getCompanyCountLabel };
