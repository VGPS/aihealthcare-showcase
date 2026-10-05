# Admin Area — AIHealthcare Reference

> Last updated: 2026-10-05

The Admin Area is the operational back office for the app owner. It covers four concerns: user account management and system analytics (`/admin`), SAML SSO identity provider configuration (`/admin/sso`), a CRM for tracking outreach to healthcare AI companies (`/admin/outreach`), and a pipeline operations console that lets the owner manually trigger, monitor, and audit every background data pipeline (`/admin/pipelines`). A separate but related area, `/enterprise/data`, is the self-service console for ENTERPRISE-tier customers to run and schedule data export jobs. All admin pages are restricted to users with the ADMIN role; `/enterprise/data` is restricted by subscription tier rather than role.

---

## Pages

### `/admin` — User Management + System Analytics

**What it shows:**
The full list of registered users (`AppUser` records) with email, role, enabled flag, subscription tier, and demo expiration date. Summary counts for admins, regular users, and disabled accounts are computed in-memory from that list. Below the user table, it shows ingestion analytics: total article count, articles ingested in the last 30 days, a daily-count breakdown for the last 30 days, and a top-10 topic distribution — both rendered as Chart.js charts. It also shows total newsletter run count and subscriber count.

**Data source:**
- Users: `app_users` table via `AppUserPort.findAll()`.
- Article counts and topic distribution: `news_articles` table via `GetAnalyticsUseCase`.
- Newsletter run count: `newsletter_runs` table via `GetAnalyticsUseCase`.
- Subscriber count: `subscribers` table via `SubscriberPort.findAll().size()` (full table scan).

**Access / tier gating:**
ADMIN role required. SecurityConfig blocks all `/admin/**` routes to non-admins. No tier check — any ADMIN user regardless of subscription tier can reach this page.

**How data gets here:**
Users arrive via self-registration (`RegistrationController`), Stripe webhook (`StripeWebhookController`), and SAML JIT provisioning. Articles are written by every harvester: `FeedHarvestScheduler` (RSS feeds, daily at 04:00 UTC), `WebMonitoringScheduler` (competitor pages at 05:00, HuggingFace at 05:30), `ResearchHarvestScheduler` (Perplexity COMBINED mode, daily at 04:00), and `RegulatoryHarvestScheduler` (FDA/CMS, daily at 04:30). Newsletter runs are written by `NewsletterGenerationScheduler` (daily at midnight UTC).

**Actions available:**
- `POST /admin/users/{email}/toggle-enabled` — toggles the enabled flag on a user account. Blocked if the acting admin tries to disable their own account.
- `POST /admin/users/{email}/change-role` — changes a user's role string. Same self-modification guard.

---

### `/admin/sso`, `/admin/sso/new`, `/admin/sso/{id}/edit` — SSO Identity Provider Management

**What it shows:**
The list of all configured SAML2 identity providers (`SsoIdentityProvider` records), each showing: registration ID (the natural primary key, e.g. `okta-prod`), label, entity ID, SSO URL, X.509 certificate, optional metadata URL, email attribute name, display name attribute, active flag, and last updated date. The create/edit form exposes all of these fields.

**Data source:**
`sso_identity_providers` table via `ManageSsoProvidersUseCase.getAll()`. The `updatedAt` timestamp is formatted server-side to `"MMM d, yyyy"` in America/Chicago and passed as a separate map to the template to avoid Thymeleaf `Instant` formatting issues.

**Access / tier gating:**
ADMIN role required (same `/admin/**` SecurityConfig rule). Every provider created here is hard-coded to `SubscriptionTier.ENTERPRISE` — only ENTERPRISE-tier users can authenticate via SSO.

**How data gets here:**
All SSO provider records are manually created and maintained by the admin. No scheduler or pipeline writes to this table. The companion `sso_provisioning_events` table (not shown on this page) is written by the Spring Security SAML filter when a user logs in via SSO for the first time (JIT provisioning).

**Actions available:**
- `POST /admin/sso` — create new IdP.
- `POST /admin/sso/{id}` — update existing IdP (preserves original `createdAt`, sets `updatedAt = now()`).
- `POST /admin/sso/{id}/delete` — delete an IdP; `IllegalArgumentException` is caught and shown as a flash error.

---

### `/admin/outreach`, `/admin/outreach/{slug}` — Company Outreach CRM

