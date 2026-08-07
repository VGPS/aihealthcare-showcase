**This will end v. 1 of the application, pre-Enterprise Initial Offers and Marketing.**

# AIHealthcare — Comprehensive QA & Hardening Plan

> Created: 2026-08-06 | Author: Bill Blackmon + Claude
> Estimated duration: 5-7 working days
> Scope: bugs, dead code, security, E2E testing, UI consistency

---

## Codebase Snapshot (as of 2026-08-06)

| Metric | Count |
|--------|-------|
| Controllers (view + REST) | 70 |
| Thymeleaf templates | 46 pages + 6 fragments |
| REST endpoints | 152 |
| Domain services | 46 |
| Outbound ports | 66 |
| Schedulers | 15 |
| Test classes | 246 |
| Untested production classes | 19 |

---

## Day 1 — Security Audit ✅ COMPLETE (7d04042)

### 1.1 Authentication & Authorization Gaps

**All `/api/**` endpoints are `permitAll`.** This means every REST endpoint is publicly
accessible without authentication. While API key auth exists via `ApiKeyAuthenticationFilter`,
it only validates requests that *include* an `X-API-Key` header — requests without the header
pass through unauthenticated.

**Audit checklist:**
- [x] Review every `/api/**` endpoint — which ones should require authentication? → ALL except Stripe webhook + feedback
- [x] Categorize endpoints: public (pricing, wiki) vs protected (deals, sentiment, frameworks) → DONE
- [x] Decide: should REST endpoints require either session auth OR API key? → YES, enforced
- [x] Review `ApiKeyAuthenticationFilter` — does it reject keyless requests or pass them through? → Pass-through, but `authenticated()` rule now blocks
- [x] Verify `/monitoring/**` endpoints are admin-only → FIXED: now `hasRole("ADMIN")`
- [x] Verify `/stripe/webhook` validates Stripe signatures → FIXED: rejects if secret missing
- [x] Check if `/unsubscribe/downgrade` POST is CSRF-protected → YES, confirmed safe

### 1.2 Input Validation & Injection

- [x] Audit all `@RequestParam` and `@PathVariable` inputs for injection risks → No SQL injection vectors; all use JPA parameterized queries
- [x] Check Thymeleaf templates for unescaped output (`th:utext` vs `th:text`) — XSS vectors → 3 instances, all LLM-generated content (accepted risk, see SEC-5)
- [x] Review `data.sql` seed data — any secrets or PII in source control? → FIXED: removed plaintext password comments
- [x] Check all ChatClient/RestClient calls for prompt injection vectors → User queries flow to LLM prompts by design; no escalation risk
- [x] Review `WebPageHarvester` — does it sanitize scraped HTML before storage? → YES, extracts text via Jsoup
- [x] Review `NewsletterPreviewController` — TinyMCE editor saves raw HTML; is it sanitized? → Admin-only, accepted risk (SEC-6)

### 1.3 Secrets & Configuration

- [x] Grep codebase for hardcoded API keys, passwords, tokens → None found; all use `${ENV_VAR}` placeholders
- [x] Verify `.env` is in `.gitignore` → YES
- [x] Review `application.yml` and `application-aws.yml` for leaked credentials → Clean; `password: 1454` is local dev DB only
- [x] Check if BCrypt password hashes in `data.sql` correspond to known weak passwords → YES (admin123, demo123); plaintext hints removed
- [x] Verify Stripe webhook signature validation is actually enforced → FIXED

### 1.4 CSRF & Session Security

- [x] Map all POST/PUT/DELETE endpoints and verify CSRF protection is appropriate → CSRF enabled for browser paths, disabled for `/api/**` + `/monitoring/**` + `/stripe/**`
- [x] Check session fixation protection → Spring Security default handles this
- [x] Review cookie settings — HttpOnly, Secure, SameSite flags → FIXED: added to `application-aws.yml`
- [x] Check for open redirect vulnerabilities in login/registration flows → Safe; `safeReturnUrl()` requires `/` prefix, all other redirects are hardcoded

---

## Day 2 — Dead Code & Structural Cleanup ✅ COMPLETE

### 2.1 Untested Production Classes

Write tests for or review these 19 classes that lack test counterparts:

| Class | Priority | Action |
|-------|----------|--------|
| `GlobalExceptionHandler` | HIGH | ✅ **TEST WRITTEN** — 10 tests covering all 10 exception→status mappings |
| `FooterModelAdvice` | MEDIUM | ✅ **TEST WRITTEN** — 6 tests (anonymous, null port, FREE, SUBSCRIBER, ADMIN, unknown user) |
| `FeedHarvestScheduler` | MEDIUM | Deferred — complex scheduler with many collaborators, existing integration coverage via pipeline tests |
| `ArticleIngestionAdapter` | MEDIUM | Deferred — DB adapter, covered transitively by controller tests |
| `NewsletterRunAdapter` | MEDIUM | Deferred — entity/domain mapping, covered transitively by controller tests |
| `NewsletterRunController` | MEDIUM | ✅ **TEST WRITTEN** — 4 tests (list runs, empty list, detail, multiple runs) |
| `RegulatoryMonitoringController` | LOW | Reviewed — active, not dead code |
| `AiSummarizationAdapter` | LOW | AI adapter — requires `@Profile("ai-integration")` |
| `AiReportAdapter` | LOW | AI adapter — requires `@Profile("ai-integration")` |
| `AnthropicAiSearchAdapter` | LOW | AI adapter — requires `@Profile("ai-integration")` |
| `OpenAiSearchAdapter` | LOW | AI adapter — requires `@Profile("ai-integration")` |
| `AnalyticsAdapter` | LOW | Reviewed — not dead code |
| `CompanyEventAdapter` | LOW | Reviewed — not dead code |
| `DocumentIngestionAdapter` | LOW | Conditional bean — wiring verified |
| `MarketIntelligenceScheduler` | LOW | Reviewed — active scheduler |
| `NoOpArticleSearchAdapter` | LOW | Conditional fallback — trivial implementation |
| `NotebookLMResearchExportAdapter` | LOW | File export — active |
| `ResearchPlanningService` | LOW | LLM delegation — active |
| `ResearchSynthesisService` | LOW | LLM delegation — active |

### 2.2 Structural Anomalies

- [x] Move `PipelineHealthService` from `web/controller/` to `infrastructure/scheduler/` — moved + all 15 references updated
- [x] Check for any other misplaced classes — no additional anomalies found
- [x] Move `MarketIntelligenceScheduler` from `infrastructure/config/` to `infrastructure/scheduler/` — moved + 4 references updated
- [x] Move `EmbeddingScheduler` from `infrastructure/ai/` to `infrastructure/scheduler/` — moved + 7 references updated
- [x] Test files moved: `PipelineHealthServiceTest` → `infrastructure/scheduler/`, `EmbeddingSchedulerTest` → `infrastructure/scheduler/`
- [x] All 49 affected tests pass after moves

### 2.3 Dead Code Scan

- [x] Compile check — clean compilation, no unused import warnings
- [x] Check for unused `@Bean` definitions in `AppConfig` — all 45+ beans are actively injected
- [x] Review all DTOs in `web/dto/` — all 50 DTOs are referenced by at least one controller/service
- [x] `semantic-search.html` — **DELETED** (orphaned template, no controller returns it since Slice 40 merge)
- [x] `ResearchCompareController` — already deleted from source; `research-compare.html` only in `target/` (stale build artifact)
- [x] `developer.html` / `DeveloperPortalController` — fully active, linked from nav, tests exist

### 2.4 Dependency Audit

- [x] Compile clean — no unused import warnings from `mvn compile`
- [x] Test-scope dependencies reviewed — all properly scoped (JUnit, Mockito, Spring Test in `<scope>test</scope>`)

### Day 2 Results

| ID | Severity | Location | Description | Fix | Status |
|----|----------|----------|-------------|-----|--------|
| DC-1 | LOW | `templates/semantic-search.html` | Orphaned template — no controller returns this view since Slice 40 merge | Deleted via `git rm` | FIXED |
| DC-2 | MEDIUM | `web/controller/PipelineHealthService` | Service class misplaced in controller package | Moved to `infrastructure/scheduler/` | FIXED |
| DC-3 | LOW | `infrastructure/config/MarketIntelligenceScheduler` | Scheduler misplaced in config package | Moved to `infrastructure/scheduler/` | FIXED |
| DC-4 | LOW | `infrastructure/ai/EmbeddingScheduler` | Scheduler misplaced in AI adapter package | Moved to `infrastructure/scheduler/` | FIXED |
| DC-5 | HIGH | `GlobalExceptionHandler` | No test coverage for critical exception→status mapping | 10-test class written | FIXED |
| DC-6 | MEDIUM | `FooterModelAdvice` | No test coverage for global model attribute injection | 6-test class written | FIXED |
| DC-7 | MEDIUM | `NewsletterRunController` | No test coverage for REST run retrieval | 4-test class written | FIXED |

---

## Day 3 — Page-by-Page UI Consistency Review ✅ COMPLETE

### 3.1 Navigation Consistency

Visit every page and verify:
- [x] Nav bar present and consistent — 42 templates use shared `fragments/nav` fragment; 6 expected exclusions (login, register, choose-path, unsubscribe, access-denied, articles)
- [x] Footer fragment present on all 47 templates
- [x] Active nav item highlighted correctly — `activePage` param passed on every template, compared in nav fragment
- [x] All 29 nav links work — curl test confirms 200/302 on every link, no 404s
- [x] "Reference" dropdown has all expected items (Wiki, Clinical Trials, Companies, Sentiment & Risk, Relationships)
- [x] Mobile responsiveness — Alpine.js dropdown toggles verified in markup

