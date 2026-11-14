# AIHealthcare — Completed Slice History

> Extracted from CLAUDE.md 2026-11-14 to keep the base file compact.
> Append new completed slices here, not to CLAUDE.md.
> See @docs/architecture.md for the full slice table with test counts.

---

## Current Slice — W7 (Wiki Hybrid Search) — COMPLETE — 75 selective tests passing

- [x] `WikiPageEntity` — added `lastEmbeddedRevision` int column (default 0) tracking which
      revision was last pushed to the vector store
- [x] `WikiPageRepository` — added `findNeedingEmbedding()` (`revision > lastEmbeddedRevision`)
- [x] `WikiEmbeddingScheduler` (new, `infrastructure.scheduler`) — mirrors `EmbeddingScheduler`;
      embeds wiki pages tagged `type=wiki_page` into the same shared `PgVectorStore` table used
      for articles; batches of 10, idempotent (ID derived from slug), swallows failures
- [x] `WikiQueryAdapter.findRelevantPages()` — blank query path unchanged (keyword/LIKE only, the
      exact code path `WikiLintScheduler`/`WikiLintController`/`FeedHarvestScheduler` depend on for
      "return everything"); non-blank query now merges keyword hits (guaranteed inclusion) with a
      pgvector semantic-similarity leg (`filterExpression("type == 'wiki_page'")`) filling any
      remaining slots up to `maxResults`, deduped by slug
- [x] Config: `aihealthcare.wiki.embedding-cron` (default `0 15 8 * * *`, 15 min after wiki lint)
- [x] No `AppConfig` change needed — `WikiQueryAdapter` is `@Component`-scanned, so Spring
      autowires the new `ObjectProvider<VectorStore>` constructor param directly
- [x] Tests: `WikiEmbeddingSchedulerTest` (7, new), `WikiQueryAdapterTest` (+8 hybrid-search
      cases), `WikiPageRepositoryTest` (+3 `findNeedingEmbedding` cases); verified unchanged:
      `WikiLintSchedulerTest`, `WikiLintControllerTest`, `WikiDeduplicationServiceTest`,
      `WikiCompilationAdapterTest`, `WikiControllerTest`
- [x] Plan: `.claude/plans/w7-wiki-hybrid-search.md`

---

## Previously Complete Slices

**Market Analysis (Phases 1–3) — COMPLETE — 260 selective tests passing**
- [x] Domain: `domain.marketanalysis` package — 9 enums, 18 records (see @docs/feature-specs.md)
- [x] Ports: 14 outbound ports + `ProduceMarketDigestUseCase` inbound
- [x] Services: `MarketDigestService`, `PeerGroupTagger`, `GuidanceQueryService`, `WeeklyRollupService`, `PriceReactionService`, `PriceReactionQueryService`
- [x] Infrastructure: `PerplexityMarketNewsAdapter`, `ClaudeImpactClassifierAdapter`, `AlpacaMarketDataAdapter`, `AlpacaNewsAdapter`, `AlpacaCorporateActionsAdapter`, `SesMarketDigestNotifier`, `EntryEmbeddingService`
- [x] Persistence: 8 entity sets + JPA repos + adapters
- [x] Config: `MarketAnalysisConfig` (5 domain service beans + `marketAnalysisExecutor` thread pool)
- [x] Scheduler: `MarketAnalysisScheduler` — daily digest at 07:00 CT + hourly price-reaction capture
- [x] Pipeline: `StartupPipelineOrchestrator` — market digest added as 14th cascade step
- [x] Web: `MarketDigestController` (REST), `MarketDashboardController` (Thymeleaf, tier-gated)
- [x] Templates: `market-digest.html`, `market-digest-history.html`, `market-digest-weekly.html`
- [x] Admin: Manual trigger cards in Pipeline page
- [x] Tests: 260+ across 35 test classes (16 domain + 15 infrastructure + 4 web)

