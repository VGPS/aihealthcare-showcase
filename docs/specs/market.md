# Market Area — AIHealthcare Reference

> Last updated: 2026-10-05

The Market Area tracks daily market-moving events in AI-powered healthcare — regulatory clearances, funding rounds, M&A activity, deal signals, legislative actions, and frontier company claims — and surfaces them in a set of tier-gated dashboard pages. Its primary audience is the app owner and paying subscribers who want a single place to monitor what is happening commercially and regulatorily in the space without assembling it from raw feeds themselves.

---

## Pages

### `/dashboard/market` — Daily Market Digest (latest)

**What it shows.** The most recent digest: a ranked list of headlines with fact/speculative badges, affected company names and tickers, deal size where available, source links, and price-reaction badges showing 1h/4h/1d/3d percentage changes. A category filter pill row lets you narrow to EARNINGS, REGULATORY, FUNDING, M_AND_A, MAJOR_PARTNERSHIP, etc.

**Data source.** Four joined tables: `market_digest`, `market_digest_entry`, `market_digest_impact_assessment`, `market_digest_affected_company`. Price-reaction badges are pulled from `price_reaction_snapshots` via `PriceReactionQueryService` — one query per company per entry on every page render (no cache).

**Access / tier gating.** FREE users see the top 3 entries. SUBSCRIBER, DEMO, ADMIN, and ENTERPRISE see all entries.

**How data gets here.** `MarketAnalysisScheduler.runDailyDigest()` fires at 07:00 America/Chicago daily (config key: `aihealthcare.market-analysis.schedule`). The pipeline inside `MarketDigestService` runs: Perplexity Sonar for primary news → Alpaca News for secondary cross-check → Claude ChatClient for impact classification → peer-group tagging → qualifying filter → persist → post-save enrichment (regulatory trackers, funding rounds, deal terms) → 7-day embedding dedup for notification suppression → SES email. Running the pipeline again on the same date returns the existing digest without re-executing.

---

### `/dashboard/market/{date}` — Daily Market Digest (by date)

**What it shows.** Same layout as the latest digest page but for a specific ISO date supplied as a path variable.

**Data source.** Same four `market_digest*` tables, queried by `digestDate`.

**Access / tier gating.** Same as the latest page: FREE sees top 3 entries, SUBSCRIBER+ sees all.

**How data gets here.** Same daily scheduler as the latest page.

---

### `/dashboard/market/history` — Digest History

**What it shows.** A summary table of every stored digest date: date string, entry count, top category, and `generatedAt` timestamp. An optional `?days=` query parameter narrows the list to a lookback window; `days=0` shows everything.

**Data source.** `MarketDigestRepository.findAll()` loaded in full, then gated in memory.

**Access / tier gating.** FREE sees the 7 most recent digests. SUBSCRIBER+ sees the full history.

**How data gets here.** Same daily scheduler.

---

### `/dashboard/market/weekly` — Weekly Rollup

**What it shows.** A deduplicated week-in-review: each row represents a story that appeared across the 7-day window, showing a representative headline, how many times it surfaced, the best rank it achieved, fact classification, company labels, and price-reaction badges. Prev/next week navigation links to the prior and following Monday.

**Data source.** `WeeklyRollupService` reads `MarketDigestRepository` over the 7-day window and computes `WeeklyRollup` / `RollupEntry` aggregates in memory. Price-reaction badges come from `price_reaction_snapshots`.

**Access / tier gating.** FREE sees the top 3 rollup entries. SUBSCRIBER+ sees all.

**How data gets here.** Same daily digest scheduler. If no digests were stored for the requested week, the rollup returns an empty entry list rather than an error.

---

### `/dashboard/deals` — Deal Signals

