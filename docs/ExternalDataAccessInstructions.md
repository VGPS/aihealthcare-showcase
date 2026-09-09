# External Data Access Instructions

> Last updated: 2026-09-09 | Author: Bill Blackmon
>
> This document catalogs every feature that allows external users to programmatically
> access the AIHealthcare application's data sources. It covers REST APIs, API key
> authentication, webhook integrations, data exports, enterprise data jobs, signed
> downloads, and subscription tier gating.

---

## Table of Contents

1. [Authentication Methods](#1-authentication-methods)
2. [API Key Management](#2-api-key-management)
3. [Enterprise Data Access (PULL)](#3-enterprise-data-access-pull)
4. [Enterprise Push Schedules](#4-enterprise-push-schedules)
5. [Enterprise Remote Connections](#5-enterprise-remote-connections)
6. [Signed Artifact Downloads](#6-signed-artifact-downloads)
7. [Webhook Channels](#7-webhook-channels)
8. [Data Exports](#8-data-exports)
9. [Public REST APIs by Domain](#9-public-rest-apis-by-domain)
10. [Market Analysis APIs](#10-market-analysis-apis)
11. [Admin / Monitoring APIs](#11-admin--monitoring-apis)
12. [Stripe Integration](#12-stripe-integration)
13. [Subscription Tier Gating Summary](#13-subscription-tier-gating-summary)
14. [Security Model](#14-security-model)

---

## 1. Authentication Methods

The application supports three authentication mechanisms:

### Session-Based Authentication (Browser)

- Standard Spring Security form login at `GET /login`
- Session cookie persists across requests
- All Thymeleaf UI pages and most REST endpoints require an authenticated session

### API Key Authentication (Programmatic)

- Header: `X-API-Key: aih_<key>`
- Applied automatically to all `/api/**` paths via `ApiKeyAuthenticationFilter`
- The filter runs **before** Spring's username/password filter
- If the header is present and valid: authenticates with `ROLE_USER` authority
- If the header is present but invalid/inactive: returns `401 Unauthorized` immediately
- If the header is absent: falls through to normal session authentication
- **Note:** API key auth always grants `ROLE_USER` only — never `ROLE_ADMIN`

### HMAC-SHA256 Signed Tokens (Artifact Downloads)

- Used exclusively for enterprise data push artifact downloads at `GET /d/{token}`
- No session or API key required — the token itself is the authorization
- Tokens are time-limited (default 24 hours) and single-purpose (tied to a specific job ID)

---

## 2. API Key Management

**Base URL:** `/api/v1/keys`

API keys allow SUBSCRIBER, ENTERPRISE, and ADMIN tier users to authenticate REST API
calls without a browser session. Keys use a `aih_` prefix followed by a UUID. The raw
key is shown only once at creation time — it is SHA-256 hashed before storage.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/keys` | Create a new API key |
| `GET` | `/api/v1/keys` | List your API keys |
| `DELETE` | `/api/v1/keys/{id}` | Delete an API key |

### Per-Tier Key Limits

| Tier | Max Keys |
|------|----------|
| FREE / DEMO / FREE_PENDING | Cannot create keys |
| SUBSCRIBER | 3 |
| ENTERPRISE | 10 |
| ADMIN | Unlimited |

### Create Key Request

```http
POST /api/v1/keys
Content-Type: application/json
X-API-Key: aih_your_existing_key

{
  "name": "My Integration Key"
}
```

### Create Key Response (201)

```json
{
  "id": 42,
  "name": "My Integration Key",
  "keyPrefix": "aih_3f8a",
  "rawKey": "aih_3f8a1b2c3d4e5f6a7b8c9d0e1f2a3b4c",
  "active": true,
  "createdAt": "2026-09-09T12:00:00Z"
}
```

> **Important:** The `rawKey` field is returned only in this response. Save it immediately.
> Subsequent `GET /api/v1/keys` calls return only the `keyPrefix` for identification.

---

## 3. Enterprise Data Access (PULL)

**Base URL:** `/api/v1/enterprise/data`
**Tier required:** ENTERPRISE

The enterprise data access system provides async, job-based data exports. Customers
submit a data job specifying a feed, optional prompt, output format, and row limit. The
job runs asynchronously, produces an artifact (CSV/JSON/PDF), and makes it available
for download. Artifacts expire after 14 days (configurable).

### Job Lifecycle

```
QUEUED → RUNNING → SUCCEEDED → (download available for 14 days) → EXPIRED
                 → FAILED
                 → CANCELLED
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/enterprise/data/jobs` | Submit a new data export job |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}` | Get job status |
| `GET` | `/api/v1/enterprise/data/jobs` | List your jobs (paginated) |
| `POST` | `/api/v1/enterprise/data/jobs/{jobId}/cancel` | Cancel a non-terminal job |
| `GET` | `/api/v1/enterprise/data/feeds` | List available data feeds |
| `GET` | `/api/v1/enterprise/data/feeds/{feedId}/prompts` | List canned prompts for a feed |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/log` | Tail job execution log |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/artifact` | Download completed artifact |

### Submit Job Request (202 Accepted)

```http
POST /api/v1/enterprise/data/jobs
Content-Type: application/json
X-API-Key: aih_your_key

{
  "feedId": "articles",
  "promptId": "recent-ai-healthcare",
  "format": "CSV",
  "rowLimit": 500
}
```

The response includes a `Location` header with the job URL for polling status.

### Download Artifact

```http
GET /api/v1/enterprise/data/jobs/{jobId}/artifact
X-API-Key: aih_your_key
```

Returns binary content with `Content-Disposition: attachment; filename={jobId}.csv`.
Returns `409` if the job hasn't succeeded yet, `410` if the artifact has expired.

### Enforcement

- **Tier:** Only ENTERPRISE tier users can submit jobs (403 otherwise)
- **Quota:** Monthly job quota enforced via usage tracking
- **Concurrency:** Maximum 2 concurrent active jobs per account (configurable)
- **Row limit:** Clamped to `min(requested, feed.maxRowLimit, config.maxRowsPerJob)`
- **Ownership:** Only the job owner can view or download their jobs — other owners see 404

---

## 4. Enterprise Push Schedules

**Base URL:** `/api/v1/enterprise/data/schedules`
**Tier required:** ENTERPRISE

Push schedules deliver data exports to email recipients on a cron schedule. The system
uses a per-minute DB sweeper with atomic claim-based scheduling to guarantee at-most-once
delivery even with multiple application instances.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/enterprise/data/schedules` | Create a push schedule |
| `GET` | `/api/v1/enterprise/data/schedules` | List your schedules |
| `PUT` | `/api/v1/enterprise/data/schedules/{scheduleId}` | Update a schedule |
| `DELETE` | `/api/v1/enterprise/data/schedules/{scheduleId}` | Delete a schedule |
| `POST` | `/api/v1/enterprise/data/schedules/{scheduleId}/run` | Trigger immediate run |
| `GET` | `/api/v1/enterprise/data/schedules/preview` | Preview next run times for a cron expression |

### Create Schedule Request (201)

```http
POST /api/v1/enterprise/data/schedules
Content-Type: application/json
X-API-Key: aih_your_key

{
  "label": "Weekly AI Healthcare Articles",
  "feedId": "articles",
  "promptId": "recent-ai-healthcare",
  "format": "CSV",
  "cronExpression": "0 0 8 * * MON",
  "zoneId": "America/Chicago",
  "recipients": ["team@example.com", "analyst@example.com"]
}
```

### Delivery Modes

- **Attachment:** If the artifact is ≤8MB, it's attached directly to the email
- **Signed Link:** If >8MB, the email contains a time-limited download link (24h expiry)

### Auto-Deactivation

After 5 consecutive failures (configurable), the schedule is automatically deactivated
and an admin notification is sent. To reactivate, update the schedule with `active: true`.

### Limits

| Limit | Default |
|-------|---------|
| Max schedules per account | 25 |
| Max recipients per schedule | 10 |
| Failure threshold before deactivation | 5 |
| Signed link expiry | 24 hours |
| Max attachment size | 8 MB |

### Preview Next Runs

```http
GET /api/v1/enterprise/data/schedules/preview?cron=0+0+8+*+*+MON&zone=America/Chicago&count=5
X-API-Key: aih_your_key
```

Returns a JSON array of the next 5 Instant values when the schedule would fire.

---

## 5. Enterprise Remote Connections

**Base URL:** `/api/v1/enterprise/connections`
**Tier required:** ENTERPRISE

Remote connections allow enterprise customers to register external HTTPS endpoints that
the data job system can pull data from via the `CUSTOMER_REMOTE` data source kind.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/enterprise/connections` | Register a new connection |
| `GET` | `/api/v1/enterprise/connections` | List your connections |
| `GET` | `/api/v1/enterprise/connections/{id}` | Get connection details |
| `PUT` | `/api/v1/enterprise/connections/{id}` | Update connection |
| `DELETE` | `/api/v1/enterprise/connections/{id}` | Delete connection |

### Security Constraints

- **HTTPS only:** All remote connection URLs must start with `https://`
- **IP pinning:** DNS is resolved once; the HTTP connection uses the validated IP directly
- **No redirects:** HTTP redirects are blocked to prevent SSRF
- **IP blocklist:** Loopback, link-local, site-local, multicast, and IMDS (169.254.x.x) ranges are blocked
- **Host allow-list:** Production requires a non-empty `allowed-hosts` configuration
- **Secrets are references:** The `secretRef` field holds an environment variable name, not a secret value. Secrets are resolved at request time and never logged, stored, or returned in API responses. Responses include only a `hasSecret` boolean.

---

## 6. Signed Artifact Downloads

**URL:** `GET /d/{token}`
**Authentication:** None required (the HMAC token is the authorization)

Signed download links are generated for enterprise push schedule deliveries when the
artifact exceeds the email attachment size limit. They provide time-limited, direct
access to a specific job's artifact without requiring a login session.

### Token Format

```
{base64url(jobId:expiryEpochSeconds)}.{base64url(hmac-sha256)}
```

### Responses

| Status | Meaning |
|--------|---------|
| 200 | Success — binary file download with Content-Disposition header |
| 403 | Invalid or expired token |
| 410 | Job or artifact no longer exists |

### Security Properties

- HMAC-SHA256 signature prevents token forgery
- Constant-time comparison (`MessageDigest.isEqual`) prevents timing attacks
- Default expiry: 24 hours
- Each download is audit-logged as `LINK_REDEEM`
- Token values are always `[REDACTED]` in application logs

---

## 7. Webhook Channels

**Base URL:** `/api/v1/webhooks`
**Tier required:** SUBSCRIBER, ENTERPRISE, or ADMIN

Webhook channels allow subscribers to receive outbound HTTP POST notifications when
specific application events occur (e.g., pipeline completions).

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/webhooks` | Create a webhook channel |
| `GET` | `/api/v1/webhooks` | List your channels |
| `DELETE` | `/api/v1/webhooks/{id}` | Delete a channel |
| `POST` | `/api/v1/webhooks/{id}/test` | Send a test notification |

### Create Channel Request (201)

```http
POST /api/v1/webhooks
Content-Type: application/json
X-API-Key: aih_your_key

{
  "name": "Pipeline Alerts",
  "webhookUrl": "https://hooks.example.com/aihealthcare",
  "channelType": "CUSTOM",
  "subscribedEvents": ["PIPELINE_COMPLETE"]
}
```

### Webhook Payload

```json
{
  "eventType": "PIPELINE_COMPLETE",
  "title": "Daily Pipeline Complete",
  "message": "All 14 pipeline stages completed successfully.",
  "data": { ... },
  "occurredAt": "2026-09-09T12:00:00Z"
}
```

---

## 8. Data Exports

### Generic Data Export

**URL:** `GET /api/v1/export`

Download articles and other data in CSV, JSON, or PDF format.

| Parameter | Type | Description |
|-----------|------|-------------|
| `type` | String | Data type to export |
| `format` | String | `CSV`, `JSON`, or `PDF` |
| `limit` | Integer | Max rows (default 100) |
| `brandName` | String | Optional branding label |

Returns a binary download with `Content-Disposition: attachment` header.

### Company Directory CSV Export

**URL:** `GET /directory/export.csv`
**Tier required:** ENTERPRISE or ADMIN

Exports the full company directory with signal scoring data. Non-ENTERPRISE users are
redirected to `/pricing`.

**CSV Columns:** Name, Category, HQ, Founded, Funding Stage, Articles(90d), Has Funding
Signal, Deal Amount, Sentiment, Sentiment Score, Relevance Score

---

## 9. Public REST APIs by Domain

All endpoints below are under `/api/v1/` and require either an authenticated session or
a valid `X-API-Key` header (per the SecurityConfig `/api/**` rule). Response format is
JSON unless otherwise noted.

### Articles

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/articles?topic=&limit=20` | List articles by topic |
| `GET` | `/api/v1/articles/search?keyword=&sourceTier=&from=&to=` | Multi-field article search |

### AI-Enhanced Search

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/search/ai?query=&topK=20&models=claude,gpt,perplexity,gemini` | Multi-model LLM synthesis |

### Research

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/research` | Execute a research query (COMBINED mode) |
| `GET` | `/api/v1/research/runs` | List research run history |
| `GET` | `/api/v1/research/runs/{runId}` | Get research run detail |

### Newsletter Runs

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/runs` | List newsletter run summaries |
| `GET` | `/api/v1/runs/{runId}` | Get full newsletter run detail (HTML + plain text) |
| `POST` | `/api/v1/newsletter/deliver` | Deliver a newsletter run to subscribers |

### Trend Analysis

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/trends/latest` | Latest trend snapshot (rising/fading/new keywords) |
| `GET` | `/api/v1/trends/history` | All trend snapshots |
| `POST` | `/api/v1/trends/detect` | Trigger trend detection |

### Deal Signals

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/deals` | List recent deal signals (optional `?type=` filter) |
| `GET` | `/api/v1/deals/{signalId}` | Get deal signal with cross-referenced context |
| `POST` | `/api/v1/deals/detect` | Trigger deal signal detection (async, 202) |

### Sentiment & Risk

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/sentiment` | All company sentiment scores |
| `GET` | `/api/v1/sentiment/{slug}` | Single company sentiment detail |
| `POST` | `/api/v1/sentiment/analyze` | Trigger full sentiment analysis |

### Framework Competitive Analysis

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/frameworks` | All framework analyses |
| `GET` | `/api/v1/frameworks/{slug}` | Single company framework analysis (6 dimensions) |
| `POST` | `/api/v1/frameworks/analyze` | Trigger framework analysis |

### Company Relationships

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/relationships` | All detected company relationships |
| `GET` | `/api/v1/relationships/graph` | Graph data in vis.js format |
| `POST` | `/api/v1/relationships/detect` | Trigger relationship detection |

### State Health-AI Legislation

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/legislation/state-laws` | List laws (filters: state, category, status, q) |
| `GET` | `/api/v1/legislation/state-laws/{id}` | Single law detail |
| `GET` | `/api/v1/legislation/state-laws/upcoming?days=90` | Laws with upcoming effective dates |
| `GET` | `/api/v1/legislation/state-laws/by-state` | Laws grouped by state (for map view) |
| `GET` | `/api/v1/legislation/changes?unreviewedOnly=true` | Law change events |
| `POST` | `/api/v1/legislation/changes/{id}/review` | Mark change reviewed (ADMIN) |
| `POST` | `/api/v1/legislation/refresh` | Trigger legislation refresh (ADMIN, async 202) |

### Subscribers

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/subscribers` | Add subscriber (201) |
| `GET` | `/api/v1/subscribers` | List all subscribers (ADMIN) |
| `DELETE` | `/api/v1/subscribers/{email}` | Remove subscriber (ADMIN) |

### Teams

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/teams` | Create a team (ENTERPRISE) |
| `GET` | `/api/v1/teams` | Get your team |
| `GET` | `/api/v1/teams/{teamId}/members` | List team members |
| `POST` | `/api/v1/teams/{teamId}/members` | Add team member |
| `DELETE` | `/api/v1/teams/{teamId}/members/{email}` | Remove team member |

### Prompt Evaluation

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/variants` | Create prompt variant |
| `GET` | `/api/v1/variants` | List prompt variants |
| `GET` | `/api/v1/variants/{id}` | Get prompt variant |
| `DELETE` | `/api/v1/variants/{id}` | Delete prompt variant |
| `POST` | `/api/v1/evaluations` | Run prompt evaluation |
| `GET` | `/api/v1/evaluations` | List evaluations |
| `GET` | `/api/v1/evaluations/{id}` | Get evaluation detail |
| `POST` | `/api/v1/comparisons` | Compare two prompt variants |

### Analytics

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/analytics/ingestion` | Ingestion analytics |
| `GET` | `/api/v1/analytics/runs` | Newsletter run analytics |
| `GET` | `/api/v1/analytics/evaluations` | Evaluation analytics |

### Search Prompts

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/search-prompts` | List all search prompt templates |
| `GET` | `/api/v1/search-prompts/{engine}` | Get search prompt by engine |

### Document Ingestion

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/documents/ingest` | Ingest documents from a directory (async, 202) |

---

## 10. Market Analysis APIs

Market analysis endpoints use `/api/market-digest/` (no `v1` prefix).

### Market Digest

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/market-digest/latest` | Most recent daily digest |
| `GET` | `/api/market-digest/{date}` | Digest for a specific date (YYYY-MM-DD) |
| `GET` | `/api/market-digest` | List all digest summaries |
| `GET` | `/api/market-digest/weekly-rollup?weekOf=` | Weekly rollup aggregation |

### Market Watchlist

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/market-digest/watchlist` | Get your ticker watchlist |
| `PUT` | `/api/market-digest/watchlist` | Replace your watchlist |
| `POST` | `/api/market-digest/subscribe` | Subscribe to watchlist alerts |

### Market Enrichment

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/market-digest/regulatory-tracker` | All regulatory trackers |
| `GET` | `/api/market-digest/regulatory-tracker/approaching` | Trackers with approaching deadlines |
| `GET` | `/api/market-digest/regulatory-tracker/{docketId}` | Single tracker by docket ID |
| `GET` | `/api/market-digest/funding` | Recent private funding rounds |
| `GET` | `/api/market-digest/deal-terms/{headline}` | Deal terms for a specific headline |
| `GET` | `/api/market-digest/guidance/{ticker}` | Earnings guidance comparison for a ticker |

---

## 11. Admin / Monitoring APIs

### Monitoring Triggers (`/api/v1/monitoring/`)

All monitoring endpoints are ADMIN-only (enforced by SecurityConfig). They trigger
async pipeline operations and return `202 Accepted`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/monitoring/harvest` | Trigger competitor web page harvest |
| `POST` | `/api/v1/monitoring/huggingface` | Trigger HuggingFace model discovery |
| `POST` | `/api/v1/monitoring/research-harvest` | Trigger research pipeline per topic |
| `POST` | `/api/v1/monitoring/feeds` | Trigger RSS feed harvest |
| `POST` | `/api/v1/monitoring/summaries` | Trigger topic summary generation |
| `POST` | `/api/v1/monitoring/embeddings` | Trigger embedding scheduler |
| `POST` | `/api/v1/monitoring/company-discovery` | Trigger company discovery pipeline |
| `POST` | `/api/v1/monitoring/sentiment-pipeline` | Trigger sentiment analysis pipeline |
| `POST` | `/api/v1/monitoring/run-all-pipelines` | Run full 14-stage pipeline cascade |
| `GET` | `/api/v1/monitoring/hashes` | List web page content hashes |

### Monitoring Operations (`/monitoring/`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/monitoring/backfill` | Trigger article backfill |
| `POST` | `/monitoring/backfill/custom` | Trigger custom backfill |
| `POST` | `/monitoring/legal-backfill` | Trigger legal article backfill |
| `POST` | `/monitoring/regulatory/harvest` | Trigger regulatory harvest |
| `POST` | `/monitoring/wiki/compile` | Trigger wiki compilation |
| `POST` | `/monitoring/wiki/gap-analysis` | Trigger wiki gap analysis |
| `POST` | `/monitoring/wiki/gap-analysis/compile-approved` | Compile approved gaps |
| `POST` | `/monitoring/wiki/lint` | Trigger wiki lint |
| `GET` | `/monitoring/wiki/lint/latest` | Get latest lint report |
| `GET` | `/monitoring/sample-newsletter` | Preview sample newsletter (HTML) |
| `POST` | `/monitoring/sample-newsletter/send` | Send sample newsletter |
| `GET` | `/monitoring/digest-newsletter` | Preview digest newsletter (HTML) |
| `POST` | `/monitoring/digest-newsletter/send` | Send digest newsletter |

---

## 12. Stripe Integration

### Checkout

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/stripe/checkout` | Create a Stripe checkout session |
| `POST` | `/api/v1/stripe/portal` | Create a Stripe customer portal session |

### Inbound Webhook

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/stripe/webhook` | Stripe webhook receiver (signature-verified) |

The webhook handles three event types:
- `checkout.session.completed` — new subscription
- `customer.subscription.updated` — plan change
- `customer.subscription.deleted` — cancellation

The webhook endpoint is `permitAll()` in SecurityConfig — authentication is via Stripe's
`Stripe-Signature` header verification.

---

## 13. Subscription Tier Gating Summary

The application has 5 subscription tiers plus an ADMIN role. Each tier unlocks
progressively more data access.

### Tier Hierarchy

| Tier | Description |
|------|-------------|
| FREE_PENDING | Newly registered, email not yet verified |
| FREE | Verified free user |
| DEMO | Time-limited full access trial |
| SUBSCRIBER | Paid subscriber ($49/mo via Stripe) |
| ENTERPRISE | Enterprise customer with data export + push capabilities |

ADMIN is a role, not a tier — an ADMIN user can be any tier level.

### Feature Access by Tier

| Feature | FREE | SUBSCRIBER | ENTERPRISE | ADMIN |
|---------|------|------------|------------|-------|
| API key creation | No | 3 keys | 10 keys | Unlimited |
| Webhook channels | No | Yes | Yes | Yes |
| AI search | No (upgrade banner) | Yes | Yes | Yes |
| Watchlists | No (redirect to pricing) | Yes | Yes | Yes |
| Company directory CSV export | No | No | Yes | Yes |
| Enterprise data jobs (PULL) | No | No | Yes | Yes |
| Enterprise push schedules | No | No | Yes | Yes |
| Enterprise remote connections | No | No | Yes | Yes |
| Team management | No | No | Yes | Yes |
| Trend analysis | Top 5 rising only | Full | Full | Full |
| Trend history | Last 4 snapshots | Full | Full | Full |
| Market digest | Top 5 entries | Full | Full | Full |
| Regulatory alerts | 5 most recent | Full | Full | Full |
| Sentiment analysis | Top 5 companies | Full | Full | Full |
| Legislation detail pages | 5 law details | Full | Full | Full |
| Archive depth | 7-day window | Unlimited | Unlimited | Unlimited |
| Research queries | Limited monthly | Higher monthly limit | Full | Full |
| Admin panel / pipelines | No | No | No | Yes |

---

## 14. Security Model

### Public Endpoints (No Authentication Required)

| Pattern | Purpose |
|---------|---------|
| `/`, `/login`, `/register` | Auth flow |
| `/pricing`, `/about`, `/privacy`, `/press` | Informational pages |
| `/directory`, `/directory/**` | Public company directory (SEO) |
| `/wiki` | Wiki index (detail pages require login) |
| `/legislation` | Legislation index (detail pages require login) |
| `/d/**` | Signed artifact download (HMAC token auth) |
| `/api/v1/stripe/webhook` | Stripe webhook (signature-verified) |
| `/api/v1/feedback/**` | Public feedback endpoint |

### CSRF Protection

CSRF is disabled for: `/api/**`, `/monitoring/**`, `/admin/pipelines/**`, `/stripe/**`,
`/d/**`. All other paths (primarily Thymeleaf forms) have CSRF protection enabled.

### Enterprise Data Security Invariants

These six invariants are enforced throughout the enterprise data access layer:

1. **No natural-language to SQL.** `DataQueryPlan` has a closed 8-field set. Changing it requires security review.
2. **No filesystem call outside `ConfinedFileStore`.** File names are regex-validated, paths are normalized and checked for traversal, symlinks are rejected.
3. **No outbound HTTP outside `RemoteEndpointGuard`.** HTTPS-only, IP-pinned, redirect-blocked, host allow-list enforced in production.
4. **No tool calling, no MCP, no agent loop** on any customer-triggered path. One-shot LLM calls only.
5. **Ownership resolved in the query.** Uses `findByXAndOwnerEmail`, never `findById` + check. Other owner returns 404, not 403.
6. **Secrets are references, never values.** `secretRef` holds an env var name, resolved at request time, never logged or returned in responses.

### Audit Trail

Every enterprise data operation is logged to the `enterprise_data_audit` table with:
- Timestamp, owner email, job ID, action (18 possible values), outcome, detail

Actions tracked: SUBMIT, TIER_DENY, QUOTA_DENY, CONCURRENCY_DENY, PLAN, FETCH, RENDER,
COMPLETE, FAIL, CANCEL, DOWNLOAD_ARTIFACT, DOWNLOAD_LOG, SCHEDULE_CREATE, SCHEDULE_UPDATE,
SCHEDULE_DELETE, SCHEDULE_FIRE, PUSH_SEND, PUSH_FAIL, SCHEDULE_DEACTIVATE, LINK_REDEEM.

---

## Quick Start: Programmatic Access

### 1. Create an API Key (requires SUBSCRIBER+ tier)

Log in to the web UI at `/profile` and create a key, or use an existing session:

```http
POST /api/v1/keys
Cookie: JSESSIONID=your_session
Content-Type: application/json

{"name": "My Script"}
```

Save the `rawKey` from the response.

### 2. Use the Key for API Calls

```bash
curl -H "X-API-Key: aih_your_key" \
     https://app.bigskylabs.ai/api/v1/trends/latest
```

### 3. Export Data

```bash
curl -H "X-API-Key: aih_your_key" \
     "https://app.bigskylabs.ai/api/v1/export?type=articles&format=CSV&limit=100" \
     -o articles.csv
```

### 4. Enterprise: Submit a Data Job

```bash
curl -X POST -H "X-API-Key: aih_your_key" \
     -H "Content-Type: application/json" \
     -d '{"feedId":"articles","format":"CSV","rowLimit":1000}' \
     https://app.bigskylabs.ai/api/v1/enterprise/data/jobs
```

Poll the returned `Location` URL until `status` is `SUCCEEDED`, then download the artifact.