**Copyright-Safe Content Retention (CR-1) — COMPLETE — 45 selective tests passing**
- [x] `ExcerptTruncator` — sentence-boundary truncation with configurable char cap
- [x] `RobotsTxtGate` — robots.txt fetcher/parser with 24h cache + domain denylist
- [x] `WebPageHarvester` — `MAX_BODY_LENGTH` 10,000→500 chars (COMPETITOR tier)
- [x] `RomeFeedHarvester` — RSS bodyText capped at 2,000 chars via `ExcerptTruncator`
- [x] `ArticleContentEnricher` — `MAX_BODY_LENGTH` 10,000→2,000 chars
- [x] Config: `aihealthcare.ingestion.domain-denylist`
- [x] No article deletion — this app is the repository of record

**Company Directory Signal Scoring — COMPLETE — 14 selective tests passing**
- [x] Domain: `CompanySignal` record (9 fields) with `hasRecentFunding()`, `isWatchList()`, `isTrending()` derived booleans
- [x] Domain: `CompanySignalService` — 3-port cross-reference in single pass
- [x] Web: `PublicCompanyController` — `sort` param, `GET /directory/export.csv`
- [x] Template: `company-directory.html` — scoring explanation, sort tabs, signal badges (🔥/💰/⚠)

**LLM-Enhanced Deal Signal Alerts (DS-1) — COMPLETE — 70 selective tests passing**
- [x] Domain: `DealSignal` extended 8→12 fields; `DealContext` enrichment wrapper; `DealClassificationPort`
- [x] Service: `DealSignalDetectionService` — keyword pre-filter + optional LLM refinement; `DealEnrichmentService`
- [x] Infrastructure: `DealClassificationAdapter` (ChatClient, batch, `DEAL_RESULTS:` parsing); `deal-classification.txt` prompt
- [x] Persistence: `DealSignalEntity` +4 columns; `DealSignalRepository.findBySignalTypeOrderByDetectedAtDesc`
- [x] Web: `DealSignalController` — filter bar, tier gating; `DealSignalRestController`; `deals.html`, `deals-detail.html`
- [x] Tests: 70 across 8 classes

**Framework Competitive Analysis + Self-Maintaining Pipeline Orchestrator — COMPLETE — 1509 tests passing**
- [x] Domain: `FrameworkAnalysis`, `FrameworkDimension`, `FrameworkCompany` records
- [x] Service: `FrameworkAnalysisService` — MIN_ARTICLES=3 threshold, 6-dimension LLM scoring
- [x] Infrastructure: `FrameworkAnalysisLlmAdapter`, `FrameworkAnalysisPersistenceAdapter`, `FrameworkCompanyProperties`
- [x] Web: `FrameworkDashboardController` at `/dashboard/frameworks`, `FrameworkRestController`
- [x] Templates: `framework-analysis.html` (Chart.js radar), `framework-detail.html`
- [x] `StartupPipelineOrchestrator` — sequences ALL 11 post-harvest pipelines, each step try-catch isolated
- [x] Config: `aihealthcare.frameworks.companies` YAML — adding companies is config-only

**Sentiment & Risk Scoring (S-SENT) — COMPLETE — 1465 tests passing**
- [x] Domain: `SentimentLabel` enum, `ArticleSentiment`, `CompanySentiment` records
- [x] Service: `CompanySentimentService` — per-article LLM classification → company-level aggregation
- [x] Infrastructure: `SentimentAnalysisAdapter` (ChatClient batch), `CompanySentimentEntity`, `CompanySentimentAdapter`
- [x] Prompt: `sentiment-analysis.txt` — `SENTIMENT_RESULTS` format
- [x] Web: `SentimentDashboardController` at `/dashboard/risk`, `SentimentRestController`

**Historical Trend Archive (T-HIST) — COMPLETE — 1416 tests passing**
- [x] `DetectTrendsUseCase.getAllSnapshots()` added; `TrendOrchestrationService.getAllSnapshots()`
- [x] Web: `TrendHistoryController` — `/dashboard/trends/history`, snapshot detail
- [x] Templates: `trend-history.html` (Chart.js multi-line), `trend-history-detail.html`