**What it shows.** A paginated, deduplicated list of deal signals (FUNDING, ACQUISITION, PARTNERSHIP, IPO, PRODUCT_LAUNCH). Each row shows company name, signal type, confidence percentage, detected-at date, deal amount, counterparty, and a source link. Above the list, two analytics panels: per-type counts comparing the current 30-day window to the prior 30 days with percentage-change labels, and five market-ratio cards (e.g., Partnership:Acquisition ratio) with short interpretive text. A `?type=` filter narrows the list by signal type.

**Data source.** `deal_signals` table via `DealSignalPort.findRecent()` / `findByType()`. Analytics panels use `DealSignalPort.countByTypeInPeriod()` against two non-overlapping 30-day windows.

**Access / tier gating.** FREE sees at most 10 signals with no pagination. SUBSCRIBER+ gets pages of 25 with full pagination controls.

**How data gets here.** Two production paths:
1. `StartupPipelineOrchestrator` runs deal signal detection as a cascade step after every harvest (calls `detectDealSignalsUseCase.detectSignals()` scanning the last 30 days of articles).
2. `DealSignalScheduler` runs standalone every 6 hours (config key: `aihealthcare.deals.detection-cron`).

Detection is two-stage: keyword pre-filter across five keyword arrays, then optional LLM batch classification via `DealClassificationAdapter` (10 articles per call), falling back to keyword-only if the LLM returns empty. Results are deduplicated by `{companyName|signalType|day}`, keeping the highest-confidence signal per key. New signals trigger a webhook via `WebhookDispatcher` with event type `WATCHLIST_MATCH`.

---

### `/dashboard/deals/{signalId}` — Deal Detail

**What it shows.** Full detail for a single deal signal, plus a cross-reference panel assembling the company's sentiment score, framework competitive analysis, any matching regulatory events, and the company profile — all pulled from other areas of the app.

**Data source.** `deal_signals` table for the signal record. `DealEnrichmentService` joins in memory (fuzzy slug matching on company name) against: `company_sentiments`, `framework_analyses`, `regulatory_events`, and the `companies` table.

**Access / tier gating.** Requires SUBSCRIBER, DEMO, or ADMIN. FREE users see an upgrade prompt page.

**How data gets here.** The core signal record is produced by the deal detection pipeline described above. The cross-referenced data comes from each respective pipeline (sentiment, framework analysis, regulatory harvest, company directory).

---

### `/dashboard/regulatory` — Regulatory Alerts

**What it shows.** A sortable, filterable table of regulatory events: FDA 510(k) clearances, De Novo classifications, CMS rules, and others. Summary badge counts appear at the top. Filter by `?filter=fda` or `?filter=cms`. Sortable by type, title, applicant, reference number, outcome, or published date via `?sort=` (append `_desc` for descending). Dates display in America/New_York.

**Data source.** `regulatory_events` table via `MonitorRegulatoryEventsUseCase` (`RegulatoryEventAdapter`).

**Access / tier gating.** FREE sees 5 events. SUBSCRIBER+ sees up to 50.

**How data gets here.** `RegulatoryHarvestScheduler` fires daily at 04:30 UTC (config key: `aihealthcare.regulatory.schedule`). `CompositeRegulatoryHarvester` aggregates three sources, each in an isolated try-catch: `Fda510kHarvester` (openFDA 510(k) API), `FdaDeNovoHarvester` (openFDA classification API), `CmsRuleHarvester` (Federal Register API). After harvest, events are matched against user watchlist items.

---

### `/dashboard/legal` — Legal Timeline

**What it shows.** A unified chronological feed merging three data streams into one timeline: litigation articles (red badge), policy articles (amber badge), and regulatory events (blue badge). Supports category filter (`?filter=litigation/regulation/policy`) and lookback window (`?days=30/90/180/365`; `days=0` means all time; default is 90 days). Google News titles in "Headline - Publisher" format are split into a display title and a publisher label. Entries are deduplicated by `{normalizedTitle|day}` and sorted newest first.

