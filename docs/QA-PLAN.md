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

## Day 1 — Security Audit

### 1.1 Authentication & Authorization Gaps

**All `/api/**` endpoints are `permitAll`.** This means every REST endpoint is publicly
accessible without authentication. While API key auth exists via `ApiKeyAuthenticationFilter`,
it only validates requests that *include* an `X-API-Key` header — requests without the header
pass through unauthenticated.

**Audit checklist:**
- [ ] Review every `/api/**` endpoint — which ones should require authentication?
- [ ] Categorize endpoints: public (pricing, wiki) vs protected (deals, sentiment, frameworks)
- [ ] Decide: should REST endpoints require either session auth OR API key?
- [ ] Review `ApiKeyAuthenticationFilter` — does it reject keyless requests or pass them through?
- [ ] Verify `/monitoring/**` endpoints are admin-only (currently `permitAll` — anyone can trigger harvests, send sample newsletters, compile wikis)
- [ ] Verify `/stripe/webhook` validates Stripe signatures (not just `permitAll`)
- [ ] Check if `/unsubscribe/downgrade` POST is CSRF-protected (it should be, since it's not in the CSRF ignore list)

### 1.2 Input Validation & Injection

- [ ] Audit all `@RequestParam` and `@PathVariable` inputs for injection risks
- [ ] Check Thymeleaf templates for unescaped output (`th:utext` vs `th:text`) — XSS vectors
- [ ] Review `data.sql` seed data — any secrets or PII in source control?
- [ ] Check all ChatClient/RestClient calls for prompt injection vectors (user input → LLM prompt)
- [ ] Review `WebPageHarvester` — does it sanitize scraped HTML before storage?
- [ ] Review `NewsletterPreviewController` — TinyMCE editor saves raw HTML; is it sanitized?

### 1.3 Secrets & Configuration

- [ ] Grep codebase for hardcoded API keys, passwords, tokens
- [ ] Verify `.env` is in `.gitignore`
- [ ] Review `application.yml` and `application-aws.yml` for leaked credentials
- [ ] Check if BCrypt password hashes in `data.sql` correspond to known weak passwords
- [ ] Verify Stripe webhook signature validation is actually enforced

### 1.4 CSRF & Session Security

- [ ] Map all POST/PUT/DELETE endpoints and verify CSRF protection is appropriate
- [ ] Check session fixation protection (Spring Security default should handle this)
- [ ] Review cookie settings — HttpOnly, Secure, SameSite flags
- [ ] Check for open redirect vulnerabilities in login/registration flows

---

## Day 2 — Dead Code & Structural Cleanup

### 2.1 Untested Production Classes

Write tests for or review these 19 classes that lack test counterparts:

| Class | Priority | Action |
|-------|----------|--------|
| `GlobalExceptionHandler` | HIGH | Write test — maps exceptions to HTTP status codes |
| `FooterModelAdvice` | MEDIUM | Write test — verifies model attributes injected on all pages |
| `FeedHarvestScheduler` | MEDIUM | Write test — verify scheduling + orchestrator delegation |
| `ArticleIngestionAdapter` | MEDIUM | Write test — DB query behavior |
| `NewsletterRunAdapter` | MEDIUM | Write test — entity/domain mapping |
| `NewsletterRunController` | MEDIUM | Write test — REST run retrieval |
| `RegulatoryMonitoringController` | LOW | Write test or review for removal |
| `AiSummarizationAdapter` | LOW | AI adapter — mock ChatClient test |
| `AiReportAdapter` | LOW | AI adapter — mock ChatClient test |
| `AnthropicAiSearchAdapter` | LOW | AI adapter — mock ChatClient test |
| `OpenAiSearchAdapter` | LOW | AI adapter — mock ChatClient test |
| `AnalyticsAdapter` | LOW | Write test — query aggregation |
| `CompanyEventAdapter` | LOW | Write test |
| `DocumentIngestionAdapter` | LOW | Conditional bean — verify wiring |
| `MarketIntelligenceScheduler` | LOW | Write test — scheduling |
| `NoOpArticleSearchAdapter` | LOW | Conditional fallback — minimal test |
| `NotebookLMResearchExportAdapter` | LOW | File export — write test |
| `ResearchPlanningService` | LOW | LLM delegation — mock test |
| `ResearchSynthesisService` | LOW | LLM delegation — mock test |

### 2.2 Structural Anomalies

- [ ] Move `PipelineHealthService` from `web/controller/` to `infrastructure/` or `domain/service/`
- [ ] Check for any other misplaced classes (services in controller packages, adapters in wrong packages)
- [ ] Review `MarketIntelligenceScheduler` in `infrastructure/config/` — should be in `infrastructure/scheduler/`
- [ ] Review `EmbeddingScheduler` in `infrastructure/ai/` — should be in `infrastructure/scheduler/`

### 2.3 Dead Code Scan

- [ ] Run full unused import scan (`mvn compile` warnings)
- [ ] Search for methods with zero callers outside their own class
- [ ] Search for private methods never called within their class
- [ ] Check for unused `@Bean` definitions in `AppConfig`
- [ ] Review all DTOs in `web/dto/` — are any unused?
- [ ] Check for commented-out code blocks
- [ ] Review `SemanticSearchController` — it redirects to `/research/ai-search`; is the template `semantic-search.html` still needed?
- [ ] Review `ResearchCompareController` — is the side-by-side compare still used?
- [ ] Review `developer.html` / `DeveloperPortalController` — is this active?

### 2.4 Dependency Audit

- [ ] Check `pom.xml` for unused dependencies
- [ ] Check for outdated dependencies with known CVEs (`mvn dependency:tree`)
- [ ] Verify no test-scope dependencies leak into production

---

## Day 3 — Page-by-Page UI Consistency Review

### 3.1 Navigation Consistency

Visit every page and verify:
- [ ] Nav bar present and consistent across all 46 pages
- [ ] Footer fragment present on all pages
- [ ] Active nav item highlighted correctly
- [ ] All nav links work (no 404s, no broken anchors)
- [ ] "Reference" dropdown has all expected items
- [ ] Mobile responsiveness — check nav collapse on narrow viewport

### 3.2 Template-by-Template Review

For each page, check:
- Correct page title in `<head>`
- Consistent header/intro paragraph style
- Table sort arrows work (if present)
- Tier gating works (FREE vs SUBSCRIBER)
- No broken Thymeleaf expressions (missing model attributes → 500)
- No raw `${...}` or `null` text visible
- Charts render (Chart.js pages)
- Links to detail pages work

**Dashboard cluster (7 pages):**
- [ ] `/dashboard` — analytics overview, Chart.js charts render
- [ ] `/dashboard/articles` — article list, sort works
- [ ] `/dashboard/news` — articles grouped by topic
- [ ] `/dashboard/search` — multi-field search, filter form works
- [ ] `/dashboard/trends` — rising/fading/new keyword cards
- [ ] `/dashboard/regulatory` — filter tabs, tier gating
- [ ] `/dashboard/deals` — type filter pills, sort arrows, tier gating

**Research cluster (6 pages):**
- [ ] `/research/ai-search` — model checkboxes, synthesis cards render
- [ ] `/research/compare` — side-by-side results (if still active)
- [ ] `/research/runs` — run history table
- [ ] `/research/runs/{runId}` — run detail
- [ ] `/research/vendors` — vendor checkbox grid, comparison cards
- [ ] `/research/intel` — intel reports list

**Framework & Sentiment (4 pages):**
- [ ] `/dashboard/frameworks` — radar chart + company cards
- [ ] `/dashboard/frameworks/{slug}` — 6-dimension breakdown
- [ ] `/dashboard/risk` — horizontal bar chart + company cards
- [ ] `/dashboard/risk/{slug}` — doughnut chart + article table

**Wiki cluster (5 pages):**
- [ ] `/wiki` — searchable page grid, type filter
- [ ] `/wiki/{slug}` — rendered markdown, provenance table
- [ ] `/wiki/contradictions` — reversal watch feed
- [ ] `/wiki/ask` — wiki question interface
- [ ] `/wiki/digest` — wiki digest

**Deal detail & trends (3 pages):**
- [ ] `/dashboard/deals/{signalId}` — cross-reference cards
- [ ] `/dashboard/trends/history` — multi-line chart + timeline
- [ ] `/dashboard/trends/history/{epochMillis}` — snapshot detail

**Legal & Clinical (3 pages):**
- [ ] `/dashboard/legal` — legal timeline
- [ ] `/dashboard/legal/trends` — legal trend analysis
- [ ] `/dashboard/clinical-trials` — clinical trials page

**Company pages (3 pages):**
- [ ] `/dashboard/companies` — company directory
- [ ] `/companies/{slug}` — company profile
- [ ] `/dashboard/relationships` — company relationships

**Admin pages (3 pages):**
- [ ] `/admin` — user management + system status
- [ ] `/admin/pipelines` — pipeline management
- [ ] `/newsletter/runs` — newsletter run list

**Auth & account (6 pages):**
- [ ] `/login` — login form
- [ ] `/register` — registration form
- [ ] `/choose-path` — tier selection
- [ ] `/profile` — user profile, tier badge, usage meter
- [ ] `/pricing` — tier comparison, Stripe checkout
- [ ] `/unsubscribe` — soft-landing page + downgrade

**Other (4 pages):**
- [ ] `/watchlist` — subscriber watchlist
- [ ] `/developer` — developer portal
- [ ] `/settings/webhooks` — webhook configuration
- [ ] `/access-denied` — 403 page

---

## Day 4 — End-to-End Flow Testing

### 4.1 User Registration & Login Flow

- [ ] Register new user → choose-path → login → dashboard
- [ ] Login with existing user → redirect to dashboard
- [ ] Login with wrong password → error message
- [ ] Login with disabled account → error message
- [ ] Logout → redirects to login page
- [ ] Access protected page without login → redirect to login
- [ ] Access admin page as non-admin → 403 page

### 4.2 Subscriber Lifecycle

- [ ] Subscribe via pricing page → Stripe checkout → callback → tier upgrade
- [ ] View profile → tier badge, usage meter correct
- [ ] Unsubscribe via email link → confirmation page
- [ ] Downgrade to free digest → re-activated as FREE
- [ ] Re-subscribe after unsubscribe → pricing page works
- [ ] Demo account → expires after 7 days → downgrades to FREE_PENDING

### 4.3 Content Pipeline E2E

- [ ] Trigger RSS harvest manually (`POST /monitoring/harvest`) → articles appear in `/dashboard/news`
- [ ] Trigger competitor harvest → articles appear
- [ ] Trigger HuggingFace harvest → models appear as articles
- [ ] Trigger regulatory harvest → events appear in `/dashboard/regulatory`
- [ ] Trigger wiki compilation → wiki pages created/updated
- [ ] Trigger trend detection → snapshot appears in `/dashboard/trends`
- [ ] Trigger sentiment analysis → results in `/dashboard/risk`
- [ ] Trigger framework analysis → results in `/dashboard/frameworks`
- [ ] Trigger deal detection → signals in `/dashboard/deals`

### 4.4 Newsletter E2E

- [ ] Generate newsletter draft → appears in `/newsletter/runs`
- [ ] Edit draft in TinyMCE → save → content updated
- [ ] Send newsletter → email delivered (check MailHog or real inbox)
- [ ] Sample newsletter preview → renders at `/monitoring/sample-newsletter`
- [ ] Sample newsletter send → email delivered

### 4.5 Search E2E

- [ ] Article search with filters → results match criteria
- [ ] AI search with single model → synthesis card renders
- [ ] AI search with multiple models → multiple synthesis cards
- [ ] AI search with no results → graceful "no matches" message
- [ ] Watchlist add keyword → matches appear after next harvest

### 4.6 API Endpoint Smoke Tests

- [ ] Test every REST endpoint with valid input → 200/201
- [ ] Test every REST endpoint with missing required params → 400
- [ ] Test endpoints with API key header → authenticated response
- [ ] Test export endpoints → CSV/JSON download works
- [ ] Test Stripe webhook with mock payload → processes correctly

### 4.7 Tier Gating Verification

- [ ] FREE user sees limited deals (10) with upgrade prompt
- [ ] FREE user sees limited trend snapshots (4)
- [ ] FREE user sees limited companies (5) in sentiment
- [ ] FREE user sees limited articles (7 days)
- [ ] SUBSCRIBER user sees full content
- [ ] ADMIN user sees admin pages
- [ ] DEMO user has time-limited full access

---

## Day 5 — Data Integrity & Edge Cases

### 5.1 Database Integrity

- [ ] Check for orphaned records (foreign key violations)
- [ ] Check for duplicate articles (same URL, different articleId)
- [ ] Check for articles with null/blank titles still in DB
- [ ] Check for subscribers with null unsubscribe tokens
- [ ] Verify `data.sql` seeds are idempotent (re-run safe)
- [ ] Check entity field lengths match DB column constraints

### 5.2 Error Handling

- [ ] Hit a non-existent URL → custom error page (not Whitelabel)
- [ ] Hit `/dashboard/deals/nonexistent-id` → graceful 404 or error
- [ ] Hit `/wiki/nonexistent-slug` → graceful handling
- [ ] Submit invalid form data → validation error (not 500)
- [ ] Trigger pipeline when no articles exist → graceful empty state
- [ ] AI adapter timeout → graceful fallback (not 500)

### 5.3 Concurrency & Scheduling

- [ ] Verify no two schedulers conflict (overlapping harvest windows)
- [ ] Verify `StartupPipelineOrchestrator` try-catch isolation works
- [ ] Verify article dedup under concurrent harvests
- [ ] Verify `@PostConstruct` startup behavior matches AWS profile expectations

### 5.4 Performance Spot Checks

- [ ] Dashboard page load time < 3 seconds
- [ ] Article search response time < 2 seconds
- [ ] News listing with 1000+ articles — pagination or performance OK?
- [ ] Deal signals page with 500+ signals — sort performance OK?
- [ ] Wiki index with 100+ pages — search/filter performance OK?

---

## Day 6 — Cross-Cutting Concerns

### 6.1 Logging Audit

- [ ] Spot-check 10 services for entry/exit logging compliance (CONVENTIONS.md)
- [ ] Verify no PII logged (email addresses, tokens in INFO/WARN)
- [ ] Verify log levels appropriate (DEBUG for trace, INFO for business events)
- [ ] Check for missing `@Slf4j` annotations on concrete classes

### 6.2 Convention Compliance

- [ ] Spot-check 10 files for class header Javadoc (`@author`, `@since`, `@updated`)
- [ ] Verify no Streams usage (grep for `.stream()`, `.collect()`, `.map(`)
- [ ] Verify domain module has zero Spring/Lombok imports
- [ ] Verify constructor injection only (grep for `@Autowired`)
- [ ] Verify all data carriers are records (not POJOs with getters/setters)

### 6.3 Email & Communication

- [ ] Verify all outgoing emails use `newsletter@bigskylabs.ai` as from address
- [ ] Verify no personal email addresses appear in any template or email
- [ ] Verify unsubscribe link is present in every email type
- [ ] Verify CAN-SPAM compliance (physical address, one-click unsubscribe)
- [ ] Check email rendering in Gmail, Outlook, Apple Mail (or Litmus)

---

## Day 7 — Fixes, Retesting & Documentation

### 7.1 Fix All Issues Found

- [ ] Prioritize: security fixes > data integrity > bugs > UI > dead code
- [ ] Fix each issue with a targeted commit
- [ ] Run selective tests after each fix

### 7.2 Regression Testing

- [ ] Run full test suite (`mvn test`) — all tests pass
- [ ] Re-test any page/flow where fixes were applied
- [ ] Deploy to EC2 and verify production behavior

### 7.3 Update Documentation

- [ ] Update `CLAUDE.md` with any new conventions or findings
- [ ] Update `architecture.md` if structural changes were made
- [ ] Archive this QA plan with completion status
- [ ] Document any known issues that were deferred

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