**Regulatory Alert System (R-REG) — COMPLETE — 1081 tests passing**
- [x] Domain: `RegulatoryEventType` enum (10 values), `RegulatoryBody`, `RegulatoryEvent` (13 fields)
- [x] Harvesters: `Fda510kHarvester`, `FdaDeNovoHarvester`, `CmsRuleHarvester`, `CompositeRegulatoryHarvester`
- [x] Scheduler: `RegulatoryHarvestScheduler` — daily 04:30 UTC
- [x] Web: `RegulatoryController` at `/dashboard/regulatory` — tier-gated (FREE=5, SUBSCRIBER=50)

**Custom Watchlists (W-WATCH) — COMPLETE — 1021 tests passing**
- [x] Domain: `WatchlistItemType` enum, `WatchlistItem`, `WatchlistMatch` records
- [x] Service: `WatchlistMatchingService` — keyword/company/topic matching with snippet extraction
- [x] Web: `WatchlistController` at `/watchlist` — tier-gated to SUBSCRIBER/DEMO/ADMIN

**Trend Detection — COMPLETE — 965 tests passing**
- [x] Domain: `TrendDirection` enum, `TrendSignal`, `TrendSnapshot` records
- [x] Services: `TrendDetectionService` (keyword frequency, 30/90/180-day windows), `TrendOrchestrationService`
- [x] Scheduler: `TrendDetectionScheduler` — weekly Sunday 08:00 UTC
- [x] Web: `TrendController` at `/dashboard/trends`, `TrendRestController`

**Access Model Redesign (R1–R8) — COMPLETE — 909 tests passing**
_(4-tier DEMO/FREE_PENDING/FREE/SUBSCRIBER, demo expiration, digest email, Stripe re-enable)_

**Vendor Compare UX Overhaul — COMPLETE — 862 tests passing**
_(vendor checkbox grid, compareSelected() port, VendorAssessmentService overload, vendor-compare.html)_

**Wiki Hybrid Search (W7) — see Current Slice above**

**Wiki Linter (W6) — COMPLETE — 847 tests passing**
_(orphan/broken-ref/stale detection + scheduled lint runs + auto-repair endpoint)_

**Reversal Watch newsletter section (W3) — COMPLETE — 860 tests passing**
_(newsletter section from wiki contradictions)_

**Reader-Facing Wiki Provenance UI (W5) — COMPLETE — 808 tests passing**
- [x] Web: `WikiController` — `/wiki` (index), `/wiki/{slug}` (detail), `/wiki/contradictions`
- [x] Templates: `wiki-index.html`, `wiki-detail.html`, `wiki-contradictions.html`
- [x] Dependency: `commonmark:0.24.0` — markdown→HTML rendering
- [x] Security: `/wiki`, `/wiki/**` added to `permitAll()`

**Wiki Persistence + Compilation Adapter (W2) — COMPLETE — 800 tests passing**
_(JPA entities, repositories, WikiCompilationAdapter, WikiResponseParser, REST trigger, scheduler wiring)_

