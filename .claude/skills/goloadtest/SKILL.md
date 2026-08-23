# Go Load Test — k6 Smoke Test

Run the k6 load test against bigskylabs.ai to simulate concurrent authenticated users.

## Arguments (from `/goloadtest <args>`)

```
/goloadtest              → 50 VUs (default), prompts for password
/goloadtest 100          → 100 VUs, prompts for password
/goloadtest 100 mypass   → 100 VUs, password = mypass (no prompt)
```

Parse args:
- First word = VU count (integer). Default 50 if missing or not a number.
- Second word = password. If missing, ask the user before proceeding:
  "What password should k6 use to log in? (your bigskylabs.ai admin password)"

---

## Steps — execute in order, stop on any failure

### 1. Parse arguments

Extract VU count and password from the skill args.
- VU count: first token, must be a positive integer. Default 50.
- Password: second token. If absent, ask the user before continuing.

### 2. Check k6 is installed

On Windows k6 may not be on PATH even if installed:

```bash
k6 version 2>&1 || "/c/Program Files/k6/k6.exe" version 2>&1
```

Use the full path `/c/Program Files/k6/k6.exe` for all subsequent k6 commands on Windows.

If k6 is not found:
- **STOP** and tell the user:
  ```
  k6 is not installed.
  Windows:  winget install k6   (installs to C:\Program Files\k6\)
  macOS:    brew install k6
  Docs:     https://grafana.com/docs/k6/latest/get-started/installation/
  ```

### 3. Confirm the app is fully deployed and stable before running

**CRITICAL**: Never run the load test while a deployment is in progress.
If you just deployed, wait for the health check to return 200, then wait an additional
30 seconds for the app to finish its startup sequence before running k6.

```bash
curl -s -o /dev/null -w "%{http_code}" https://app.bigskylabs.ai/login
```

If not 200, **STOP** and wait for the app to come up.

### 4. Confirm target and warn about cost

Report to the user before running:

- Target: https://app.bigskylabs.ai
- VUs: <N>
- Duration: ~5 min (30s warm-up → 60s ramp → 3m hold → 30s ramp-down)
- Pages tested: dashboard, news, search, trends, deals, regulatory, wiki, admin-pipelines, REST /api/v1/articles
- Note: LLM endpoints (AI Search, frameworks, sentiment) and pipeline trigger buttons are excluded — no API cost.
- **Warning**: Do not run during a scheduled harvest window (04:00-04:30 UTC) — the background
  pipeline will compete for DB connections and CPU, invalidating the results.

### 5. Run k6

Three files are written per run to `logs/k6/` (gitignored — local only):

| File | Contents |
|------|----------|
| `run-TIMESTAMP.json` | Every metric data point as JSON Lines — one object per HTTP request with URL, group, status, duration, tags |
| `run-TIMESTAMP.log` | k6's own INFO/WARN/ERROR messages (login failures, network errors, threshold events) |
| `run-TIMESTAMP-summary.txt` | The terminal summary table (same as what prints at the end) |

```bash
TIMESTAMP=$(date +%Y-%m-%d-%H%M%S) && \
mkdir -p logs/k6 && \
"/c/Program Files/k6/k6.exe" run \
  --out "json=logs/k6/run-${TIMESTAMP}.json" \
  --log-output "file=logs/k6/run-${TIMESTAMP}.log" \
  --env MAX_VUS=<N> \
  --env K6_USERNAME=wgblackmonall@gmail.com \
  --env K6_PASSWORD=<password> \
  load-test/k6-smoke.js 2>&1 | tee "logs/k6/run-${TIMESTAMP}-summary.txt" | tail -55
```

Use `K6_USERNAME` (not `K6_PASSWORD` — Windows USERNAME env var collision: on Windows,
`__ENV.USERNAME` in k6 resolves to the Windows system variable `Administrator`, not the
login email. The script uses `K6_USERNAME` to avoid this.)

k6 streams live output to the terminal. Let it run to completion (~5 minutes).

### Reading the JSON metrics file

The JSON file has one object per line. Two line types:

- `"type":"Metric"` — declares a metric (appears once per metric name)
- `"type":"Point"` — one data point; the useful one for analysis

Each `Point` looks like:
```json
{"type":"Point","data":{"time":"2026-08-23T21:00:01Z","value":234.5,"tags":{"group":"::dashboard","url":"https://app.bigskylabs.ai/dashboard","status":"200","expected_response":"true"}},"metric":"http_req_duration"}
```

Key fields in `data`:
- `value` — response time in milliseconds (for `http_req_duration`)
- `tags.group` — which `group()` block the request came from (e.g. `::dashboard`, `::wiki-index`)
- `tags.status` — HTTP status code
- `tags.url` — full request URL
- `time` — UTC timestamp of the request

**Useful one-liners for analyzing the JSON file** (replace `RUNFILE` with the actual filename):
```bash
# Per-page average response time
grep '"type":"Point"' logs/k6/RUNFILE.json | grep '"metric":"http_req_duration"' | \
  python3 -c "import sys,json; rows=[json.loads(l) for l in sys.stdin]; \
  groups={}; \
  [groups.setdefault(r['data']['tags'].get('group','?'),[]).append(r['data']['value']) for r in rows]; \
  [print(f'{sum(v)/len(v):7.0f}ms avg  {g}') for g,v in sorted(groups.items())]"

# Any non-200 responses
grep '"type":"Point"' logs/k6/RUNFILE.json | \
  python3 -c "import sys,json; [print(r['data']['tags']) for r in (json.loads(l) for l in sys.stdin) if r.get('metric')=='http_req_failed' and r['data']['value']==1]"
```