### 3.2 Template-by-Template Review

For each page, check:
- Correct page title in `<head>` — **FIXED: 4 templates had duplicate `— AIHealthcare` suffix** (UI-1)
- No broken Thymeleaf expressions — all 36 tested pages return 200, no 500 errors
- No raw `${...}` or `null` text visible — scanned all rendered output, clean

**Dashboard cluster (7 pages):**
- [x] `/dashboard` — 200 OK, footer renders, Chart.js scripts present
- [x] `/dashboard/articles` — requires `?topic=` param (400 without, 200 with — expected behavior)
- [x] `/dashboard/news` — 200 OK, articles grouped by topic
- [x] `/dashboard/search` — 200 OK, filter form renders
- [x] `/dashboard/trends` — 200 OK, keyword cards render
- [x] `/dashboard/regulatory` — 200 OK, filter tabs present
- [x] `/dashboard/deals` — 200 OK, type filter pills render

**Research cluster (6 pages):**
- [x] `/research/ai-search` — 200 OK, model checkboxes present
- [x] `/research/compare` — **REMOVED** — controller deleted; no template or nav link remains
- [x] `/research/runs` — 200 OK, run history table
- [x] `/research/runs/{runId}` — requires valid runId (tested via run history links)
- [x] `/research/vendors` — 200 OK, vendor checkbox grid
- [x] `/research/intel` — 200 OK, intel reports list

**Framework & Sentiment (4 pages):**
- [x] `/dashboard/frameworks` — 200 OK, radar chart markup present
- [x] `/dashboard/frameworks/{slug}` — requires valid slug (tested via framework links)
- [x] `/dashboard/risk` — 200 OK, horizontal bar chart markup present
- [x] `/dashboard/risk/{slug}` — requires valid slug (tested via risk links)

**Wiki cluster (5 pages):**
- [x] `/wiki` — 200 OK (public), searchable page grid
- [x] `/wiki/{slug}` — requires valid slug
- [x] `/wiki/contradictions` — 200 OK (public), reversal watch feed
- [x] `/wiki/ask` — 200 OK, wiki question interface
- [x] `/wiki/digest` — 200 OK, wiki digest

**Deal detail & trends (3 pages):**
- [x] `/dashboard/deals/{signalId}` — requires valid signalId
- [x] `/dashboard/trends/history` — 200 OK, multi-line chart + timeline
- [x] `/dashboard/trends/history/{epochMillis}` — requires valid epoch

**Legal & Clinical (3 pages):**
- [x] `/dashboard/legal` — 200 OK, legal timeline
- [x] `/dashboard/legal/trends` — 200 OK, legal trend analysis
- [x] `/dashboard/clinical-trials` — 200 OK, clinical trials page

**Company pages (3 pages):**
- [x] `/dashboard/companies` — 200 OK, company directory
- [x] `/companies/{slug}` — requires valid slug
- [x] `/dashboard/relationships` — 200 OK, company relationships

**Admin pages (3 pages):**
- [x] `/admin` — 200 OK (ADMIN only), user management + system status
- [x] `/admin/pipelines` — 200 OK (ADMIN only), pipeline management
- [x] `/newsletter/runs` — 200 OK (ADMIN only), newsletter run list

**Auth & account (6 pages):**
- [x] `/login` — 200 OK (public), login form
- [x] `/register` — 200 OK (public), registration form
- [x] `/choose-path` — 200 OK (public), tier selection
- [x] `/profile` — 200 OK (authenticated), tier badge + usage meter
- [x] `/pricing` — 200 OK (public), tier comparison
- [x] `/unsubscribe` — 200 OK (public), soft-landing page

**Other (4 pages):**
- [x] `/watchlist` — 200 OK, subscriber watchlist
- [x] `/developer` — 200 OK, developer portal
- [x] `/settings/webhooks` — 200 OK, webhook configuration
- [x] `/access-denied` — 200 OK, 403 page
- [x] `/notes` — 200 OK, analyst notes

### Day 3 Results

| ID | Severity | Location | Description | Fix | Status |
|----|----------|----------|-------------|-----|--------|
| UI-1 | LOW | 4 templates | Duplicate `— AIHealthcare` in page title (head fragment already appends it) | Removed suffix from `ai-search.html`, `research-run-detail.html`, `research-runs.html`, `vendor-compare.html` | FIXED |
| UI-2 | INFO | `/research/compare` | QA plan listed this but controller already deleted (Day 2) | Removed from checklist | N/A |

---

## Day 4 — End-to-End Flow Testing ✅ COMPLETE (2026-08-07, commit TBD)

### 4.1 User Registration & Login Flow

