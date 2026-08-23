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

```bash
k6 version 2>&1
```

If the command fails or returns "not found":
- **STOP** and tell the user:
  ```
  k6 is not installed.
  Windows:  winget install k6
  macOS:    brew install k6
  Docs:     https://grafana.com/docs/k6/latest/get-started/installation/
  ```

### 3. Confirm target and warn about cost

Report to the user before running:

- Target: https://app.bigskylabs.ai
- VUs: <N>
- Duration: ~5 min (30s warm-up → 60s ramp → 3m hold → 30s ramp-down)
- Pages tested: dashboard, news, search, trends, deals, regulatory, wiki, REST /api/v1/articles
- Note: LLM endpoints (AI Search, frameworks, sentiment) are excluded — no API cost.

### 4. Run k6

```bash
k6 run --env MAX_VUS=<N> --env K6_PASSWORD=<password> load-test/k6-smoke.js
```

k6 streams live output to the terminal. Let it run to completion (~5 minutes).

### 5. Report results

After k6 exits, report a summary:

Always include:
- Pass/fail (did thresholds pass?)
- page_load_ms p(95) value vs 3000 ms threshold
- http_req_failed rate vs 2% threshold
- Total requests and RPS
- Any failing checks (login failures, 4xx/5xx responses)

If thresholds failed, suggest next steps:
- p(95) > 3s: DB query or template rendering is slow — check slow query log
- error_rate > 2%: Check EC2 logs: sudo journalctl -u aihealthcare --no-pager -n 50
- login_failures > 0: CSRF scraping failed — check /login page HTML for name="_csrf"

---

## EC2 log command (if needed)

```bash
ssh -i "C:/workspaces/SpringAIClaude/N_VaKeyPair.pem" "ec2-user@100.61.13.237" 'sudo journalctl -u aihealthcare --no-pager -n 50'
```
