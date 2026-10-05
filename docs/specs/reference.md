# Reference Area — AIHealthcare Reference

> Last updated: 2026-10-05

The Reference Area is a set of read-heavy, data-rich pages that give subscribers and the app owner structured intelligence about the AI-in-healthcare landscape. It covers company sentiment and risk scoring, competitive framework analysis, a public company directory, and a state health-AI legislation registry. The audience is primarily subscribers doing due diligence on companies or tracking regulatory change, plus the app owner monitoring pipeline output and data quality.

---

## Pages

### Sentiment / Risk Scoring

**URL:** `/dashboard/risk` (overview), `/dashboard/risk/{slug}` (company detail)

**What it shows:** A ranked list of tracked AI healthcare companies with an aggregate sentiment score derived from article-level LLM classifications. Each company shows a continuous score in the range [-1.0, +1.0], a label (POSITIVE / NEGATIVE / MIXED / NEUTRAL), and article-level breakdowns. The detail page adds a per-article table with source name, URL, publication date, and individual sentiment label, plus a personal analyst notes field for authenticated users.

**Data source:**
- `company_sentiments` table — the primary source; holds overallScore, label, articleCount, and JSON-serialized per-article sentiment records.
- `news_articles` table — re-queried by articleId on both pages to supply URL, sourceName, and publishedAt for display.
- `analyst_notes` table — loaded on the detail page only, keyed on `NoteTargetType.COMPANY` + company slug, scoped to the authenticated user.

**Access / tier gating:**
- FREE: overview shows the top 5 companies only (FREE_COMPANY_LIMIT = 5). The summary badges (total company count) always reflect the full dataset.
- SUBSCRIBER / DEMO / ENTERPRISE / ADMIN: full company list on overview.
- Detail page: FREE users receive a reduced view with `upgradeRequired=true`; no sentiment data is returned. SUBSCRIBER and above get the full breakdown.

**How data gets here:**
- `StartupPipelineOrchestrator` triggers `CompanySentimentService.analyzeAll()` as one step in its post-harvest cascade after every article harvest.
- The pipeline loads company profiles, fetches linked articles per company, calls the LLM (via `SentimentAnalysisAdapter` / Claude) for batch classification, aggregates results, and persists to `company_sentiments`.
- Also triggerable manually from the admin Pipeline page via `POST /monitoring/sentiment/analyze`.
- Companies with fewer than 2 linked articles (MIN_ARTICLES = 2) are skipped during analysis.

---

### Framework Competitive Analysis

**URL:** `/dashboard/frameworks` (overview), `/dashboard/frameworks/{slug}` (detail), `/dashboard/frameworks/{slug}/articles` (source articles)

**What it shows:** A radar-chart comparison of configured AI healthcare companies scored across 6 competitive dimensions by Claude. Each company gets an overall score (1–10), per-dimension scores with rationale, and lists of strengths, weaknesses, and recent developments. The source articles page shows the exact articles that were fed to the LLM for that company's analysis.

**Data source:**
- `framework_analyses` table — primary source; stores dimensions, strengths, weaknesses, recentDevelopments as JSON TEXT columns and overallScore, articleCount, and pipe-delimited articleIds as scalar columns.
- `news_articles` table — re-queried by articleId on the source articles page.
- `analyst_notes` table — loaded on the detail page for authenticated users, keyed on `NoteTargetType.COMPANY` + slug.

**Access / tier gating:**
- Overview: accessible to all authenticated and unauthenticated users. The `isEnterprise` flag is passed to the template for display-only purposes.
- Detail page and source articles page: require `TierResolver.hasFullAccess()` (SUBSCRIBER / DEMO / ENTERPRISE / ADMIN). Lower-tier users get an `upgradeRequired=true` template with no data.

**How data gets here:**
- `StartupPipelineOrchestrator` cascades into `FrameworkAnalysisService.analyzeAll()` after every harvest.
- The pipeline iterates the company list from `aihealthcare.frameworks.companies` in `application.yml`, fetches all stored articles for each company's configured topics via `ArticleIngestionPort.fetchAllByTopic()`, deduplicates by articleId, and calls the LLM (`FrameworkAnalysisLlmAdapter` / Claude) for 6-dimension scoring.
- Companies with fewer than 3 linked articles (MIN_ARTICLES = 3) are silently skipped.
- Results are upserted — re-analysis always overwrites the previous result for a given slug.
- Adding a new company requires only a YAML entry (slug, name, url, topics list); no code changes.

---

### Public Company Directory

**URL:** `/directory` (index), `/directory/{slug}` (detail), `/directory/export.csv` (CSV download)

