---
name: golocal
description: Kill, review, build, and restart AIHealthcare locally
---

# Go Local — Full Local Rebuild and Restart

Kill running app, review code for errors, build, restart locally, and verify.

## Steps — execute in order, stop on any failure

### 1. Kill running Java processes

```bash
taskkill //F //FI "IMAGENAME eq java.exe" 2>/dev/null; echo "done"
```

### 2. Review current code — compile check

Run a compile-only build to surface any errors before committing to a full build:

```bash
mvn compile -q 2>&1
```

If this produces any output, **STOP** and report every compilation error to the user. Do not proceed to the build step. List each error with file path and line number.

### 3. Run tests

```bash
mvn test 2>&1 | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | tail -10
```

If `BUILD FAILURE` appears, **STOP** and report:
- Total tests run, failures, and errors
- The failing test class and method names
- A brief explanation of what went wrong

Do not proceed to the next step.

### 4. Build the JAR (skip tests — already passed)

```bash
mvn package -DskipTests -q 2>&1 | tail -5
```

If the build fails, **STOP** and report the error.

### 5. Detect front-end changes

Before starting the app, check if any templates or static assets were modified in this session:

```bash
git diff --name-only HEAD -- '*.html' '*.css' '*.js' 2>/dev/null
git diff --name-only --cached -- '*.html' '*.css' '*.js' 2>/dev/null
git ls-files --others --exclude-standard -- '*.html' '*.css' '*.js' 2>/dev/null
```

Save the list of changed front-end files — you will report them with URLs in step 7.

### 6. Start the app locally

Source `.env` as OS environment variables first — Spring AI's auto-config resolves the
Anthropic API key at bean-creation time, before the `.env` property source is available.
Without this, all AI calls fail with HTTP 401.

Run in background with `timeout 120000`:

```bash
set -a && source .env && set +a && mvn spring-boot:run -DskipTests
```

Then wait for startup:

```bash
sleep 45 && curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/login
```

Should return `200`. If connection refused, wait 20 more seconds and retry once. If still failing after 65 seconds total, check the background task output for errors and report them.

### 7. Report results

Report a summary to the user:

**Always include:**
- Test count (e.g., "1403 tests passed")
- Build status (SUCCESS)
- App status (running at `http://localhost:8080`)
- Login URL: `http://localhost:8080/login`

**If front-end files changed (from step 5), also include a "Changed Pages" section listing the affected URLs.** Map template filenames to URLs using this table:

| Template | URL |
|----------|-----|
| `dashboard.html` | `/dashboard` |
| `articles.html` | `/dashboard/articles` |
| `news-listing.html` | `/dashboard/news` |
| `search.html` | `/dashboard/search` |
| `trends.html` | `/dashboard/trends` |
| `regulatory.html` | `/dashboard/regulatory` |
| `research-compare.html` | `/research/compare` |
| `research-runs.html` | `/research/runs` |
| `research-run-detail.html` | `/research/runs/{runId}` |
| `vendor-compare.html` | `/research/vendors` |
| `ai-search.html` | `/research/ai-search` |
| `intel-reports.html` | `/research/intel` |
| `intel-report-detail.html` | `/research/intel/{reportId}` |
| `newsletter-runs.html` | `/newsletter/runs` |
| `newsletter-edit.html` | `/newsletter/runs/{runId}/edit` |
| `wiki-index.html` | `/wiki` |
| `wiki-detail.html` | `/wiki/{slug}` |
| `wiki-contradictions.html` | `/wiki/contradictions` |
| `watchlist.html` | `/watchlist` |
| `admin.html` | `/admin` |
| `pricing.html` | `/pricing` |
| `login.html` | `/login` |
| `profile.html` | `/profile` |
| `clinical-trials.html` | `/dashboard/clinical-trials` |
| `legal-timeline.html` | `/dashboard/legal` |
| `pipeline.html` | `/admin/pipeline` |
| `nav.html` (fragment) | *(all pages — navigation changed)* |
| `head.html` (fragment) | *(all pages — head changed)* |
| `footer.html` (fragment) | *(all pages — footer changed)* |
| `tailwind.css` or other CSS | *(all pages — styles changed)* |

Prefix each URL with `http://localhost:8080` so the user can click directly.

## Notes

- App uses PostgreSQL on AWS RDS (not H2) via `.env` config
- Login: `wgblackmonall@gmail.com` or any seeded user
- Startup takes ~45-90 seconds due to topic summary generation via LLM