**LLM Wiki Domain Records, Ports & Tests (W1) — COMPLETE — 759 tests passing**
- [x] Domain: `WikiPageType` enum, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport` records
- [x] Ports: `KnowledgeCompilationPort`, `WikiQueryPort`

**AI Search Enhancements (Slice 47) — COMPLETE — 719 tests passing**
- [x] Fix: Claude output bug — multi-line SUMMARY parsing in all adapters
- [x] Infrastructure: `GeminiAiSearchAdapter` — raw `RestClient` to Gemini REST API
- [x] UI: Gemini checkbox, model-differentiated synthesis cards with colored borders

**Search Result Caching through Dashboard Charts (Slices 42-46) — COMPLETE — 713 tests passing**
_(Caffeine TTL cache, Profile Page, Email Template, API Key Management, Chart.js charts)_

**New AI Healthcare Companies (Slice 41) — COMPLETE — 681 tests passing**
- [x] Domain: `Company`, `CompanyDiscoveryResult`, `CompanyTags` records
- [x] Services: `CompanyClassifier` (8 subcategories), `CompanyDeduplicator`, `CompanyDiscoveryService`
- [x] Infrastructure: `StartupDirectoryHarvester` — YC, TopStartups, incumbents

**Merge Semantic Search into AI Search (Slice 40) — COMPLETE — 627 tests passing**
_(Unified at `/research/ai-search`, `SemanticSearchController` → 302 redirect, NO_MATCH gating)_

**AI-Enhanced Search (Slice 37) — COMPLETE — 631 tests passing**
- [x] Domain: `AiSearchResult`, `AiSearchSynthesis` records; `ConductAiSearchUseCase`; `AiSearchPort`
- [x] Infrastructure: `AnthropicAiSearchAdapter`, `OpenAiSearchAdapter`, `PerplexityAiSearchAdapter`
- [x] Web: `AiSearchController` at `/research/ai-search`, `AiSearchRestController`

**Multi-Field Article Search (Slice 36) — COMPLETE — 610 tests passing**
_(criteria-based filtering with JPA Specification)_

**Admin User Management Actions (Slice 35) — COMPLETE — 585 tests passing**
_(toggle enable/disable, change role)_

**HuggingFace Enrichment (Slice 34) — COMPLETE — 577 tests passing**
_(cardData, likes, library enrichment; sourceTier removed from UI)_

**Admin Panel (Slice 33) — COMPLETE — 577 tests passing**
_(user management table, system status dashboard)_

**Role-Based Access Control (Slice 32) — COMPLETE — 573 tests passing**
_(ADMIN vs USER page restrictions, 403 page)_

**Spring Security Session-Based Authentication (Slice 29) — COMPLETE — 551 tests passing**
_(login page, user entity, session-based auth)_

**Archive Depth Gating (Slice 30) — COMPLETE — 559 tests passing**
_(FREE=7d, MEMBER=unlimited article date filtering)_

**Member-Only Semantic Search (Slice 31) — COMPLETE — 567 tests passing**
_(vector similarity search, tier gating, usage metering)_

**Tier Rename + Usage Metering + Feature Gating (Slice 28) — COMPLETE — 542 tests passing**
_(PREMIUM→MEMBER rename, usage metering)_

**Tier-Based Content Gating (Slice 27) — COMPLETE — 520 tests passing**
_(FREE gets teaser, MEMBER gets full newsletter)_

**Cron Job Consolidation + Newsletter Draft-First Workflow (Slice 26) — COMPLETE — 501 tests passing**
- [x] All cron expressions externalized to `application.yml`
- [x] `NewsletterGenerationScheduler` — draft-only: generates DRAFT but does NOT auto-send
- [x] Daily timeline (UTC): 04:00 RSS → 05:00 Competitor → 05:30 HuggingFace → 06:00+ Industry → 07:00 Embedding → 00:00 Draft

**Topic Summary AI Generation (Slice 25) — COMPLETE — 502 tests passing**
- [x] Domain: `TopicSummary` record, `TopicSummaryPort`, `TopicSummaryGenerationService`
- [x] `FeedHarvestScheduler` — auto-generates summaries after harvest
- [x] `news-listing.html` — "AI Summary" box under each topic section

**Perplexity Healthcare news source refactor (Slice 24) — COMPLETE — 502 tests passing**
_(5 Perplexity feed sources: Hub, Health Blog, Premium Health Sources, PR Newswire, Sonar API)_

**OpenAI Healthcare & Google Healthcare news source refactor (Slice 23) — COMPLETE — 502 tests passing**
_(10 sources across OpenAI Healthcare and Google Healthcare topics)_

**Anthropic Healthcare news source refactor (Slice 22) — COMPLETE — 502 tests passing**
- [x] 7 Anthropic feed sources; `FeedSourceConfig` — `keywords` field for content-based filtering
- [x] `WebPageHarvester` / `RomeFeedHarvester` — keyword matching support

**Newsletter Preview/Edit UI with TinyMCE (Slice 21) — COMPLETE — 485 tests passing**
- [x] `NewsletterPreviewController` — list, TinyMCE editor, save, send
- [x] `newsletter-runs.html`, `newsletter-edit.html` (TinyMCE 7.9.0 WYSIWYG)

**News Listing UI (Slice 20) — COMPLETE — 475 tests passing**
- [x] `GET /dashboard/news` Thymeleaf page — articles grouped under configurable topic sections
- [x] `FeedSourceConfig` — `topic` field + `effectiveTopic()` fallback
- [x] Beckers Hospital Review added as INDUSTRY RSS feed

**Vendor Compare UI (Slice 19) — COMPLETE — 470 tests passing**
- [x] Domain: `CompareVendorsUseCase`; `VendorAssessmentService` with `vendor-compare.txt` prompt
- [x] Web: `VendorCompareController` at `/research/vendors`

**Research Dashboard UI (Slice 18) — COMPLETE — 460 tests passing**
_(run history table, full detail card, Thymeleaf at `/research/runs`)_

**ResearchHarvestScheduler (Slice 17) — COMPLETE — 458 tests passing**
_(daily COMBINED pipeline per configured topic, pre-warms research_runs table)_

**COMBINED Research Mode (Slice 16) — COMPLETE — 455 tests passing**
_(merges Perplexity API + DB article sources before synthesis)_

**Research Result Persistence (Slice 15) — COMPLETE — 445 tests passing**
_(ResearchRunEntity, ResearchRunAdapter, GET /api/v1/research/runs)_

**Perplexity Live Integration (Slice 14) — COMPLETE — 430 tests passing**
_(`PerplexityHarvester` upgraded to live Sonar API; Research Compare resilience)_

**Research Compare UI (Slice 13) — COMPLETE — 420 tests passing**
_(side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH at `/research/compare`)_

**Research Refactor: Staged Research Architecture (Slice 12) — COMPLETE — 415 tests passing**
- [x] Domain: `ResearchMode`, `ResearchRequest`, `ResearchPlan`, `RetrievedSource`, `ResearchAnswer`, `VendorAssessment` records
- [x] Services: `CitationAssembler`, `ResearchPlanningService`, `ResearchSynthesisService`, `ResearchOrchestratorService`
- [x] Infrastructure: `LegacyGoogleResearchAdapter`, `PerplexityResearchAdapter`

**Analytics Dashboard UI (Slice 11) — COMPLETE — 379 tests passing**
_(Thymeleaf starter, `DashboardController`, `dashboard.html`, `articles.html`)_

**Analytics Dashboard REST (Slice 10) — COMPLETE — 372 tests passing**
_(`IngestionAnalytics`, `RunAnalytics`, `EvaluationAnalytics` records; `AnalyticsController`)_

**RAG Generation + Document Ingestion + HTML Export (Slice 7) — COMPLETE — 333 tests passing**
- [x] Domain: `DocumentChunk`, `DocumentIngestionResult`, `IngestDocumentsUseCase`, `DocumentVectorPort`, `FileParserPort`
- [x] Infrastructure: `PdfParser`, `DocxParser`, `PlainTextParser`, `DocumentIngestionAdapter`
- [x] `NotebookLMService` — added companion `.html` export alongside `.txt` summary
- [x] Web: `DocumentIngestionController` — `POST /api/v1/documents/ingest`

**Prompt Refactoring (Slice 6) — COMPLETE — 323 tests passing**
_(SearchPromptConfig, `PromptLoaderService`, filesystem-first template loader, `PerplexityHarvester` stub)_

**Web Monitoring & Competitive Intelligence (Slice 5) — COMPLETE — 264 tests passing**
- [x] `FeedTier` enum — `COMPETITOR` and `HUGGINGFACE`
- [x] `WebPageHarvester` — jsoup scraper with SHA-256 change detection
- [x] `HuggingFaceHarvester` — public API client for healthcare LLMs
- [x] `WebMonitoringScheduler` — daily 07:00 UTC (competitor) + 07:30 (HuggingFace)

**Prompt Evaluation (Slice 4) — COMPLETE — 233 tests passing**
_(LLM-as-judge: `PromptEvaluationService`, `AiEvaluationAdapter`, JPA entities, REST endpoints)_

**Scheduling + Delivery (Slice 3) — COMPLETE — 137 tests passing**
_(Subscriber record, `DeliveryService`, `EmailDeliveryAdapter`, `NewsletterGenerationScheduler`)_

**Vector Store (Slice 2b) — COMPLETE**
_(`SimpleVectorStore` bean, `EmbeddingScheduler`, `VectorStoreArticleSearchAdapter`)_

**JPA Persistence H2 (Slice 2a) — COMPLETE**
_(JPA entities, repositories, adapters, `ArticleController`, `NewsletterRunController`)_

---

## Newsletter Digest Quality Fix (CI-30) — COMPLETE

- **Root cause:** `DigestNewsletterRenderer` had no article cap — fetched all articles from last 3 days (1,973 articles on 2026-08-15)
- `LOOKBACK_DAYS`: 3 → 1; `MAX_DIGEST_ARTICLES = 75` (after dedup, sort by `sourceWeight` DESC)
- Email subject: `"1973 News Articles From 8/15/2026"` → `"AI Healthcare Intelligence — August 15, 2026"`
- **Tier system cleanup:** Removed `FeedTier.HUGGINGFACE`; HuggingFace feeds moved to `FeedTier.RESEARCH`
- Priority order: REGULATORY ≥ LEGAL ≥ RESEARCH ≥ ACADEMIC > INDUSTRY > COMPETITOR

---

## Infrastructure Classes Added in Early Slices

### Slice 1 Additions

| Class | Package | Notes |
|-------|---------|-------|
| `FeedSourceConfig` | `infrastructure.ingestion.feed` | Immutable record: topicId, name, url, tier, weight |
| `FeedSourceProperties` | `infrastructure.ingestion.feed` | `@ConfigurationProperties(prefix="aihealthcare.feeds")` |
| `RomeFeedHarvester` | `infrastructure.ingestion.feed` | Implements `ArticleHarvestingPort` via Rome library |
| `FeedHarvestScheduler` | `infrastructure.ingestion.feed` | `@Scheduled` — daily (ACADEMIC/REGULATORY) + 4h (INDUSTRY) |
| `ArticleIngestionAdapter` | `infrastructure.ingestion` | DB-backed; queries `NewsArticleRepository` by topic |
| `AiSummarizationAdapter` | `infrastructure.ai` | Spring AI `ChatClient` adapter |
| `AppConfig` | `infrastructure.config` | `@EnableScheduling` + `@EnableConfigurationProperties` |
| `StartupPipelineOrchestrator` | `infrastructure.scheduler` | Sequences all post-harvest pipelines; each step try-catch isolated |
| `FrameworkAnalysisLlmAdapter` | `infrastructure.ai` | ChatClient adapter: 6-dimension competitive scoring |
| `DealClassificationAdapter` | `infrastructure.ai` | ChatClient adapter: batch deal classification |

### Slice 2a Additions

| Class | Package | Notes |
|-------|---------|-------|
| `NewsletterRenderer` | `domain.service` | Pure Java — renders HTML + plain-text from `NewsletterDraft` |
| `NewsletterRunStatus` | `domain.model` | Enum: DRAFT → SENT → ARCHIVED |
| `NewsletterRun` | `domain.model` | Record: persisted newsletter run |
| `ArticleStoragePort` | `domain.port.outbound` | `void save(List<NewsArticle>)` — silently skips duplicate URLs |
| `NewsletterRunPort` | `domain.port.outbound` | `save`, `findByRunId`, `findAll` |
| `TopicEntity` | `infrastructure.persistence` | JPA entity for `topics` table |
| `NewsArticleEntity` | `infrastructure.persistence` | JPA entity; url VARCHAR(2048), bodyText TEXT |
| `NewsletterRunEntity` | `infrastructure.persistence` | JPA entity; htmlContent + plainTextContent as CLOB |
| `ArticleStorageAdapter` | `infrastructure.persistence` | Implements `ArticleStoragePort`; dedup via `existsByUrl` |
| `NewsletterRunAdapter` | `infrastructure.persistence` | Implements `NewsletterRunPort` |
| `ArticleController` | `web.controller` | `GET /api/v1/articles?topic=&limit=` |
| `NewsletterRunController` | `web.controller` | `GET /api/v1/runs` + `GET /api/v1/runs/{runId}` |
