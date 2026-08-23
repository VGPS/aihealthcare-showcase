/**
 * k6 load test — AIHealthcare / bigskylabs.ai
 *
 * Simulates authenticated subscriber sessions hitting the pages
 * real users actually visit. Does NOT call LLM endpoints (AI Search,
 * framework analysis) — those have intentional rate limits and per-call cost.
 *
 * Usage:
 *   k6 run load-test/k6-smoke.js                   # default: 50 VUs against EC2
 *   k6 run --env BASE_URL=http://localhost:8080 \
 *          --env USERNAME=wgblackmonall@gmail.com \
 *          --env PASSWORD=yourpassword \
 *          load-test/k6-smoke.js
 *
 * Install k6 (Windows): winget install k6
 * Install k6 (macOS):   brew install k6
 * Docs: https://grafana.com/docs/k6/latest/
 *
 * Thresholds (hard stop criteria):
 *   - 95th-percentile response time < 3 s
 *   - Error rate < 2 %
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

// ---------------------------------------------------------------------------
// Config — override via --env flags
// ---------------------------------------------------------------------------
const BASE_URL  = __ENV.BASE_URL  || 'https://app.bigskylabs.ai';
const USERNAME  = __ENV.USERNAME  || 'wgblackmonall@gmail.com';
const PASSWORD  = __ENV.PASSWORD  || 'changeme';
const MAX_VUS   = parseInt(__ENV.MAX_VUS) || 50;  // override: --env MAX_VUS=100

// ---------------------------------------------------------------------------
// Custom metrics
// ---------------------------------------------------------------------------
const pageLoadTime = new Trend('page_load_ms', true);
const errorRate    = new Rate('error_rate');
const loginFails   = new Counter('login_failures');

// ---------------------------------------------------------------------------
// Load shape — ramp to MAX_VUS, hold 3 min, ramp down
// ---------------------------------------------------------------------------
const warmupVus = Math.max(1, Math.round(MAX_VUS * 0.2));  // 20% of target for warm-up

export const options = {
    stages: [
        { duration: '30s', target: warmupVus },  // warm up to 20% of target
        { duration: '60s', target: MAX_VUS   },  // ramp to full load
        { duration: '3m',  target: MAX_VUS   },  // hold — this is your "under load" window
        { duration: '30s', target: 0         },  // ramp down
    ],
    thresholds: {
        'page_load_ms':  ['p(95)<3000'],  // 95th percentile under 3 s
        'error_rate':    ['rate<0.02'],   // less than 2% errors
        'http_req_failed': ['rate<0.02'],
    },
};

// ---------------------------------------------------------------------------
// Session helper — login once per VU, reuse cookie jar
// ---------------------------------------------------------------------------
function login(jar) {
    // Step 1: GET /login to scrape the CSRF token
    const loginPage = http.get(`${BASE_URL}/login`, { jar });
    const csrfMatch = loginPage.body.match(/name="_csrf"\s+value="([^"]+)"/);
    if (!csrfMatch) {
        loginFails.add(1);
        console.error('CSRF token not found on /login page');
        return false;
    }
    const csrfToken = csrfMatch[1];

    // Step 2: POST credentials
    const loginResp = http.post(
        `${BASE_URL}/login`,
        { username: USERNAME, password: PASSWORD, _csrf: csrfToken },
        { jar, redirects: 5 }
    );

    const ok = loginResp.status === 200 && !loginResp.url.includes('/login?error');
    if (!ok) {
        loginFails.add(1);
        console.error(`Login failed — status=${loginResp.status} url=${loginResp.url}`);
    }
    return ok;
}

// ---------------------------------------------------------------------------
// Main VU function
// ---------------------------------------------------------------------------
export default function () {
    const jar = http.cookieJar();

    // Each VU logs in once at the start of its lifetime
    if (!login(jar)) {
        sleep(5);
        return;
    }

    const params = { jar, tags: { test: 'auth' } };

    // --- Page tour: simulate a real user browsing session ---

    group('dashboard', () => {
        const r = http.get(`${BASE_URL}/dashboard`, params);
        pageLoadTime.add(r.timings.duration, { page: 'dashboard' });
        errorRate.add(r.status !== 200);
        check(r, { 'dashboard 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 3));
    });

    group('news-listing', () => {
        const r = http.get(`${BASE_URL}/dashboard/news`, params);
        pageLoadTime.add(r.timings.duration, { page: 'news' });
        errorRate.add(r.status !== 200);
        check(r, { 'news 200': (res) => res.status === 200 });
        sleep(randomBetween(2, 5));
    });

    group('article-search', () => {
        const r = http.get(`${BASE_URL}/dashboard/search?topic=&keyword=AI&sourceTier=&dateRange=&sort=date`, params);
        pageLoadTime.add(r.timings.duration, { page: 'search' });
        errorRate.add(r.status !== 200);
        check(r, { 'search 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 4));
    });

    group('trends', () => {
        const r = http.get(`${BASE_URL}/dashboard/trends`, params);
        pageLoadTime.add(r.timings.duration, { page: 'trends' });
        errorRate.add(r.status !== 200);
        check(r, { 'trends 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 3));
    });

    group('deals', () => {
        const r = http.get(`${BASE_URL}/dashboard/deals`, params);
        pageLoadTime.add(r.timings.duration, { page: 'deals' });
        errorRate.add(r.status !== 200);
        check(r, { 'deals 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 3));
    });

    group('regulatory', () => {
        const r = http.get(`${BASE_URL}/dashboard/regulatory`, params);
        pageLoadTime.add(r.timings.duration, { page: 'regulatory' });
        errorRate.add(r.status !== 200);
        check(r, { 'regulatory 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 3));
    });

    group('wiki-index', () => {
        const r = http.get(`${BASE_URL}/wiki`, params);
        pageLoadTime.add(r.timings.duration, { page: 'wiki' });
        errorRate.add(r.status !== 200);
        check(r, { 'wiki 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 2));
    });

    group('rest-articles', () => {
        const r = http.get(`${BASE_URL}/api/v1/articles?limit=20`, params);
        pageLoadTime.add(r.timings.duration, { page: 'api-articles' });
        errorRate.add(r.status !== 200);
        check(r, { 'api articles 200': (res) => res.status === 200 });
        sleep(randomBetween(1, 2));
    });

    // Pause between full page tours — simulates real dwell time
    sleep(randomBetween(3, 8));
}

function randomBetween(min, max) {
    return Math.random() * (max - min) + min;
}