**What it shows:**
The list page shows all `CompanyOutreach` records from the `company_outreach` table: company slug, outreach purpose (`OutreachPurpose` enum), status (`OutreachStatus` enum), notes, and contacted-at date. A combo box populated from the companies directory (`healthcare_ai_companies` table, ordered by name) lets the admin add a new outreach record by company name. A live contact preview below the combo box shows existing contacts for the selected company via a JSON endpoint.

The company detail page (`/admin/outreach/{slug}`) shows all outreach rows for that company, the full contact list from `company_contacts`, and — if the company is not yet in the public directory — a "Register to Directory" inline form.

**Data source:**
- Outreach records: `company_outreach` table via `ManageOutreachUseCase`.
- Contacts: `company_contacts` table via `ManageOutreachUseCase`.
- Company dropdown: `healthcare_ai_companies` table via `HealthcareAiCompanyPort.findAllByOrderByName()`.
- Directory membership check: `HealthcareAiCompanyPort.findBySlug()`.
- Live contact preview JSON: `GET /admin/outreach/contacts-json?slug=` — returns contacts for a slug as JSON, consumed by client-side JavaScript.

**Access / tier gating:**
ADMIN role required. No tier check. The contacts-json endpoint has no per-owner scoping — any admin can read any company's contacts.

**How data gets here:**
All outreach and contact records are entered manually by the admin. The company combo box is populated from the `healthcare_ai_companies` table, which is written by `CompanyDiscoveryScheduler` (weekly) and the `/api/v1/companies/discover` REST endpoint.

**Actions available:**
- `POST /add` — add a new outreach record (slug is trimmed and lowercased before save).
- `POST /{id}/status`, `POST /{id}/notes` — update status or notes on an outreach record.
- `POST /{id}/delete` — delete an outreach record; redirects to the list page (not the detail page).
- `POST /{slug}/contacts/add` — add a contact person with name, title, email, LinkedIn URL, source, and notes.
- `POST /contacts/{contactId}/status`, `POST /contacts/{contactId}/email`, `POST /contacts/{contactId}/delete` — update or remove individual contacts.
- `POST /{slug}/register-company` — create a minimal company record in the public directory (name, slug, optional domain/description). Visible only when the company is not already in the directory.

---

### `/admin/pipelines` — Pipeline Operations Console

**What it shows:**
Twenty-four pipeline cards, each displaying: name, description, schedule, trigger URL, HTTP method, estimated duration, performance impact, current last-run status (SUCCESS / FAILED / PARTIAL), last-run duration and items processed, any pre-flight warnings (missing API keys, DB issues), and a manual trigger button. A recent history table at the bottom shows the last 50 pipeline run events from the `pipeline_run_events` table, including error classification by type (LLM_AUTH, LLM_QUOTA, NETWORK, FATAL, UNKNOWN) and responsible provider (Anthropic, OpenAI, Perplexity, Gemini, Alpaca).

**Data source:**
- Pipeline card definitions: built statically in `AdminPipelineController.buildPipelineList()`.
- Pre-flight warnings: `PipelineHealthService.preFlightCheckAll()` — checks API key presence, DB connectivity, etc.
- Last-run records: `PipelineHealthService.getAllLastRuns()` — combines `pipeline_run_events` table data with an in-memory cache.
- Recent history: `PipelineHealthService.getRecentHistory(50)` — last 50 rows from `pipeline_run_events`.

**Access / tier gating:**
ADMIN role required. No tier check.

**How data gets here:**
The `pipeline_run_events` table is written by two paths: `StartupPipelineOrchestrator` writes a record for every cascade step during scheduled and startup runs; `AdminPipelineController.recordRun()` writes a record when the browser's JavaScript reports the result of a manual trigger back to `POST /admin/pipelines/record/{id}`.

**Trigger mechanism:**
Manual trigger buttons in the browser call each pipeline's native endpoint directly (e.g., `POST /monitoring/harvest`). After the call completes, the JavaScript posts the result (status, message, duration) to `/admin/pipelines/record/{id}` to persist the outcome and update the in-memory last-run cache. A few pipelines have direct handlers in `AdminPipelineController` itself: newsletter draft generation, digest delivery, market digest generation, price-reaction capture, trend detection, frontier claim detection (with configurable lookback days), and NotebookLM sync.

**Special notes:**
- `legislation-seed` has no trigger URL and no manual button — it fires automatically on every startup via `StateLawSeedRunner` `@Order(1)`.
- `notebooklm-sync` spawns `ssh.exe` and `scp.exe` processes and only works on the developer's local machine (requires PEM key at a hardcoded path; returns 503 if absent).
- `trend-detection` runs synchronously and is documented to take 15–20 minutes due to Perplexity Deep Research calls.
- If any pipeline's Spring bean is missing from the context (`@Autowired(required = false)` pattern), that step is silently skipped with no error.