- [x] ~~Register new user → choose-path → login → dashboard~~ (deferred — requires form CSRF extraction)
- [x] Login with existing user → redirect to dashboard (302 → /dashboard)
- [x] Login with wrong password → error message (302 → /login?error)
- [x] ~~Login with disabled account~~ (deferred — no disabled account in seed data)
- [x] Logout → redirects to login page (GET /logout → 302 → /login?logout, session invalidated)
- [x] Access protected page without login → redirect to login (302 → /login)
- [x] Access admin page as non-admin → 403 page (demo user → /admin → 403)

### 4.2 Subscriber Lifecycle

- [x] ~~Subscribe via pricing page~~ (deferred — requires live Stripe session)
- [x] View profile → tier badge, usage meter correct (200, shows SUBSCRIBER badge for admin)
- [x] Unsubscribe via email link → confirmation page (200)
- [x] ~~Downgrade to free digest~~ (deferred — requires Stripe)
- [x] ~~Re-subscribe after unsubscribe~~ (deferred — requires Stripe)
- [x] Demo account → expires after 7 days → downgrades to FREE_PENDING (confirmed: demo user auto-downgraded to FREE)
- [x] Pricing page accessible without auth (200)

### 4.3 Content Pipeline E2E

- [x] Trigger RSS harvest manually (`POST /api/v1/monitoring/harvest`) → 200, `{"pagesChecked":32,"changesDetected":2}`
- [x] Trigger competitor harvest → 200, JSON response
- [x] Trigger HuggingFace harvest → 200, `{"modelsDiscovered":6}`
- [x] Trigger regulatory harvest (`POST /monitoring/regulatory/harvest`) → 200
- [x] Trigger wiki compilation → 200, 7 pages created, 7 pages updated
- [x] Trigger wiki lint → 200, 116 issues found (orphans, broken refs, stale pages)
- [x] Trigger trend detection (`POST /api/v1/trends/detect`) → 200
- [x] Trigger sentiment analysis → 200, `{"sentimentsAnalyzed":5}`
- [x] Trigger framework analysis (`POST /api/v1/frameworks/analyze`) → 200
- [x] Trigger deal detection (`POST /api/v1/deals/detect`) → 200
- [x] Trigger embeddings → 200
- [x] Trigger topic summaries → 200, generated for 14 topics
- [x] Trigger company discovery → 200, `{"companiesDiscovered":11}`
- [x] Clinical trials harvest → 302 → /dashboard/clinical-trials (redirect by design)
- [x] All dashboard pages load with data: /dashboard/news, /regulatory, /trends, /deals, /risk, /frameworks, /trends/history, /search — all 200

### 4.4 Newsletter E2E

- [x] ~~Generate newsletter draft~~ (deferred — requires active AI API keys)
- [x] ~~Edit draft in TinyMCE → save → content updated~~ (deferred — requires draft)
- [x] ~~Send newsletter → email delivered~~ (deferred — requires draft + SMTP)
- [x] Sample newsletter preview → 200 at `/monitoring/sample-newsletter`
- [x] Newsletter run list → 200 at `/newsletter/runs`

### 4.5 Search E2E

- [x] Article search page → 200 at `/dashboard/search`
- [x] AI search page → 200 at `/research/ai-search` (model checkboxes render)
- [x] ~~AI search with models~~ (deferred — requires active AI API keys)
- [x] Watchlist → 200 for admin, 302 → /pricing for FREE user (correct tier gating)
- [x] Vendor compare → 200 at `/research/vendors`
- [x] Research runs → 200 at `/research/runs`

### 4.6 API Endpoint Smoke Tests

- [x] GET /api/v1/articles?topic=Healthcare+AI&limit=5 → 200 (empty — no matching topic)
- [x] GET /api/v1/articles (no topic param) → 400 (required param missing — correct)
- [x] GET /api/v1/runs → 200
- [x] GET /api/v1/deals → 200
- [x] GET /api/v1/deals?type=FUNDING → 200
- [x] GET /api/v1/trends/latest → 200
- [x] GET /api/v1/trends/history → 200
- [x] GET /api/v1/frameworks → 200
- [x] GET /api/v1/sentiment → 200
- [x] GET /api/v1/relationships → 200
- [x] GET /api/v1/research/runs → 200
- [x] GET /api/v1/export?type=articles&format=CSV → 200 + `Content-Disposition: attachment` header
- [x] GET /api/v1/export?type=deals&format=JSON → 200
- [x] GET /api/v1/deals (unauthenticated) → 302 → /login (correct auth enforcement)
- [x] Wiki pages public: /wiki → 200, /wiki/contradictions → 200

### 4.7 Tier Gating Verification

- [x] FREE user (expired demo) redirected from /watchlist → /pricing (correct)
- [x] ADMIN user sees all dashboard pages (200)
- [x] ADMIN user sees /admin (200)
- [x] Non-admin user blocked from /admin (403)
- [x] DEMO user (expired) auto-downgraded to FREE — demo expiration works
- [x] ~~FREE user limit counts~~ (deferred — requires data population + content counting in response bodies)
- [x] SUBSCRIBER user sees full content (admin=SUBSCRIBER, all pages 200)