### 6. Report results

After k6 exits, report a summary:

Always include:
- Pass/fail (did thresholds pass?)
- error_rate value vs 2% threshold
- http_req_failed rate vs 2% threshold
- page_load_ms p(95) value vs 3000 ms threshold
- Total requests and RPS
- Per-page check results (✓/✗ and pass rate)

If thresholds failed, use the diagnostic guide below.

---

## Diagnostic Guide (performance issues found in Aug 2026 load testing)

### Pattern 1: error_rate > 2% + one or more pages at 0%

**Root cause A: N+1 query problem**
- Symptom: one page consistently at 0% or low %, p(95) near the 60s timeout
- Diagnosis: count DB queries per request — if it's O(N) where N = data set size, you have N+1
- Fix: replace per-record port calls in the controller with a single bulk fetch or repository call
- Lesson learned: `WikiController.wikiIndex()` called `wikiQueryPort.getPage(slug)` for each
  of 649 wiki pages = 1,299 queries per request. Fix: single `findAllBy()` call.

**Root cause B: OSIV (Open Session In View) holding DB connections**
- Symptom: pool exhaustion under moderate concurrency (50+ VUs), slow requests across all pages
- Diagnosis: `spring.jpa.open-in-view=true` (the Spring Boot default) holds a Hibernate session
  (and therefore a Hikari connection) for the FULL HTTP request lifecycle, including Thymeleaf
  template rendering — which can be slow. With 50 VUs, all 50 connections are pinned simultaneously.
- Fix: add `spring.jpa.open-in-view: false` to `application.yml` AND `application-aws.yml`
- Also: right-size the pool — `maximum-pool-size` should be >= expected concurrent VUs

**Root cause C: Large column transfer on findAll()**
- Symptom: page works but p(95) is slow; data_received unusually high
- Diagnosis: check if findAll() returns a column containing large text (TEXT/CLOB) that is
  immediately discarded by the controller. E.g., wiki_pages.content_markdown avg 1,284 bytes
  × 649 rows = 833 KB per request; at 50 VUs that's ~40 MB/s of wasted RDS-to-app data transfer.
- Fix: Spring Data JPA interface projection — define an interface with only the getters you need,
  return `List<YourProjection>` from the repository. Spring Data generates a SELECT of only those
  columns. Keep the full-entity methods for detail pages.
  ```java
  public interface WikiPageIndexView {
      String getSlug();
      String getTitle();
      // ... only what the index page actually uses
  }
  List<WikiPageIndexView> findAllBy(Pageable pageable);
  ```

**Root cause D: CPU saturation from rendering too many items**
- Symptom: pages are fast in isolation but fail under concurrent load; no DB errors in logs
- Diagnosis: Thymeleaf/template rendering is CPU-bound. Rendering 649 cards × 50 VUs simultaneously
  saturates a small EC2 instance.
- Fix: server-side pagination. Limit the default listing to 60 items with Spring Data `Pageable`.
  Add prev/next nav to the template.

### Pattern 2: login_failures > 0

CSRF scraping failed. Check `/login` page HTML for `name="_csrf"` hidden input.
The k6 script scrapes the CSRF token via regex — if the login form structure changed, update
the regex in `load-test/k6-smoke.js`.

### Pattern 3: p(95) > 3s but error_rate = 0%

All pages are returning 200, but slowly. On a single t3.small/t3.medium EC2 under 50-VU burst
this is expected — the 3s threshold is aggressive. Focus on error_rate and per-page pass rates
as the primary health signal. p(95) threshold is aspirational for a single-node deployment.

### Pattern 4: Test invalidated by mid-test restart

If you see a burst of 4xx after many successful responses (especially 302→login page from
authenticated endpoints), the Spring Security session store was wiped (app restarted mid-test).
Discard the results and re-run after the app is stable.

---

## EC2 log command (if needed)

```bash
ssh -i "C:/workspaces/SpringAIClaude/N_VaKeyPair.pem" "ec2-user@100.61.13.237" \
  'sudo journalctl -u aihealthcare --no-pager -n 50'
```

Filter for errors only during a specific window:
```bash
ssh -i "C:/workspaces/SpringAIClaude/N_VaKeyPair.pem" "ec2-user@100.61.13.237" \
  'sudo journalctl -u aihealthcare --no-pager --since "2026-08-23 17:37:00" | grep -v "DEBUG\|TRACE\|INFO" | tail -30'
```

---

## Baseline results (Aug 2026, after all fixes)

| Metric | Before fixes | After fixes |
|--------|-------------|-------------|
| wiki pass rate | 2% → 0% → 100% | 100% |
| error_rate | 9.38% | 0.00% |
| http_req_failed | 5.47% | 0.00% |
| Total requests | ~550 | 2,068 |
| DB queries per /wiki | 1,299 | 1 |

Fixes applied (in order):
1. N+1 elimination — `WikiController` loop no longer calls `wikiQueryPort.getPage()` per entity
2. Hikari pool 25 → 50
3. OSIV disabled — `spring.jpa.open-in-view: false`
4. JPA projections — `WikiPageIndexView` excludes `content_markdown`
5. Wiki pagination — 60 pages/page with `Pageable`
6. DB indexes — `news_articles(topic, published_at, url)`, `wiki_source_refs(page_slug)`,
   `wiki_contradictions(page_slug)`, `wiki_pages(page_type, updated_at)`