---

### `/enterprise/data` — Enterprise Data Console

**What it shows:**
Three tabs for ENTERPRISE-tier customers: a Feeds tab listing available data feeds (label, description, kind, supported formats, max row limit); a Jobs tab showing the last 50 export jobs for this user (status, row count, byte size, submitted/completed timestamps, download link if succeeded); and a Schedules tab listing push delivery schedules (cron expression, recipients, last status, next run time). When jobs are in flight (status QUEUED or RUNNING), the jobs table auto-polls via HTMX. Log output for running jobs streams incrementally via `GET /enterprise/data/jobs/{jobId}/log-tail?from=N`.

**Data source:**
- Feeds: `enterprise_data_feeds` table via `RequestEnterpriseDataUseCase.listFeeds(ownerEmail)`.
- Jobs: `enterprise_data_jobs` table via `listJobs(ownerEmail, 0, 50)`. Capped at 50 with no pagination.
- Schedules: `enterprise_push_schedules` table via `ManageDataPushSchedulesUseCase.list(ownerEmail)`.

**Access / tier gating:**
ENTERPRISE subscription tier required (SecurityConfig gates `/enterprise/data` separately from `/admin/**`). This is tier-gated, not role-gated. All data queries are owner-scoped by the logged-in user's email (`principal.getName()`) — users see only their own jobs and schedules. Ownership is enforced in the use-case layer: a job belonging to another user returns 404, not 403.

**How data gets here:**
Jobs are created via `POST /api/v1/enterprise/data/jobs` (REST). Push schedules fire via `EnterpriseDataPushScheduler` (per-minute DB sweeper with atomic claim), which creates jobs automatically per configured cron. `EnterpriseDataJobReaper` (every 5 minutes) marks stale RUNNING jobs as FAILED. `EnterpriseDataRetentionScheduler` (daily at 03:15 UTC) deletes expired artifacts and marks old jobs EXPIRED.

**Artifact downloads:**
Completed job artifacts are available at `GET /d/{token}` (`SignedDownloadController`), which uses HMAC-SHA256 token verification and requires no login. Token TTL is configurable but is not displayed on the console page.

---

## How Data Gets In

The admin area reflects data produced by a set of background schedulers. Here is how each scheduler connects to what the admin sees:

| Scheduler | Frequency | What it writes | Visible in admin area at |
|---|---|---|---|
| `FeedHarvestScheduler` | Daily 04:00 UTC (RSS); every 4h (industry feeds) | `news_articles` | `/admin` article counts and charts; triggers full pipeline cascade |
| `ResearchHarvestScheduler` | Daily 04:00 UTC | `news_articles`, `research_runs` | `/admin` article counts |
| `WebMonitoringScheduler` | Daily 05:00 (competitors), 05:30 (HuggingFace) | `news_articles`, `page_content_hashes` | `/admin` article counts |
| `RegulatoryHarvestScheduler` | Daily 04:30 UTC | `regulatory_events`, `news_articles` | `/admin` article counts; `/admin/pipelines` last-run status |
| `EmbeddingScheduler` | Daily 07:00 UTC | Vector store (in-memory / pgvector) | `/admin/pipelines` last-run status |
| `NewsletterGenerationScheduler` | Daily 00:00 UTC | `newsletter_runs` | `/admin` run count |
| `StartupPipelineOrchestrator` | On RSS harvest completion; startup (if enabled) | `pipeline_run_events` for all 15 steps | `/admin/pipelines` last-run status and history table |
| `DealSignalScheduler` | Every 6 hours | `deal_signals` | `/admin/pipelines` last-run status |
| `MarketAnalysisScheduler` | Daily 12:00 UTC (digest); hourly (price reactions) | `market_digest`, `price_reaction_snapshots` | `/admin/pipelines` last-run status |
| `LegislationMonitorScheduler` | Monday 10:30 UTC (source check), 11:00 UTC (discovery) | `state_law_sources`, `law_change_events`, `new_bill_candidates` | `/admin/pipelines` last-run status |
| `EnterpriseDataPushScheduler` | Every 1 minute (DB sweeper) | `enterprise_data_jobs`, `enterprise_push_schedules` | `/enterprise/data` jobs and schedules |
| `EnterpriseDataJobReaper` | Every 5 minutes | `enterprise_data_jobs` (status updates) | `/enterprise/data` job status |
| `EnterpriseDataRetentionScheduler` | Daily 03:15 UTC | Deletes expired artifacts and job records | `/enterprise/data` job list |