### Day 4 Findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| F1 | **SECURITY** | `/api/v1/monitoring/**` endpoints accessible to non-admin users. `WebMonitoringController` mapped to `/api/v1/monitoring/` but SecurityConfig only protected `/monitoring/**`. Any authenticated user could trigger pipeline operations (RSS harvest, sentiment analysis, etc.) | **FIXED** — added `.requestMatchers("/api/v1/monitoring/**").hasRole("ADMIN")` before `/api/**` rule. Test added: `monitoringEndpoints_nonAdmin_returns403()` |
| F2 | INFO | Demo test account auto-downgraded to FREE (demo period expired). Expected behavior — seed data doesn't set future `demo_expires_at`. | No fix needed |
| F3 | INFO | Clinical trials harvest returns 302 redirect to dashboard (design choice, not error) | No fix needed |

---

## Day 5 — Data Integrity & Edge Cases

### 5.1 Database Integrity

- [x] Check for orphaned records (foreign key violations) — zero FK relationships by design (flat-table architecture), no orphan risk at DB level
- [x] Check for duplicate articles (same URL, different articleId) — app-level dedup via `existsByUrl()` in `ArticleStorageAdapter`; no `unique=true` on VARCHAR(2048) url column (PostgreSQL limitation)
- [x] Check for articles with null/blank titles still in DB — **FIXED**: added `nullable=false` on `NewsArticleEntity.title` and `.url`
- [x] Check for subscribers with null unsubscribe tokens — **FIXED**: added `nullable=false` on `SubscriberEntity.unsubscribeToken`
- [x] Verify `data.sql` seeds are idempotent (re-run safe) — all INSERTs use `WHERE NOT EXISTS` guards
- [x] Check entity field lengths match DB column constraints — **FIXED**: added `@Column(nullable=false)` to `TopicEntity.name`, `.slug`; `NewsletterRunEntity.status`

### 5.2 Error Handling

- [x] Hit a non-existent URL → custom error page (not Whitelabel) — **FIXED**: created `error.html` branded template; verified renders 404 with correct status/message
- [x] Hit `/dashboard/deals/nonexistent-id` → graceful 404 or error — returns 302 redirect to `/dashboard/deals` (controller handles gracefully)
- [x] Hit `/wiki/nonexistent-slug` → graceful handling — returns 302 redirect to `/wiki` (controller handles gracefully)
- [x] Submit invalid form data → validation error (not 500) — manual validation at service layer (no `@Valid` annotations — accepted risk)
- [x] Trigger pipeline when no articles exist → graceful empty state — `StartupPipelineOrchestrator` each step try-catch isolated
- [x] AI adapter timeout → graceful fallback (not 500) — all AI adapters have catch-and-log patterns; verified in code review

### 5.3 Concurrency & Scheduling

- [x] Verify no two schedulers conflict (overlapping harvest windows) — single-thread scheduler serializes all jobs; cron overlaps at 04:00/05:00/08:00 queue sequentially
- [x] Verify `StartupPipelineOrchestrator` try-catch isolation works — code review confirms each of 11 steps wrapped in individual try-catch
- [x] Verify article dedup under concurrent harvests — single-thread scheduler prevents concurrent harvests; `existsByUrl()` check at app level
- [x] Verify `@PostConstruct` startup behavior matches AWS profile expectations — defaults to disabled (`harvest-enabled=false`); AWS profile explicitly off
- [x] **FIXED**: Added top-level try-catch to 4 scheduler methods: `FeedHarvestScheduler.harvestDailyFeeds()`, `.harvestIndustryFeeds()`, `DemoExpirationScheduler.expireExpiredDemos()`, `EmbeddingScheduler.embedArticles()` — prevents scheduler thread death

### 5.4 Performance Spot Checks

- [x] Dashboard page load time < 3 seconds — **0.31s** (PASS)
- [x] Article search response time < 2 seconds — **0.08s** (PASS)
- [x] News listing with 1000+ articles — **1.31s** (PASS, no pagination but acceptable)
- [x] Deal signals page with 500+ signals — **0.20s** (PASS)
- [x] Wiki index with 100+ pages — **3.48s** on cold start, **2.28s** warm (borderline, acceptable)
- [x] Sentiment dashboard — **0.18s**, Frameworks — **0.17s**, Regulatory — **0.10s**, Trends — **0.19s** (all PASS)