**Data source.** `news_articles` table for articles with topic names "AI Healthcare Legal" and "AI Healthcare Government Policy" (topic names are hardcoded constants in `LegalTimelineController`). `regulatory_events` table queried directly via `RegulatoryEventRepository` (time-windowed JPA query, not through the use-case layer). Articles with empty or duplicate body text are enriched with meta descriptions fetched at page-render time by `MetaDescriptionFetcher` (8-second timeout per URL, cached in memory only).

**Access / tier gating.** FREE is clamped to a 30-day window regardless of the `?days=` param. SUBSCRIBER+ can request any window including `days=0`.

**How data gets here.** Legal and policy articles arrive via the standard RSS harvest (`FeedHarvestScheduler` at 04:00 UTC). Regulatory events arrive via `RegulatoryHarvestScheduler` at 04:30 UTC.

---

### `/dashboard/claims` — Frontier Claim Tracker

**What it shows.** A sortable table of claims made by frontier AI companies (Anthropic, OpenAI, Google, etc.). Each row shows company name, claim type (6 values: CAPABILITY_CLAIM, SAFETY_CLAIM, BENCHMARK_CLAIM, TIMELINE_CLAIM, REGULATORY_CLAIM, PARTNERSHIP_CLAIM), verdict (5 values: EVIDENCE_BACKED, ALLEGED_UNVERIFIED, MARKETING_HYPE, CONTRADICTED, RETRACTED), claim text, evidence notes, source title, source URL, and detected-at timestamp. The header shows verdict distribution counts; an "unverified" aggregate groups ALLEGED_UNVERIFIED + MARKETING_HYPE + CONTRADICTED. Rows where company name starts with "no_qualifying" are hidden. Filter by company, verdict, or type via query params (company filter takes priority; only one filter is active at a time). CSV export available at `GET /dashboard/claims/export.csv`. Each row has a "Generate Post" button that creates LinkedIn + Facebook drafts and redirects to `/dashboard/social/drafts`.

**Data source.** `frontier_claims` table via `FrontierClaimPort.findAll()` / `getByCompany()` / `getByVerdict()` / `getByType()`.

**Access / tier gating.** FREE sees 5 claims. SUBSCRIBER+ sees all. CSV export is blocked for FREE (redirects to `/pricing`).

**How data gets here.** `StartupPipelineOrchestrator` runs frontier claim detection as a cascade step after each harvest, passing only the most recent 1 day of articles (`fetchRecentArticles(1)`). `FrontierClaimService` delegates to `ClaimClassifierPort` (LLM-powered via Sonnet model, configured via `aihealthcare.ai.claim-classifier-model`), deduplicates against existing claims by `{company + normalized claim text prefix}`, then runs a 90-day contradiction check across same-company prior claims before persisting.

---

### `/dashboard/social` — Social Post Generator

**What it shows.** Copy-ready LinkedIn body + first-comment pair and Facebook body + comment pair, both sourced from the top 5 entries of the latest market digest by rank. LinkedIn body excludes URLs (algorithm penalty); LinkedIn comment holds all source URLs. Facebook body includes the lead story URL. An optional `?yourTake=` query param prepends a personal voice statement to both posts. A "Generate with AI Agent" button (POST `/dashboard/social/agent-draft`) calls a Spring AI agent to produce a draft and stores it; drafts are then accessible at `/dashboard/social/drafts`.

**Data source.** `ProduceMarketDigestUseCase.findLatest()` — same `market_digest*` tables. The template-based generation is pure formatting (no LLM call). The agent draft path makes a live LLM call.

**Access / tier gating.** FREE sees an upgrade prompt with no post content. SUBSCRIBER+ sees the full generator.

**How data gets here.** The social post content is assembled from the latest market digest at page-render time. The market digest is produced by the daily digest scheduler described above.

---

### `/dashboard/market/enrichment` — Market Enrichment Detail

