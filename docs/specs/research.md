# Research Area -- AIHealthcare Reference

> Last updated: 2026-10-05

The Research area gives subscribers and admins tools to query the article corpus, generate on-demand intelligence reports, and compare AI vendor coverage using LLM synthesis. It has five distinct capabilities: a multi-model AI-enhanced search backed by the pgvector store (`/research/ai-search`), a vendor comparison tool (`/research/vendors`), a read-only audit log of past research pipeline runs (`/research/runs`), an on-demand competitive intelligence report generator (`/research/intel`), and an Intelligence Console (`/admin/intelligence`) that proxies into a separate Claude Healthcare Intelligence Service. The area is intended for subscribers who want deeper analysis than the news feed provides, and for the app owner to inspect what the automated research harvester is producing.

---

## Pages

### `/research/ai-search`

**What it shows:** A search form where the user enters a query, optionally selects which LLM models to include, and sets a result count (topK). On submit, the page shows a list of semantically similar articles retrieved from the vector store, followed by one synthesis card per available LLM model (summary + key findings). A "no match" notice appears for any model that found the query irrelevant to the corpus.

**Data source:** Article similarity is computed via `ArticleSearchPort.findSimilar()` against the pgvector index (populated from `news_articles`). LLM synthesis is produced by whichever of these adapters reports `isAvailable() = true` at runtime: `AnthropicAiSearchAdapter` (Claude), `OpenAiSearchAdapter` (GPT), `GeminiAiSearchAdapter`, `PerplexityAiSearchAdapter`. Results are on-demand and not persisted -- no `ResearchRun` record is written for AI searches.

**Access / tier gating:**
- Anonymous: upgrade teaser shown; no query runs.
- FREE / FREE_PENDING: access denied banner; no query runs.
- SUBSCRIBER: credit-based throttling via `TierGatingService`. Credits consumed per model per search: Claude/GPT/Gemini = 3, Perplexity Sonar = 1, any "deep" research model = 10. Monthly usage tracked against the subscriber's `UsageRecord`. topK silently capped at 20.
- ADMIN: fully unmetered; topK cap is 50.
- ENTERPRISE: credit throttling still applies (same path as SUBSCRIBER); topK cap is 50.