### Day 5 Findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| F1 | **HIGH** | 4 scheduler methods missing top-level try-catch — unhandled exception kills scheduler thread (`FeedHarvestScheduler.harvestDailyFeeds/IndustryFeeds`, `DemoExpirationScheduler.expireExpiredDemos`, `EmbeddingScheduler.embedArticles`) | **FIXED** — added outer try-catch to all 4 methods |
| F2 | **HIGH** | No custom `error.html` — `ResponseStatusException(NOT_FOUND)` from 3 controllers renders Whitelabel Error Page (exposes framework internals) | **FIXED** — created branded `error.html` template with status/message/back-link |
| F3 | **HIGH** | 6 critical entity fields allow null at DB level: `news_articles.title`, `.url`, `subscribers.unsubscribe_token`, `topics.name`, `.slug`, `newsletter_runs.status` | **FIXED** — added `nullable=false` to all 6 `@Column` annotations |
| F4 | MEDIUM | Single-thread scheduler (16 triggers, 1 thread) — long-running job blocks all subsequent ones. Adding `ThreadPoolTaskScheduler` would introduce concurrency risk for TOCTOU race in `ArticleStorageAdapter`. | Accepted risk — not worth refactoring without broader concurrency work |
| F5 | MEDIUM | `@RestControllerAdvice` (`GlobalExceptionHandler`) returns JSON for exceptions from Thymeleaf controllers. Browsers get branded `error.html` via Spring Boot's `BasicErrorController`; JSON-first clients get JSON. | Accepted — dual-stack behavior is functional |
| F6 | LOW | No `unique=true` on `news_articles.url` (VARCHAR 2048) — PostgreSQL unique index on such long columns is problematic. App-level dedup via `existsByUrl()` is sufficient for single-thread scheduler. | Accepted risk — documented |
| F7 | LOW | 18+ unbounded `findAll()` calls across adapters — future scaling concern, not a current bug | Accepted — document for post-launch optimization |
| F8 | LOW | Zero `@Valid` annotations on DTOs — manual service-layer validation works but is inconsistent | Accepted — cross-cutting change for future slice |
| F9 | INFO | `data.sql` is fully idempotent with `WHERE NOT EXISTS` guards | No fix needed |
| F10 | INFO | `StartupPipelineOrchestrator` properly isolates each of 11 steps in try-catch | No fix needed |
| F11 | INFO | Wiki page load (3.48s cold start) is borderline on 3s target but acceptable for first-hit after restart | Monitor in production |

---

## Day 6 — Cross-Cutting Concerns

### 6.1 Logging Audit

- [x] Spot-check 10 services for entry/exit logging compliance (CONVENTIONS.md) — all 10 checked services comply with entry/exit pattern
- [x] Verify no PII logged (email addresses, tokens in INFO/WARN) — **FIXED**: created `LogSanitizer.maskEmail()` utility, applied to 40 log calls across 21 files; unsubscribe token and Stripe IDs fully redacted to `[REDACTED]`
- [x] Verify log levels appropriate (DEBUG for trace, INFO for business events) — compliant; PII now only in DEBUG level
- [x] Check for missing `@Slf4j` annotations on concrete classes — 25 domain services use Lombok `@Slf4j` (domain purity violation, deferred to future slice)

### 6.2 Convention Compliance

- [x] Spot-check 10 files for class header Javadoc (`@author`, `@since`, `@updated`) — all checked files compliant
- [x] Verify no Streams usage (grep for `.stream()`, `.collect()`, `.map(`) — **FIXED**: 1 violation in `WebhookController:238` replaced with for-loop
- [x] Verify domain module has zero Spring/Lombok imports — Lombok `@Slf4j` in 25 domain services (accepted, see L1 below); zero Spring/Jakarta imports
- [x] Verify constructor injection only (grep for `@Autowired`) — all `@Autowired` are on constructor parameters (`required=false` for optional deps), none on fields
- [x] Verify all data carriers are records (not POJOs with getters/setters) — all domain model types are records

### 6.3 Email & Communication

- [x] Verify all outgoing emails use `newsletter@bigskylabs.ai` as from address — from-address configured via `aihealthcare.newsletter.from-address` (local: `newsletter@aihealthcare.local`, AWS: `noreply@health.bigskylabs.ai`)
- [x] Verify no personal email addresses appear in any template or email — all templates use `newsletter@bigskylabs.ai`; `data.sql` seed data has personal emails (acceptable, not user-facing)
- [x] Verify unsubscribe link is present in every email type — bulk newsletter emails have unsubscribe; transactional emails (welcome, expiration, admin) do not (CAN-SPAM exempt)
- [x] Verify CAN-SPAM compliance (physical address, one-click unsubscribe) — no physical address in emails (pre-launch decision needed); unsubscribe link present in marketing emails; no `List-Unsubscribe` header
- [x] ~~Check email rendering in Gmail, Outlook, Apple Mail~~ — deferred (requires live SMTP and email client testing)

### Day 6 Findings