**What it shows:** A browsable, sortable directory of AI healthcare companies with signal badges (trending, recently funded, watchlist). The index supports sort tabs: Relevance (default), Trending, Recently Funded, and Watch List. The detail page shows full company profile, JSON-LD Organization schema for SEO, up to 5 related wiki pages, and up to 5 relevant state laws.

**Data source:**
- `healthcare_ai_companies` table — primary company records.
- `company_signals` (computed on demand by `CompanySignalService`): cross-references `news_articles` (90-day article count), `deal_signals` (recent FUNDING deals), and `company_sentiments` (sentiment score and label).
- `wiki_pages` table — detail page queries `WikiPageRepository.searchByKeywordForIndex()` for up to 5 related pages.
- `state_laws` table — detail page loads all laws and filters in memory for up to 5 relevant entries.

**Access / tier gating:**
- `/directory` and `/directory/{slug}`: fully public — no login required (`permitAll()` in Spring Security).
- Trending sort tab: any authenticated user (FREE and above).
- Recently Funded and Watch List sort tabs: SUBSCRIBER / DEMO / ENTERPRISE / ADMIN only. If a lower-tier user constructs a gated sort URL manually, the controller downgrades to relevance sort and sets `upgradeRequired=true`.
- CSV export (`/directory/export.csv`): ENTERPRISE or ADMIN only; other users are redirected to `/pricing`.

**How data gets here:**
- Company records are populated by `StartupDirectoryHarvester` (scrapes YC, TopStartups; anchors known incumbents) and `CompanyDiscoveryService`.
- Signal data flows from three independent pipelines: article harvest feeds `news_articles`; deal signal detection feeds `deal_signals`; sentiment analysis feeds `company_sentiments`.
- Signals are recomputed on each page request by `CompanySignalService` — they are not cached or pre-materialized.

---

### State Health-AI Legislation Registry

**URL:** `/legislation` (index), `/legislation/{id}` (detail), `/legislation/map` (state map), `/legislation/upcoming` (upcoming effective dates)

**What it shows:** A registry of enacted U.S. state laws regulating AI in healthcare. The index is a searchable, filterable table. The detail page shows full law text, enforcement provisions, official source links with freshness status, change history, related wiki pages, and related company directory entries. The map page shows a per-state summary with law counts. The upcoming page shows laws with effective dates in the next 90 days.

**Data source:**
- `state_laws` table — primary source (43 records seeded from `state_health_ai_laws_seed.json`; 41 enacted + 2 NOT_ENACTED for dedup suppression).
- `state_law_sources` table — source URLs with content-hash freshness tracking, surfaced on the detail page.
- `law_change_events` table — change history shown on the detail page; unreviewed changes surfaced to admins.
- `wiki_pages` table — detail page queries up to 5 related pages matched on LawCategory keyword or state name.
- `healthcare_ai_companies` table — detail page loads all companies and filters in memory for up to 5 related entries.

**Access / tier gating:**
- `/legislation` index: fully public, no authentication required.
- `/legislation/{id}` detail: authentication is not enforced at the controller level. A `fullAccess` boolean is passed to the template; FREE users see a reduced view per template logic. No 403 or redirect is thrown by the controller.
- `/legislation/map` and `/legislation/upcoming`: no access gate; anyone who can reach the URL gets the data.
- `/legislation/changes` and `POST /legislation/changes/{id}/review`: ADMIN role required (`@PreAuthorize("hasRole('ADMIN')")`).

**How data gets here:**
- `StateLawSeedRunner` (`ApplicationRunner`, `@Order(1)`): idempotent upsert on every app startup from `state_health_ai_laws_seed.json`. Existing records are updated if the `datasetVersion` in JSON is newer.
- `LegislationMonitorScheduler` runs two weekly jobs:
  - Monday 10:30 UTC — source freshness check: re-fetches official URLs, compares SHA-256 hashes, records a `LawChangeEvent` if content changed.
  - Monday 11:00 UTC — discovery sweep: calls `PerplexityLegislationDiscoveryAdapter` to search for newly enacted or amended bills and creates `NewBillCandidate` records.
- New bill candidates require manual admin promotion via REST endpoints before entering the registry. Nothing is auto-written.

---

## How Data Gets In