**What it shows:**
A three-tab page exposing the structured data extracted from the daily market digest pipeline as post-save enrichment: Regulatory Trackers, Private Funding Rounds, and Deal Terms. Each tab shows summary count badges (total items, approaching deadlines for trackers), then the full dataset. Regulatory Trackers include: jurisdiction, rulemaking stage, docket ID, comment deadline, and last-updated date. Private Funding Rounds show: company name, round stage, amount, lead investors, and announced date. Deal Terms show: headline (linked to the digest entry), upfront cash, milestone payments, equity stake %, royalty %, and disclosed portion. FREE users see 3 items per tab; SUBSCRIBER+ see all.

**Data source:**
- Regulatory Trackers: `regulatory_trackers` table via `RegulatoryTrackerRepository.findAll()` + `findApproachingDeadlines(now + 30 days)`.
- Private Funding Rounds: `private_funding_rounds` table via `PrivateFundingPort.findRecentRounds(now - 180 days, null)` (180-day lookback, no peer-group filter).
- Deal Terms: `deal_terms` table via `DealTermsPort.findAllWithHeadlines()` — returns a `Map<String, DealTerms>` keyed by headline string.
- All three ports are `@Autowired(required = false)` — if a bean is absent, the corresponding tab renders as empty without error.

**Access / tier gating:**
FREE: 3 items per tab (FREE_LIMIT = 3). SUBSCRIBER / DEMO / ADMIN: all items. No anonymous access. Note: Deal Terms `fullAccess` gating is applied to regulatory trackers and funding rounds only — deal terms map is not sliced (this is a minor bug; the full deal terms map is always passed to the template regardless of tier).

**How data gets here:**
The enrichment data is written by `MarketDigestService`'s post-save step, which runs after every daily digest is persisted. Three extraction routines run against the digest entries: regulatory tracker inference (jurisdiction/stage from entry text + docket regex), private funding round inference (round-stage from funding category entries), and deal terms extraction (keyword-based amount parsing from M&A/partnership entries). There is no standalone scheduler for this page — all data flows from the market digest pipeline.

---

### `/dashboard/legal/trends` — Legal Trend Analysis

**What it shows:**
A bar chart and card grid of rising legal and regulatory keyword trends, sourced from a dedicated `LegalTrendSnapshot` that tracks keyword frequency across legal, litigation, regulation, and policy categories. Each trend card shows: keyword (title-cased), category badge (LITIGATION / REGULATION / POLICY), current 30-day frequency, previous 90-day baseline, momentum score, direction (UP/DOWN/STABLE), and a linked-items list of the specific articles and regulatory events that drove the trend. Summary counts at the top show total keywords tracked and per-category breakdowns. FREE users see only the top 3 trends; SUBSCRIBER+ see all.

**Data source:**
`legal_trend_snapshots` table via `DetectLegalTrendsUseCase.getLatestSnapshot()`. Linked items per trend are resolved at page-render time: article IDs in `topArticleIds` are looked up via `ArticleIngestionPort.fetchArticlesByIds()`, and any IDs that don't match articles are tried against `MonitorRegulatoryEventsUseCase.getEvent(id)` as regulatory events. This means each page load issues one `IN` query for articles plus N individual regulatory-event lookups for unmatched IDs.

**Access / tier gating:**
FREE: top 3 trends. SUBSCRIBER / DEMO / ADMIN: all trends. No anonymous access. The chart is rendered with `chartLimit = min(displayTrends.size(), 10)` — the chart shows at most 10 items even for full-access users.

**How data gets here:**
`LegalTrendDetectionScheduler` runs on a configurable schedule (cron key: `aihealthcare.legal-trends.schedule`) and calls `DetectLegalTrendsUseCase.detectLegalTrends()`. This analyzes articles from the `AI Healthcare Legal` and `AI Healthcare Government Policy` topics for keyword frequency across 30-day and 90-day windows, then persists a `LegalTrendSnapshot` with the `risingTrends` list. A manual trigger is available via `POST /dashboard/legal/trends/detect` (ADMIN role required, enforced at method level, not SecurityConfig).

