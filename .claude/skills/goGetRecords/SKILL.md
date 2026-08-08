# Go Get Records — Full Pipeline Harvest + Newsletter Send

Authenticate to EC2, trigger all data pipelines in sequence, then generate and send
the newsletter. Use this when you want to refresh all data and send a fresh newsletter.

## Connection info

```
IP="100.61.13.237"
KEY="C:/workspaces/SpringAIClaude/N_VaKeyPair.pem"
```

## Steps — execute in order

### 1. Authenticate

```bash
rm -f /tmp/ec2_cookies.txt

# Get login page for CSRF token
LOGIN_PAGE=$(curl -s -c /tmp/ec2_cookies.txt https://app.bigskylabs.ai/login)
CSRF=$(echo "$LOGIN_PAGE" | grep -o 'name="_csrf"[^>]*value="[^"]*"' | grep -o 'value="[^"]*"' | cut -d'"' -f2)

# Login
curl -s -c /tmp/ec2_cookies.txt -b /tmp/ec2_cookies.txt \
  -X POST "https://app.bigskylabs.ai/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=wgblackmonall%40gmail.com&password=Kinks%23998&_csrf=$CSRF" \
  -o /dev/null -w "Login: %{http_code}\n" -L
```

### 2. Get fresh CSRF from admin page

After login and before each POST, refresh the CSRF token:

```bash
ADMIN=$(curl -s -b /tmp/ec2_cookies.txt https://app.bigskylabs.ai/admin)
CSRF=$(echo "$ADMIN" | grep 'meta name="_csrf"' | grep -o 'content="[^"]*"' | cut -d'"' -f2)
```

### 3. Run all pipelines in order

Trigger each pipeline via POST with `X-CSRF-TOKEN` header and session cookie.
Use `--max-time 300` on LLM-heavy endpoints. Report results as you go.

**Data gathering (run in sequence):**

| # | Pipeline | Endpoint | Timeout |
|---|----------|----------|---------|
| 1 | RSS Feed Harvest | `POST /api/v1/monitoring/feeds` | 5 min |
| 2 | Competitor Web Pages | `POST /api/v1/monitoring/harvest` | 2 min |
| 3 | HuggingFace Discovery | `POST /api/v1/monitoring/huggingface` | 2 min |
| 4 | Regulatory Harvest | `POST /monitoring/regulatory/harvest` | 2 min |
| 5 | Clinical Trials | `POST /monitoring/clinical-trials-harvest` | 2 min |

**LLM-powered processing (run in sequence, expect some 504 gateway timeouts — these still execute server-side):**

| # | Pipeline | Endpoint | Timeout |
|---|----------|----------|---------|
| 6 | Wiki Compilation | `POST /monitoring/wiki/compile` | 5 min |
| 7 | Wiki Lint | `POST /monitoring/wiki/lint` | 1 min |
| 8 | Trend Detection | `POST /api/v1/trends/detect` | 1 min |
| 9 | Topic Summaries | `POST /api/v1/monitoring/summaries` | 5 min |
| 10 | Article Embeddings | `POST /api/v1/monitoring/embeddings` | 10 min |
| 11 | Company Discovery | `POST /api/v1/monitoring/company-discovery` | 5 min |
| 12 | Sentiment Pipeline | `POST /api/v1/monitoring/sentiment-pipeline` | 5 min |

**Curl pattern for each pipeline:**

```bash
curl -s -b /tmp/ec2_cookies.txt \
  -X POST "https://app.bigskylabs.ai{ENDPOINT}" \
  -H "X-CSRF-TOKEN: $CSRF" \
  -w "\nHTTP: %{http_code}\n" --max-time {TIMEOUT_SECS}
```

### 4. Wait for background pipelines

If any pipeline returned 504 (gateway timeout but still running server-side),
wait 60 seconds before proceeding to newsletter generation.

### 5. Generate and send newsletter

```bash
curl -s -b /tmp/ec2_cookies.txt \
  -X POST "https://app.bigskylabs.ai/admin/pipelines/newsletter/generate-and-send" \
  -H "X-CSRF-TOKEN: $CSRF" \
  -w "\nHTTP: %{http_code}\n" --max-time 300
```

Expected response: `{"status":"SUCCESS","message":"Newsletter generated and sent","durationMs":...}`

### 6. Report results

Report a summary table to the user showing each pipeline's result (article count,
HTTP status, or timeout). Include the newsletter generation duration.

## Notes

- 504 gateway timeouts on LLM-heavy pipelines are normal — the proxy times out but
  the JVM continues processing. The pipeline still runs to completion server-side.
- CSRF tokens may expire between calls. If you get a 403, refresh the CSRF token
  from the admin page (step 2) before retrying.
- Clinical trials endpoint sometimes returns 302 — this is a known redirect behavior.
- The full sequence takes 10-20 minutes depending on LLM response times.