| Pipeline | Frequency | What it produces |
|---|---|---|
| `StartupPipelineOrchestrator` (post-harvest cascade) | After every article harvest | Refreshes sentiment scores and framework analyses |
| `FeedHarvestScheduler` (daily + 4h industry) | Daily 04:00 UTC (ACADEMIC/REGULATORY), every 4h (INDUSTRY) | New articles in `news_articles` that feed sentiment and framework pipelines |
| `StateLawSeedRunner` | Every app startup | Upserts all 43 seed law records idempotently |
| `LegislationMonitorScheduler` — source check | Monday 10:30 UTC | Freshness check on official source URLs; records change events |
| `LegislationMonitorScheduler` — discovery | Monday 11:00 UTC | Perplexity search for new bills; creates candidate records for admin review |
| `CompanySignalService` (on demand) | Every directory page request | Cross-references articles, deals, and sentiment — not cached |
| `StartupDirectoryHarvester` / `CompanyDiscoveryService` | On demand / scheduler | Populates `healthcare_ai_companies` records |
| Manual triggers (admin Pipeline page) | On demand | Sentiment analysis and framework analysis can be re-run manually |

---

## Cross-References

- **Sentiment → Deal Signals:** `DealContext` enrichment on `/dashboard/deals/{signalId}` pulls `CompanySentiment` for the relevant company slug.
- **Sentiment → Company Directory:** `CompanySignalService` reads `sentimentScore` and `sentimentLabel` from `company_sentiments` to compute directory signal badges.
- **Framework Analysis → Deal Signals:** `DealEnrichmentService` reads `FrameworkAnalysis` via `FrameworkAnalysisPort` for deal signal detail pages.
- **Company Directory → Wiki:** detail page (`/directory/{slug}`) surfaces up to 5 related wiki pages matched by company name keyword.
- **Company Directory → Legislation:** detail page surfaces up to 5 relevant state laws matched by company name keyword.
- **Legislation → Company Directory:** law detail page (`/legislation/{id}`) surfaces up to 5 related companies matched by category keywords or state name. (Reverse of the above.)
- **Legislation → Wiki:** law detail page surfaces up to 5 related wiki pages matched by category keyword or state name.
- **Analyst Notes (shared pattern):** both Sentiment and Framework detail pages load personal `AnalystNote` records for the authenticated user from the `analyst_notes` table, keyed on `NoteTargetType.COMPANY` + slug.

---

## Known Limitations

**Sentiment / Risk Scoring**
- The risk summary text is built from a fixed template string in `buildRiskSummary()` — it is not LLM-generated prose.
- `analyzedAt` reflects when the batch pipeline ran, not when the underlying articles were published. Stale analysis can persist until the next harvest cycle.
- Companies with fewer than 2 articles are silently excluded from analysis; they will not appear in the sentiment area at all.

**Framework Competitive Analysis**
- `fetchAllByTopic()` has no date window — all articles ever stored for a topic are fed to the LLM. Very old articles are included in every analysis run.
- The multi-company radar chart takes its dimension names from the first company's dimension list. If companies have differing dimension sets after a prompt change, radar axis labels will be wrong for other companies. No validation exists for this.
- Re-analysis always overwrites the previous result (upsert behavior); there is no history of prior framework scores.

**Public Company Directory**
- Company slugs are computed at query time by `SlugUtils.toSlug(name)` — no slug is stored in the database. Slug resolution scans all companies and matches the computed slug to the URL path variable on every request.
- `findRelevantLaws()` on the detail page calls `stateLawRepository.findAll()` and filters in memory — no DB-side filtering; scans all law records on every detail page load.
- Signal computation (`CompanySignalService`) runs on every page request with no caching. Under load this means multiple cross-table queries per page view.
- Citation markers (`[N]`) from Perplexity-sourced company descriptions are stripped at display time but remain in the database.
- CSV export includes sentiment score only when `hasSentimentData` is true; otherwise that column is blank.

**State Health-AI Legislation Registry**
- Index page filters are mutually exclusive (first non-blank parameter wins in order: `q` → `state` → `category` → `status`). There is no multi-filter combination support.
- Free-text search delegates to a SQL LIKE or in-memory filter — not vector or semantic search.
- `findRelatedCompanies()` on the detail page loads all companies with `findAllByOrderByNameAsc()` and filters in memory. Performance degrades as the company count grows.
- The FREE_DETAIL_LIMIT = 5 constant is defined but enforced only in the Thymeleaf template via a `fullAccess` boolean — the controller does not redirect or throw a 403 for lower-tier users on detail pages.
- NOT_ENACTED records (2 in the seed dataset) are included in `getAll()` results unless explicitly filtered by `status=ENACTED`.
- Source freshness status on the detail page is only meaningful if `LegislationMonitorScheduler` has completed at least one source-check run.

**Shared**
- All cross-area linking (laws → companies, companies → laws, laws → wiki, companies → wiki) uses string `contains` matching or repository keyword search — not vector similarity or relevance scoring. Match quality depends entirely on the keyword strings present in names and category labels.
- `TierResolver` is used for all tier checks in this area rather than Spring Security annotations directly. Tier logic is centralized there.