**Known limitations:**
- If no snapshot exists yet, the page renders with `hasSnapshot = false` and all model attributes set to empty/zero defaults. There is no "run detection now" prompt for non-admin users.
- The N regulatory-event lookups per page render are individual queries, not a batch fetch. On a trend card with many unmatched article IDs, this adds O(N) sequential queries at render time.
- Keywords are title-cased at display time in `toTitleCase()` but stored lowercase in the database. The display key used in `trendLinkedItems` uses the title-cased version, which must match exactly — a mismatch would silently drop linked items for a trend card.

---

### `/dashboard/weekly-roundup` — Weekly Content Roundup Generator

**What it shows:**
A copy-ready social publishing tool that generates an LLM-synthesized analytical narrative from the past week's LEGAL and COMPETITOR tier articles, formatted for three channels: a LinkedIn post body (≤2,900 chars, no URLs), a LinkedIn first comment (source publication names + insights page link), and a Substack article (longer-form with section headers). The page shows the date range for the week (Sunday to today, America/Chicago), article counts by tier, and character counts for each output block. There is no submit button — outputs are plain text for manual copy-paste into the respective publishing platform.

**Data source:**
`news_articles` table via `ArticleIngestionPort.fetchRecentArticles(7)`. Only articles with `sourceTier` of LEGAL or COMPETITOR are kept; articles with `sourceWeight < 0.5`, generic "Google News" titles, `NFE/`-prefixed body text, or titles identical to their topic label are filtered out. Results are sorted LEGAL-first then by `sourceWeight` descending. Deduplication by normalized title (lowercase, non-alphanumeric stripped). Top 10 articles are passed to `WeeklyRoundupSynthesizer.synthesizeNarrative()`, which calls the Claude ChatClient. On LLM failure, a fallback string-concatenation narrative is used.

**Access / tier gating:**
No explicit tier gating in the controller — any authenticated user can access the page. Content is always generated on page load (including the LLM call), so every page visit consumes a `WeeklyRoundupSynthesizer` call regardless of tier.

**How data gets here:**
This page generates content at request time from the article corpus — it is not backed by any pre-computed table. Every `GET /dashboard/weekly-roundup` triggers a fresh LLM synthesis call. There is no caching.

**Known limitations:**
- **LLM call on every GET request.** Unlike most pages, every page load makes a synchronous ChatClient call. If the Anthropic API is slow or rate-limited, the page hangs until the call completes or the fallback kicks in.
- **No caching.** The synthesized narrative is not persisted. Refreshing the page generates a new narrative and a new LLM call.
- **LinkedIn body hard-truncated at 2,900 chars.** If the narrative is long, `buildLinkedInBody()` cuts at 2,900 characters and then looks for the last newline within the last 200 chars to avoid mid-sentence truncation. If no newline is found in that range, the cut may be mid-sentence.
- **`weekStart` computed as "previous Sunday" from today.** If today is Sunday, `TemporalAdjusters.previous(DayOfWeek.SUNDAY)` returns the previous Sunday, not today — the date range will be one full week behind actual Sunday-to-today span.

---

## How Data Gets In

| Pipeline | Trigger | Config key | What it produces |
|---|---|---|---|
| `MarketAnalysisScheduler` (digest) | Daily 07:00 America/Chicago | `aihealthcare.market-analysis.schedule` | Market digest entries, regulatory tracker extractions, funding round extractions, deal term extractions |
| `MarketAnalysisScheduler` (price reaction) | Every hour | `aihealthcare.market-analysis.reaction.schedule` | Price-reaction snapshots for 1h/4h/1d/3d horizons |
| `RegulatoryHarvestScheduler` | Daily 04:30 UTC | `aihealthcare.regulatory.schedule` | Regulatory events (FDA 510(k), De Novo, CMS rules) |
| `FeedHarvestScheduler` + `StartupPipelineOrchestrator` | Daily 04:00 UTC (RSS), then cascade | `aihealthcare.harvest.daily-cron` | News articles for legal/policy timeline; triggers deal signal detection and frontier claim detection as cascade steps |
| `DealSignalScheduler` | Every 6 hours | `aihealthcare.deals.detection-cron` | Deal signals (standalone re-scan between harvests) |