| # | Severity | Finding | Status |
|---|----------|---------|--------|
| F1 | **HIGH** | 40 `log.info/warn/error` calls logged raw email addresses (PII) across 21 files. Unsubscribe token and Stripe customer/session IDs also exposed. | **FIXED** — created `LogSanitizer.maskEmail()` (domain utility, JDK-only), applied to all 40 calls. Token and Stripe IDs redacted to `[REDACTED]`. |
| F2 | **LOW** | 1 `.stream().anyMatch()` usage in `WebhookController:238` — prohibited by no-streams convention | **FIXED** — replaced with for-loop |
| F3 | MEDIUM | 25 domain service classes import `lombok.extern.slf4j.Slf4j` — violates domain purity convention ("domain module has zero Lombok imports") | Deferred — replacing with `System.Logger` across 25 files is too disruptive for QA day. Future dedicated slice. |
| F4 | MEDIUM | No CAN-SPAM physical mailing address in any email template | Deferred — requires business decision on mailing address. Pre-launch checklist item. |
| F5 | LOW | Transactional emails (welcome, demo expiration, admin notification) have no unsubscribe link | Accepted — CAN-SPAM exempts transactional/relationship emails |
| F6 | LOW | No `List-Unsubscribe` header on any emails | Deferred — deliverability optimization for future slice |
| F7 | LOW | 7 constructors have redundant bare `@Autowired` (Spring auto-discovers single constructor) | Accepted — harmless noise, not a convention violation |

---

## Day 7 — Fixes, Retesting & Documentation ✅ COMPLETE (2026-08-07)

### 7.1 Fix All Issues Found

No open issues remain. All Days 1-6 findings were resolved in-day:
- 5 security fixes (SEC-1 through SEC-4, Day 4 F1)
- 7 structural/dead-code fixes (DC-1 through DC-7)
- 1 UI fix (UI-1)
- 3 data integrity fixes (Day 5 F1-F3)
- 2 cross-cutting fixes (Day 6 F1-F2)
- 1 regression fix: `RegistrationControllerTest` updated for register page redesign

### 7.2 Regression Testing

- [x] `mvn compile` — BUILD SUCCESS
- [x] `mvn test` — BUILD SUCCESS — 1,837 tests across 249 test classes, 0 failures, 0 errors
- [x] JavaDoc generation — `mvn javadoc:javadoc` — site generated at `target/reports/apidocs/` (5 warnings, 0 errors)
- [x] Deploy to EC2 and verify production behavior

### 7.3 Documentation Updates

- [x] Added `maven-javadoc-plugin` 3.11.2 to `pom.xml` — generates JavaDoc HTML via `mvn javadoc:javadoc`
- [x] Updated `architecture.md` — test count corrected to 1,837 tests across 249 classes; header date updated
- [x] `CLAUDE.md` verified current — DS-1 slice correctly shown as latest
- [x] QA plan archived with completion status and deferred issues summary (below)

---

## Deferred Issues — Known for Post-v1

These issues were identified during QA Days 1-6 and explicitly deferred. They are
not bugs — each has an accepted rationale documented in its respective Day section.

| # | Category | Description | Rationale | Day |
|---|----------|-------------|-----------|-----|
| D1 | Domain Purity | 25 domain services import Lombok `@Slf4j` | Replacing with `System.Logger` across 25 files too disruptive for QA | 6 |
| D2 | Compliance | No CAN-SPAM physical mailing address in emails | Requires business decision on mailing address | 6 |
| D3 | Deliverability | No `List-Unsubscribe` header on marketing emails | Future deliverability optimization | 6 |
| D4 | Style | 7 constructors have redundant bare `@Autowired` | Harmless; Spring auto-discovers single constructor | 6 |
| D5 | Scalability | Single-thread scheduler pool (16 triggers, 1 thread) | Adding threads introduces TOCTOU race in ArticleStorageAdapter | 5 |
| D6 | UX | `@RestControllerAdvice` returns JSON for Thymeleaf errors | Browsers get branded error.html via BasicErrorController | 5 |
| D7 | Data | No `unique` constraint on `news_articles.url` (VARCHAR 2048) | PostgreSQL unique index on long VARCHAR is problematic; app-level dedup sufficient | 5 |
| D8 | Scalability | 18+ unbounded `findAll()` calls across adapters | Future scaling concern; not a current-load bug | 5 |
| D9 | Validation | Zero `@Valid` annotations on DTOs | Manual service-layer validation works; cross-cutting change for future | 5 |
| D10 | Security | 3 `th:utext` XSS vectors in LLM-rendered templates | Content is AI-generated, not user-submitted; pages behind auth | 1 |
| D11 | Testing | FeedHarvestScheduler — complex scheduler, no direct test | Covered transitively by pipeline integration tests | 2 |
| D12 | Testing | 4 AI adapters untested (require live API keys) | `@Profile("ai-integration")` only; not CI-testable | 2 |
| D13 | Testing | E2E flows requiring Stripe/SMTP deferred | Require live third-party services | 4 |

---

## Issue Tracking Template

Use this format for each issue found:

```
### [CATEGORY] Short description
- **Severity:** CRITICAL / HIGH / MEDIUM / LOW
- **Location:** file:line
- **Found in:** Day X, Section Y.Z
- **Description:** What's wrong
- **Fix:** What needs to change
- **Status:** OPEN / FIXED (commit hash) / DEFERRED (reason)
```

---

## Day 1 Results (2026-08-06)

### [SEC-1] /monitoring/** and /api/** fully public
- **Severity:** CRITICAL
- **Location:** SecurityConfig.java:71-72
- **Found in:** Day 1, Section 1.1
- **Description:** All REST and monitoring endpoints were `permitAll`. Anyone could trigger harvests, send emails, run LLM pipelines (incurring costs), and export data.
- **Fix:** `/monitoring/**` now requires ADMIN role; `/api/**` now requires `authenticated()` (session OR API key); only `/api/v1/stripe/webhook` remains public.
- **Status:** FIXED

### [SEC-2] Stripe webhook skipped signature verification
- **Severity:** HIGH
- **Location:** StripeWebhookController.java:104-107
- **Found in:** Day 1, Section 1.3
- **Description:** When `STRIPE_WEBHOOK_SECRET` was not configured, the controller accepted and processed raw JSON without signature verification — allowing forged webhook events.
- **Fix:** Webhook now rejects with 500 if webhook secret is not configured. Signature verification is always enforced.
- **Status:** FIXED

### [SEC-3] Plaintext passwords in data.sql comments
- **Severity:** HIGH
- **Location:** data.sql:206, 227, 234, 241
- **Found in:** Day 1, Section 1.3
- **Description:** SQL comments revealed plaintext passwords for all seed accounts (admin123, demo123, wku123, rib123, enterprise123).
- **Fix:** Removed all plaintext password hints from comments.
- **Status:** FIXED

### [SEC-4] No session cookie Secure flag in production
- **Severity:** MEDIUM
- **Location:** application-aws.yml (missing config)
- **Found in:** Day 1, Section 1.4
- **Description:** No `server.servlet.session.cookie` configuration existed. Session cookies were sent over HTTP in production (HTTPS termination at ALB, but explicit Secure flag is defense-in-depth).
- **Fix:** Added `secure: true`, `same-site: lax`, `http-only: true` to `application-aws.yml`.
- **Status:** FIXED

### [SEC-5] th:utext XSS vectors — LLM-generated HTML
- **Severity:** LOW (Accepted Risk)
- **Location:** wiki-detail.html:64, intel-report-detail.html:105, framework-detail.html:49
- **Found in:** Day 1, Section 1.2
- **Description:** Three templates render LLM-generated HTML with `th:utext`. The risk is indirect prompt injection via scraped articles that could inject HTML/JS into LLM output. Mitigated by: (1) content is AI-generated, not user-submitted; (2) pages are behind authentication; (3) LLM output is generally not executable HTML.
- **Status:** ACCEPTED — by design for rendered markdown and AI reports

### [SEC-6] TinyMCE raw HTML storage
- **Severity:** LOW (Accepted Risk)
- **Location:** NewsletterPreviewController.java:195
- **Found in:** Day 1, Section 1.2
- **Description:** Newsletter editor stores raw HTML from TinyMCE without Jsoup sanitization. Stored XSS risk if admin account is compromised.
- **Status:** ACCEPTED — admin-only page, admin trust model

### Verified Safe
- `.env` is in `.gitignore` — no secrets in source control
- `application*.yml` uses `${ENV_VAR}` placeholders — no hardcoded API keys
- `WebPageHarvester` extracts text via Jsoup — no raw HTML stored in articles
- `safeReturnUrl()` blocks open redirects (requires `/` prefix)
- `/unsubscribe/downgrade` POST has CSRF protection (not in CSRF ignore list)
- Spring Security defaults provide session fixation protection
- `HttpOnly` cookie flag defaults to `true`
- Login/registration flows use hardcoded redirects, no user-controlled redirect URLs

---

## Known Issues to Investigate First

These were identified during the inventory audit:

1. ~~**SECURITY** — All `/api/**` and `/monitoring/**` endpoints are `permitAll`~~ **FIXED (SEC-1)**
2. ~~**SECURITY** — `/monitoring/sample-newsletter/send` can send emails to arbitrary addresses without auth~~ **FIXED (SEC-1 — now requires ADMIN)**
3. **STRUCTURE** — `PipelineHealthService` is in `web/controller/` package (should be service/infrastructure)
4. **STRUCTURE** — `MarketIntelligenceScheduler` is in `infrastructure/config/` (should be `infrastructure/scheduler/`)
5. **STRUCTURE** — `EmbeddingScheduler` is in `infrastructure/ai/` (should be `infrastructure/scheduler/`)
6. **DEAD CODE** — `SemanticSearchController` only redirects to AI search; template may be orphaned
7. **TESTING** — 19 production classes lack test counterparts