`CompanyDiscoveryScheduler` also runs weekly and writes to `healthcare_ai_companies`, which is what the `/admin/outreach` company combo box reads from.

---

## Cross-References

- `/admin` article counts reflect the same articles visible on `/dashboard/news` and searchable at `/research/ai-search`.
- `/admin/outreach/{slug}/register-company` writes to `healthcare_ai_companies` — companies added this way appear immediately on the public `/directory` page, though with minimal data until the discovery pipeline runs.
- `/admin/sso` providers feed Spring Security's SAML2 authentication filter. Users provisioned via SSO appear in the `/admin` user table with ENTERPRISE tier.
- `/admin/pipelines` pipeline cards map 1-to-1 to feature pages: `rss-feeds` → `/dashboard/news`, `market-digest` → `/dashboard/market`, `claim-detection` → `/dashboard/claims`, `framework-analysis` → `/dashboard/frameworks`, `deal-signals` → `/dashboard/deals`, `embedding` → `/research/ai-search`, `wiki-compile` → `/wiki`, `sentiment-analysis` → `/dashboard/risk`, `regulatory-harvest` → `/dashboard/regulatory`, `trend-detection` → `/dashboard/trends`.
- `/enterprise/data` artifact downloads go through `/d/{token}` (`SignedDownloadController`), which shares the HMAC signing key with the rest of the application.
- The `pipeline_run_events` table written by `StartupPipelineOrchestrator` and manual triggers is the sole data source for the pipeline history table on `/admin/pipelines` — there is no separate audit log for background cron runs beyond what is written to this table.

---

## Known Limitations

- **Subscriber count is a full table scan.** `SubscriberPort.findAll().size()` on `/admin` loads every subscriber row into memory. This will degrade at high subscriber volume. A `COUNT(*)` query would be more appropriate.
- **Role values are free-form strings.** `POST /admin/users/{email}/change-role` accepts any string. There is no server-side validation that the submitted value is a known role (`ADMIN`, `USER`). An invalid value is silently persisted.
- **SSO certificate validation is deferred.** The PEM certificate in the IdP form is saved as raw text with no validation. A malformed certificate will not cause an error at save time — it will fail silently or throw an exception the first time a user tries to authenticate via that provider.
- **SSO registration ID is immutable after creation.** The edit form does not expose `registrationId`, so it cannot be corrected without deleting and recreating the provider.
- **Outreach delete redirects to list, not detail.** Deleting an outreach record from within the company detail page (`/admin/outreach/{slug}`) redirects to the list page, losing the user's context.
- **Company slug collision on register-company.** The Register to Directory form slugifies the company name with a simple regex replacement (`[^a-z0-9]+` → `-`). It does not pre-check for collision with an existing slug, which could cause a constraint violation at save time with no user-friendly error.
- **Enterprise console job list is capped at 50 with no pagination.** Jobs beyond the most recent 50 are not visible in the console UI, though they remain in the database and are accessible via REST.
- **Log tail byte offset vs. character length mismatch.** The incremental log streaming endpoint (`/enterprise/data/jobs/{jobId}/log-tail`) returns character length as `nextOffset`, but the underlying read operates on byte offset. These diverge for any log output containing multi-byte UTF-8 characters, which could cause repeated or skipped log content in the streaming UI.
- **NotebookLM sync is dev-machine-only.** The `notebooklm-sync` pipeline card on `/admin/pipelines` is permanently non-functional in production — it requires a PEM key at a hardcoded local path and spawns native `ssh.exe`/`scp.exe` processes. It returns HTTP 503 in any environment where that file is absent.
- **Pipeline cascade steps are silently skipped if beans are absent.** All 15 steps in `StartupPipelineOrchestrator` use `@Autowired(required = false)`. A missing bean (e.g., due to a misconfigured API key that prevents a bean from initializing) will cause that step to be skipped with no visible error in the pipeline history — the step simply never appears in `pipeline_run_events`.
- **Deal signal LLM fallback is not an error.** If `DealClassificationPort` is not configured, the deal-signals pipeline falls back to keyword-only detection silently. This is by design but means the admin has no signal from the pipeline page that LLM-enhanced classification is disabled.