All cron expressions are externalized to `application.yml`. The market digest pipeline is idempotent — a second run on the same date returns the cached digest without re-executing.

---

## Cross-References

- **Deal detail (`/dashboard/deals/{signalId}`)** reads from three other areas at page-render time: `company_sentiments` (Sentiment & Risk), `framework_analyses` (Framework Analysis), and `companies` (Company Directory). All lookups use in-memory fuzzy slug matching in `DealEnrichmentService`.
- **Market digest post-save enrichment** writes derived records to `regulatory_trackers`, `private_funding_rounds`, and `deal_terms` tables, which are exposed through their own REST endpoints (`/api/market-digest/regulatory-tracker`, `/api/market-digest/funding`, `/api/market-digest/deal-terms/{headline}`).
- **Legal timeline** reads `regulatory_events` that were produced by the same `RegulatoryHarvestScheduler` that feeds `/dashboard/regulatory`.
- **Claim tracker** writes to `SavedPost` records (social posts area) when the "Generate Post" button is clicked, redirecting to `/dashboard/social/drafts`.
- **Social post generator** reads the same market digest tables as `/dashboard/market` and can create saved drafts via `ManageSavedPostsUseCase`.
- **Watchlist** receives match notifications when new deal signals or regulatory events are detected and match a subscriber's watchlist keywords.

---

## Known Limitations

**Price-reaction query volume.** `PriceReactionQueryService.findReactions()` is called once per affected company per digest entry on every page render of `/dashboard/market` and `/dashboard/market/weekly`. No result caching exists at the controller level. A digest with many entries and many ticker-bearing companies will generate a large number of synchronous queries.

**Legal timeline bypasses the use-case layer.** `LegalTimelineController` calls `RegulatoryEventRepository` directly rather than through `MonitorRegulatoryEventsUseCase`. This was intentional to access a time-windowed JPA query variant, but it means this call does not go through the domain port layer — a deviation from the hexagonal architecture rule that is worth cleaning up in a future refactor.

**Legal timeline meta description latency.** `MetaDescriptionFetcher` is called synchronously at page-render time for articles that have empty or duplicate body text. Each fetch has an 8-second timeout. On a page load with many such articles, this adds meaningful latency. Results are cached in JVM memory only; cache is lost on restart.

**Frontier claim scan window is 1 day.** The orchestrator passes `fetchRecentArticles(1)` to the claim detection step. If the harvest pipeline fails or is skipped for a day, those articles will not be scanned for claims in the next run.

**Deal signal re-ingestion gap.** `DealSignalDetectionService` deduplicates by `articleId`. If an article is re-ingested with a new `articleId` (e.g., after a URL change), it will be re-processed and may produce a duplicate signal.

**Qualifying filter broader than original spec.** The `isMarketMoving()` method in `MarketDigestService` passes LEGAL_ACTION, WORKFORCE, and PRODUCT_LAUNCH categories through without a size threshold, in addition to the originally specified EARNINGS, REGULATORY, M_AND_A, MAJOR_PARTNERSHIP, and FUNDING > $50M (strict greater-than; exact $50M does not qualify).

**Social post agent draft failures are silent.** If the Spring AI agent call in `POST /dashboard/social/agent-draft` fails, the controller redirects back to the social page with a flash error attribute. The template-based synchronous path is unaffected.

**Weekly rollup on weeks with no data.** If no market digests were stored for the requested 7-day window, `WeeklyRollupService` returns an empty entry list with no error indication to the user.