**How data gets here:** Articles must be harvested first (see [How Data Gets In](#how-data-gets-in)) and then embedded by `EmbeddingScheduler` (07:00 UTC daily) before they appear in search results. There is no way to search an article that has not yet been embedded.

---

### `/research/vendors`

**What it shows:** A vendor comparison form with two modes. In vendor-select mode, the user checks boxes for competitors (e.g., "Claude for Healthcare", "Google Health AI") and submits; the page returns a card per vendor with strengths, weaknesses, and a relevance score. In free-form mode, the user enters an open query; the page returns a synthesis and a numbered citation list (newest-first).

**Data source:**
- Vendor-select mode: Articles are read directly from `news_articles` filtered by COMPETITOR-tier topic via `ArticleIngestionAdapter` (legacy DB path). No Perplexity live call is made. The vendor checkbox list is derived at controller startup from `FeedSourceProperties`, keeping only entries where `FeedTier == COMPETITOR`.
- Free-form mode: `ResearchOrchestratorService` runs the full COMBINED pipeline -- AI query decomposition, Perplexity live retrieval, legacy DB retrieval, dedup via `CitationAssembler`, then `VendorAssessmentService` for structured synthesis.

All vendor compare runs are fully transient. No `ResearchRun` record is persisted for either mode. There is no history.

**Access / tier gating:** None. Any authenticated user can access this page regardless of tier.

**How data gets here:** Vendor-select results depend on `WebMonitoringScheduler` having scraped competitor pages recently (daily at 05:00 UTC). Free-form results come from Perplexity live on each request; no caching.

---

### `/research/runs`

**What it shows:** A sortable table of all persisted `ResearchRun` records, newest first by default. Columns: timestamp, query (truncated at 80 characters), mode badge (LEGACY_GOOGLE / STAGED_RESEARCH / COMBINED), and citation count. Sortable via `?sort=` query param on timestamp, query, mode, or citations.

**Data source:** `ResearchRunPort.findAll()` reads directly from the `research_runs` table via `ResearchRunAdapter`. No service layer -- it is a direct repository read. All records from all time are loaded into memory in a single query; there is no pagination.

**Access / tier gating:** None. All authenticated users can see the full run list.

**How data gets here:** A `ResearchRun` record is written by `ResearchOrchestratorService.conduct()` after every pipeline execution: both manual REST calls (`POST /api/v1/research`) and automated daily runs by `ResearchHarvestScheduler`. Automated entries and manual queries are visually indistinguishable in the list.

---

### `/research/runs/{runId}`

**What it shows:** Full detail for a single research run -- run ID, timestamp (formatted as "yyyy-MM-dd HH:mm UTC"), query, mode, and citation count. Returns HTTP 404 if the run ID is not found.

**Data source:** `ResearchRunPort.findByRunId()` against the `research_runs` table.

**Access / tier gating:** None. All authenticated users can access any run detail.

**How data gets here:** Same as `/research/runs` above.

---

## How Data Gets In

| Scheduler | Schedule | What it produces |
|---|---|---|
| `FeedHarvestScheduler` | 04:00 UTC daily (ACADEMIC/REGULATORY), every 4h (INDUSTRY) | Harvests RSS feeds via `RomeFeedHarvester` -> `news_articles` table |
| `WebMonitoringScheduler` | 05:00 UTC (competitor pages), 05:30 UTC (HuggingFace) | Scrapes COMPETITOR-tier pages via `WebPageHarvester` and HuggingFace model data -> `news_articles` |
| `EmbeddingScheduler` | 07:00 UTC daily | Reads all `news_articles`, embeds into pgvector store; articles are not searchable via `/research/ai-search` until this runs |
| `ResearchHarvestScheduler` | 04:00 UTC daily | Runs COMBINED pipeline per configured topic; saves Perplexity-discovered articles to `news_articles`; writes a `ResearchRun` record per topic; exports articles to the NotebookLM corpus directory on EC2 |

Manual paths:

- A user submitting `POST /api/v1/research` triggers `ResearchOrchestratorService`, which writes a `ResearchRun` record and may add Perplexity-discovered articles to `news_articles`.
- A user running the free-form vendor compare path triggers a live Perplexity call but does not write any `ResearchRun` or `news_articles` record.

New Perplexity-sourced articles written to `news_articles` (by `ResearchHarvestScheduler` or the manual research endpoint) become searchable in `/research/ai-search` only after the next `EmbeddingScheduler` run at 07:00 UTC.

---

## Cross-References

- **`/dashboard/search` (article search):** Uses the same pgvector index as `/research/ai-search`. Both go through `ArticleSearchPort.findSimilar()`. Searching either page queries the same underlying data.
- **`/profile` (subscriber profile):** The credit usage meter on the profile page reflects the same `UsageRecord` incremented by `/research/ai-search`. A heavy search session will show on the profile page.
- **NotebookLM export:** `ResearchHarvestScheduler` and manual STAGED/COMBINED research calls trigger `ResearchExportPort` (`NotebookLMService`), which writes HTML and TXT files to a corpus directory on EC2. This is a one-way export; it does not affect what the app displays.
- **Vendor checkbox list:** The COMPETITOR-tier entries in `FeedSourceProperties` (configured in `application.yml`) are the shared source of truth for both `WebMonitoringScheduler` (which harvests them) and `VendorCompareController` (which builds the checkbox list). Adding a new competitor requires only a YAML entry with `tier: COMPETITOR`.
- **Admin alert email:** If an LLM adapter throws during AI search synthesis, `AiSearchService` calls `AdminNotificationPort.notifyModelFailure()` and sends an alert email to the admin account.

---

### `/research/intel` — Competitive Intelligence Reports

**What it shows:**
A list of all previously generated `IntelReport` records with generated timestamps, plus a query form to generate a new report. Each report has a detail page (`/research/intel/{reportId}`) showing the full report body and a numbered source citation table (source title, URL, retrieved date). Reports are scoped to the whole app — all SUBSCRIBER+ users see the same shared report history.

**Data source:**
`intel_reports` table via `GenerateIntelReportUseCase.findAll()` and `findById(reportId)`. Timestamps are formatted server-side to `America/New_York` and passed in a `reportDates` map to avoid Thymeleaf `Instant` formatting issues.

**Access / tier gating:**
SUBSCRIBER, DEMO, and ADMIN: full access — report list and generation form are visible.
FREE / FREE_PENDING: upgrade prompt shown; no reports displayed, generation blocked.
No anonymous access.

**How data gets here:**
Reports are created only on demand — there is no scheduler or automated pipeline that generates them. A user submits a freeform query via `POST /research/intel/generate`. `GenerateIntelReportUseCase.generate(query, userEmail)` calls the Perplexity Sonar API to gather live web sources, then calls the Claude ChatClient to synthesize a structured report with numbered citations. The resulting `IntelReport` record is persisted to `intel_reports` and the user is redirected to the detail page. Generation failures show an error on the report list page; no partial record is persisted.

**Known limitations:**
- **No deletion.** There is no UI or endpoint to delete an individual report. The history grows without bound.
- **Shared history.** All authenticated SUBSCRIBER+ users see all reports generated by any user. There is no per-user scoping.
- **No generation history for the same query.** Submitting the same query twice creates two separate report records. There is no dedup or "already exists" check.
- **Generation is synchronous.** `POST /research/intel/generate` blocks until the Perplexity + Claude pipeline completes. On slow connections or quota-throttled API keys, the browser connection may time out before the response returns.

---

### `/admin/intelligence` — Intelligence Console (Claude Intelligence Service Proxy)

**What it shows:**
A tabbed console with 9 tabs covering the Claude Healthcare Intelligence Service — a separate Spring Boot application that runs alongside this app and exposes its own REST API. Tabs: Chat, Analyze, Synthesis, Platform Race, Wiki Ask, Verify, Trending, History, Files. The console renders the tab layout in Thymeleaf and uses JavaScript AJAX calls for all tab content — no server-side Thymeleaf rendering of tab data.

**Data source:**
All data displayed in the tabs comes from the Claude Intelligence Service at the `${claude.intelligence.base-url}` URL (default `http://localhost:8081`). This controller is a transparent HTTP proxy — every AJAX call from the browser is forwarded to the intelligence service and the raw JSON response is returned unchanged.

**Access / tier gating:**
All authenticated users: console page renders. Tab content is gated both at the UI level (Thymeleaf `th:if` on `isEnterprise` and `isSubscriber`) and at the API proxy level:
- SUBSCRIBER: 5 tabs (Chat, Analyze, Synthesis, Platform Race, History).
- ENTERPRISE / DEMO: all 9 tabs including Verify and Trending (enterprise-path API calls).
- ADMIN: all 9 tabs; bypasses tier checks and usage quota entirely.
- Usage quota: LLM-triggering POST paths (`/api/v1/intelligence/chat`, `/api/v1/intelligence/analyze`, `/api/v1/intelligence/synthesis`, `/api/v1/intelligence/platform-race`, `/api/v1/intelligence/wiki/ask`, `/api/v1/intelligence/verify`) count against the monthly `UsageRecord` limit. HTTP 429 is returned when the limit is reached; successful calls increment the counter.

**How data gets here:**
The Claude Intelligence Service is a separate application with its own knowledge base, pipelines, and storage. This controller does not read from any AIHealthcare database tables directly. It is purely a proxy layer with tier gating and usage metering.

**Known limitations:**
- **502 on service unavailability.** If the Claude Intelligence Service is not running or unreachable, all API proxy calls return HTTP 502 with a JSON error body. The Thymeleaf console page itself still renders, but all tabs will show error states.
- **No retry logic.** `doProxyPost()` and the GET proxy make a single attempt per request. No circuit-breaker or retry is implemented.
- **`X-API-Key` is sent even when blank.** If `intelligence.api-key` is empty string in `application.yml`, the header is not sent. If it is set to a non-blank value, it is sent on every request including GET. There is no per-request key rotation.
- **Files tab returns HTML, not JSON.** The GET proxy detects paths containing `/history/files/` (but not ending in `/files`) and sets `Content-Type: text/html` instead of `application/json`. All other GET paths return JSON. This is the only path-based content-type branching in the proxy.

---

## Known Limitations

**No vendor compare history.** `VendorCompareController` does not persist results. There is no way to recall a previous comparison without rerunning it.

**Research run list includes automated scheduler entries.** `ResearchHarvestScheduler` writes a `ResearchRun` record for each configured topic daily. On a system with many topics, the `/research/runs` table grows large and mixes routine automated entries with manual user queries. There is no flag distinguishing them.

**Vendor-select mode is DB-only and depends on scraper freshness.** The `compareSelected()` path queries `news_articles` for COMPETITOR-tier topics. If `WebMonitoringScheduler` has not scraped recently, or if competitor pages have changed without triggering the SHA-256 change detection, vendor cards may be thin or stale.

**AI model availability is runtime-dynamic with silent degradation.** If `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, or `GEMINI_API_KEY` is absent at startup, those adapters report `isAvailable() = false` and disappear from the model checkbox list without any warning to the user. The admin panel ANTHROPIC_API_KEY status warning is a known open bug.

**STAGED_RESEARCH can silently fall back to legacy DB.** If Perplexity returns zero sources, `ResearchOrchestratorService` falls back to the legacy DB adapter with a warning log. The resulting `ResearchRun` record shows `mode=STAGED_RESEARCH` even though it ran against local articles only.

**topK cap is applied silently.** A SUBSCRIBER requesting topK=50 receives topK=20 with no user-visible warning. The capped value is passed back to the template, so the form will show 20 on the next render.

**No pagination on `/research/runs`.** `ResearchRunPort.findAll()` loads all records into the Thymeleaf model in a single query. On a long-running instance this list will grow without bound.

**Free-form vendor compare deduplication is word-match only.** `filterByQueryRelevance()` removes legacy DB articles that share no words (of 3+ characters) with the query before passing them to the AI prompt. Short or generic query terms will allow many unrelated articles through.

**New articles have a lag before they are searchable.** Any article written to `news_articles` -- whether by a harvester or a research pipeline run -- does not appear in `/research/ai-search` until `EmbeddingScheduler` runs at 07:00 UTC the next morning. There is no on-demand embedding trigger from the research area.
