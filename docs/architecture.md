# AIHealthcare — Architecture Reference

> Last updated: 2026-08-07 | Reflects Day 7 QA — v1 final

## Design Philosophy
Spec-Driven Development + Hexagonal Architecture. The OpenAPI spec is the single source of
truth for the HTTP API surface. The domain layer owns business rules and is framework-free.
Infrastructure adapters are pluggable — swapping the AI provider or scraping strategy
requires no domain changes.

---

## Dependency Rules (enforced by Maven module boundaries)

```
web  ──▶  application  ──▶  domain
                ▲                ▲
         infrastructure/*  ──────┘
              (adapters)
api  ──▶  web   (generated DTOs imported here only)
```

- `domain` → no dependencies outside JDK
- `application` → depends on `domain` only
- `infrastructure/*` → depends on `domain`; may use Spring, Spring AI, HTTP clients
- `web` → depends on `application` + `api` (generated DTOs); uses Spring MVC + Thymeleaf
- `api` → OpenAPI Generator output only; no hand-written code

---

## Completed Slices

| Slice | Description | Tests (cumulative) |
|-------|-------------|-------------------|
| 1 | Ingest & Summarize (in-memory) | baseline |
| 2a | JPA Persistence (H2) | +~30 |
| 2b | Vector Store (pgvector + EmbeddingScheduler) | +14 |
| 3 | Scheduling + Email Delivery + Subscribers | 137 |
| 4 | Prompt Evaluation (LLM-as-judge) | 233 |
| 5 | Web Monitoring & Competitive Intelligence | 264 |
| 6 | Prompt Refactoring (search_prompts, PerplexityHarvester stub) | 323 |
| 7 | RAG Generation + Document Ingestion + HTML Export | 333 |
| 8 | Newsletter Filtering, section ordering, HTML link fixes | ~347 |
| 9 | ~~Market Intelligence Service~~ (removed — superseded by Intelligence Console) | 358 |
| 10 | Analytics Dashboard REST (ingestion / runs / evaluations) | 372 |
| 11 | Analytics Dashboard UI — Thymeleaf at `/dashboard` | 379 |
| 12 | Research Refactor: Perplexity-style staged research architecture | 415 |
| 13 | Research Compare UI: side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH | ~420 |
| 14 | Perplexity Live Integration + Research Compare Resilience | ~430 |
| 15 | Research Result Persistence: durable ResearchRun records + export | ~445 |
| 16 | COMBINED Research Mode: merge Perplexity + DB sources before synthesis | ~455 |
| 17 | ResearchHarvestScheduler: proactive daily COMBINED pipeline per topic | ~458 |
| 18 | Research Dashboard UI — Thymeleaf at `/research/runs` | 460 |
| 19 | Vendor Compare UI — Thymeleaf at `/research/vendors` | 470 |
| 20 | News Listing UI + Beckers feed + topic/name separation | 475 |
| 21 | Newsletter Preview/Edit UI with TinyMCE editor | 485 |
| 22 | Anthropic Healthcare news source refactor: 7 sources + keyword filter | ~490 |
| 23 | OpenAI Healthcare & Google Healthcare news source refactor | ~495 |
| 24 | Perplexity Healthcare news source refactor: 5 sources | ~498 |
| 25 | Topic Summary AI Generation: per-topic 3-sentence summaries on news page | 502 |
| 26 | Cron Job Consolidation + Newsletter Draft-First Workflow | 501 |
| 27 | Tier-based content gating — FREE gets teaser, MEMBER gets full newsletter | 520 |
| 28 | Tier rename (PREMIUM→MEMBER), usage metering, feature gating | 542 |
| 29 | Spring Security session-based authentication — login page, user entity | 551 |
| 30 | Archive Depth Gating — per-tier article date filtering (FREE=7d, MEMBER=unlimited) | 559 |
| 31 | Member-only semantic search — vector similarity, tier gating, usage metering | 567 |
| 32 | Role-based access control — ADMIN vs USER page restrictions, 403 page | 573 |
| 33 | Admin panel — user management table, system status dashboard | 577 |
| 34 | HuggingFace enrichment (cardData, likes, library) + sourceTier removed from UI | 577 |
| 35 | Admin user management actions — toggle enable/disable and change role | 585 |
| 36 | Multi-field article search — criteria-based filtering with JPA Specification | 610 |
| 37 | AI-Enhanced Search — multi-model LLM synthesis (Claude, GPT, Perplexity) | 631 |
| 38 | AI Search UI cleanup — numbered article references, default topK=20 | 633 |
| 39 | Simplify search criteria, expand AI synthesis, Healthcare Dive feed | 631 |
| 40 | Merge Semantic Search into AI Search — unified page + NO_MATCH gating | 627 |
| 41 | New AI Healthcare Companies — company discovery pipeline + MEMBER gating | 681 |
| 42 | Search Result Caching — Caffeine TTL-based cache for AI search | ~685 |
| 43 | Subscriber Self-Service Profile Page — tier badge, usage meter, Stripe portal | ~690 |
| 44 | Email Newsletter Template — professional HTML email with inline CSS | ~700 |
| 45 | API Key Management — X-API-Key header auth for REST endpoints | ~710 |
| 46 | Dashboard Analytics Charts — Chart.js trend + doughnut charts | 713 |
| 47 | AI Search Enhancements — Claude fix + Gemini adapter + UI differentiation | 719 |
| W1 | LLM Wiki — domain records, port interfaces, unit tests | 759 |
| W2 | LLM Wiki — persistence, compilation adapter, REST trigger, scheduler wiring | 800 |
| W5 | Reader-Facing Wiki Provenance UI — wiki index, detail, contradictions pages | 808 |
| W6 | Wiki Linter — orphan/broken-ref/stale detection + scheduled lint runs | 847 |
| W3 | Reversal Watch newsletter section from wiki contradictions | 860 |
| — | Vendor Compare UX Overhaul — checkbox grid, compareSelected(), vendor prompt | 862 |
| — | Dynamic model registry + admin failure notifications + AWS Bedrock adapter | 876 |
| — | CompanyDiscoveryScheduler + model availability gating | 879 |
| R1–R8 | Access Model Redesign — 4-tier (DEMO/FREE_PENDING/FREE/SUBSCRIBER), demo expiration, digest email, Stripe re-enable | 909 |
| — | UI polish: shared CSS extraction, news sort, button fixes, label formatting | 914 |
| — | Trend Detection — weekly keyword frequency analysis (rising/fading/new) with UI + REST | 965 |
| W-WATCH | Custom Watchlists — subscriber watchlist with keyword/company/topic matching + scheduler integration | 1021 |
| R-REG | Regulatory Alert System — FDA 510(k)/De Novo + CMS rules harvesting, watchlist integration, tier-gated UI | 1081 |
| — | Sentiment & Risk Scoring — LLM-powered company sentiment classification | 1465 |
| — | Framework Competitive Analysis + Pipeline Orchestrator | 1509 |
| DS-1 | LLM-Enhanced Deal Signal Alerts — cross-referenced context, detail page, type filtering, tier gating | 1509+ |
| MA-1 | Market Analysis (Phases 1–3) — Perplexity news research, Claude impact classifier, Alpaca market data, weekly rollup, price-reaction scoring | 1509+ |
| ED-1 | Enterprise Data PULL — async job-based data export with LLM query planning, confined file I/O, remote HTTPS connector, job reaper + retention scheduler | 1509+ |
| ED-2 | Enterprise Data PUSH — per-customer cron schedules, DB-sweeper scheduler with atomic claim, email delivery with size-aware attachment/signed-link fallback, schedule CRUD REST + console Schedules tab | 1509+ |

---

## Package Naming Convention

```
com.wgblackmon.aihealthcare.domain.model
com.wgblackmon.aihealthcare.domain.port.inbound
com.wgblackmon.aihealthcare.domain.port.outbound
com.wgblackmon.aihealthcare.domain.service
com.wgblackmon.aihealthcare.domain.exception
com.wgblackmon.aihealthcare.infrastructure.ai
com.wgblackmon.aihealthcare.infrastructure.config
com.wgblackmon.aihealthcare.infrastructure.delivery
com.wgblackmon.aihealthcare.infrastructure.ingestion
com.wgblackmon.aihealthcare.infrastructure.ingestion.feed
com.wgblackmon.aihealthcare.infrastructure.ingestion.document
com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface
com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity
com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory
com.wgblackmon.aihealthcare.infrastructure.ingestion.web
com.wgblackmon.aihealthcare.infrastructure.persistence
com.wgblackmon.aihealthcare.infrastructure.research
com.wgblackmon.aihealthcare.infrastructure.scheduler
com.wgblackmon.aihealthcare.web.controller
com.wgblackmon.aihealthcare.web.dto
```

---

## AI Integration Pattern

Spring AI `ChatClient` is wrapped by AI adapters, all in `infrastructure.ai`:

| Adapter | Port implemented | Purpose |
|---------|-----------------|---------|
| `AiSummarizationAdapter` | `AiSummarizationPort` | Newsletter section summarization (standard + RAG) + topic summaries |
| `AiEvaluationAdapter` | `AiEvaluationPort` | LLM-as-judge prompt evaluation scoring |
| `AiReportAdapter` | `AiReportPort` | Monthly market intelligence report generation |
| `AnthropicAiSearchAdapter` | `AiSearchPort` | Claude synthesis for AI-enhanced search |
| `OpenAiSearchAdapter` | `AiSearchPort` | GPT synthesis for AI-enhanced search |
| `PerplexityAiSearchAdapter` | `AiSearchPort` | Perplexity Sonar synthesis via RestClient |
| `GeminiAiSearchAdapter` | `AiSearchPort` | Google Gemini synthesis via RestClient |
| `WikiCompilationAdapter` | `KnowledgeCompilationPort` | LLM wiki compilation — articles → pages/contradictions |
| `DealClassificationAdapter` | `DealClassificationPort` | LLM deal classification — batch articles → confirmed DealSignals with extracted fields |

Unit tests inject mock ports — no real AI calls outside `@Profile("ai-integration")`.

Prompt templates (`application/src/main/resources/prompts/`):
- `summarize-articles.txt` — standard newsletter summarization
- `summarize-articles-rag.txt` — RAG-augmented summarization
- `evaluate-section.txt` — LLM-as-judge scoring (5 dimensions)
- `generate-introduction.txt` — newsletter intro generation
- `research-plan.txt` — research query decomposition
- `research-synthesis.txt` — research answer synthesis
- `vendor-compare.txt` — vendor assessment (strengths/weaknesses/relevance + Doc Frequency/TF-IDF)
- `topic-summary.txt` — 3-sentence topic summary from article titles
- `ai-search-synthesis.txt` — multi-model AI search synthesis with `[N]` citations
- `wiki-compile.txt` — structured wiki compilation (PAGE/CONTRADICTION/WARNING sections)
- `deal-classification.txt` — batch deal classification (TYPE/AMOUNT/COMPANY/COUNTERPARTY/CONFIDENCE/SUMMARY/ANALYSIS)

---

## Article Ingestion Pattern

Multiple harvesters feed into `ArticleStoragePort` → `ArticleStorageAdapter` → `news_articles` table.

```
FeedHarvestScheduler     → RomeFeedHarvester     → RSS feeds (ACADEMIC/REGULATORY/INDUSTRY)
WebMonitoringScheduler   → WebPageHarvester       → Competitor web pages (SHA-256 change detection)
                         → HuggingFaceHarvester   → HuggingFace model API
ResearchHarvestScheduler → PerplexityHarvester    → Perplexity API (COMBINED research mode, daily)
RegulatoryHarvestScheduler → CompositeRegulatoryHarvester → FDA/CMS APIs (510(k), De Novo, Federal Register)
```

All harvested articles share the `NewsArticle` domain record (11 fields). `ArticleStorageAdapter`
deduplicates by URL before persisting. Regulatory events use a separate `RegulatoryEvent` domain
record (13 fields) persisted via `RegulatoryEventAdapter` with dedup by reference number and source URL.

---

## Research Pipeline

Three research modes selectable via `aihealthcare.research.mode`:

```
LEGACY_GOOGLE    → LegacyGoogleResearchAdapter  → DB articles only
STAGED_RESEARCH  → PerplexityResearchAdapter    → Perplexity API live search
COMBINED         → both adapters merged         → DB + Perplexity, deduped by URL
```

`ResearchOrchestratorService` drives the full pipeline:
1. `ResearchPlanningService` — decomposes query into `RetrievalQuery` list via AI
2. Source retrieval via `SourceRetrievalPort` adapters
3. `CitationAssembler` — deduplicates and ranks `SourceCitation` list
4. `ResearchSynthesisService` — synthesizes `ResearchAnswer` via AI
5. `ResearchRunPort.save()` — persists `ResearchRun` record for the audit trail

`ResearchHarvestScheduler` runs daily, executing the COMBINED pipeline per configured topic
and persisting results so the DB is pre-warmed for subsequent queries.

---

## Thymeleaf UI Pages

| URL | Controller | Template |
|-----|-----------|----------|
| `GET /dashboard` | `DashboardController` | `dashboard.html` — analytics overview |
| `GET /dashboard/articles` | `DashboardController` | `articles.html` — article list |
| `GET /research/compare` | `ResearchCompareController` | `research-compare.html` — side-by-side compare |
| `GET /research/runs` | `ResearchDashboardController` | `research-runs.html` — run history |
| `GET /research/runs/{runId}` | `ResearchDashboardController` | `research-run-detail.html` — run detail |
| `GET /research/vendors` | `VendorCompareController` | `vendor-compare.html` — vendor comparison card grid |
| `GET /dashboard/news` | `DashboardController` | `news-listing.html` — articles grouped by topic |
| `GET /newsletter/runs` | `NewsletterPreviewController` | `newsletter-runs.html` — run list with edit links |
| `GET /newsletter/runs/{runId}/edit` | `NewsletterPreviewController` | `newsletter-edit.html` — TinyMCE WYSIWYG editor |
| `GET /dashboard/search` | `DashboardController` | `search.html` — multi-field article search with filter form |
| `GET /admin` | `AdminController` | `admin.html` — user management + system status (ADMIN only) |
| `GET /research/ai-search` | `AiSearchController` | `ai-search.html` — unified AI search with multi-model synthesis |
| `GET /pricing` | `PricingController` | `pricing.html` — tier comparison with Stripe checkout |
| `GET /login` | `LoginController` | `login.html` — Spring Security login form |
| `GET /wiki` | `WikiController` | `wiki-index.html` — searchable wiki page grid with type filter |
| `GET /wiki/{slug}` | `WikiController` | `wiki-detail.html` — rendered markdown + provenance table + contradictions |
| `GET /wiki/contradictions` | `WikiController` | `wiki-contradictions.html` — reversal watch contradiction feed |
| `GET /dashboard/trends` | `TrendController` | `trends.html` — keyword trend analysis (rising/fading/new) |
| `GET /watchlist` | `WatchlistController` | `watchlist.html` — subscriber watchlist with keyword/company/topic items + matches |
| `GET /dashboard/regulatory` | `RegulatoryController` | `regulatory.html` — FDA/CMS regulatory alerts with filter tabs + tier gating |
| `GET /dashboard/deals` | `DealSignalController` | `deals.html` — deal signals with type filter, tier gating |
| `GET /dashboard/deals/{signalId}` | `DealSignalController` | `deals-detail.html` — cross-referenced deal context (sentiment, framework, regulatory, profile) |
| `GET /directory` | `PublicCompanyController` | `company-directory.html` — public company list with sector filter pills; no login required |
| `GET /directory/{slug}` | `PublicCompanyController` | `company-directory-detail.html` — public company profile with clickable `[N]` citation anchors; JSON-LD SEO |
| `GET /enterprise/data` | `EnterpriseDataConsoleController` | `enterprise-data-console.html` — ENTERPRISE tier console: submit jobs, monitor progress, tail logs, download artifacts |

---

## REST API Surface

| Method | Path | Controller |
|--------|------|-----------|
| GET | `/api/v1/articles` | `ArticleController` |
| GET | `/api/v1/articles/search` | `ArticleSearchController` |
| GET/GET | `/api/v1/runs`, `/api/v1/runs/{runId}` | `NewsletterRunController` |
| POST | `/api/v1/newsletter/deliver` | `NewsletterDeliveryController` |
| POST/GET/DELETE | `/api/v1/subscribers` | `SubscriberController` |
| POST/GET/DELETE | `/api/v1/variants`, `/api/v1/variants/{id}` | `PromptVariantController` |
| POST/GET | `/api/v1/evaluations`, `/api/v1/evaluations/{id}` | `PromptEvaluationController` |
| POST | `/api/v1/comparisons` | `PromptEvaluationController` |
| POST/POST/POST/POST/GET | `/monitoring/harvest`, `/monitoring/competitor`, `/monitoring/huggingface`, `/monitoring/summaries`, `/monitoring/hashes` | `WebMonitoringController` |
| GET/GET/PUT | `/api/v1/search-prompts`, `/api/v1/search-prompts/{engine}` | `SearchPromptController` |
| POST | `/api/v1/documents/ingest` | `DocumentIngestionController` |
| GET | `/api/v1/analytics/ingestion`, `/api/v1/analytics/runs`, `/api/v1/analytics/evaluations` | `AnalyticsController` |
| POST | `/api/v1/research` | `ResearchController` |
| GET/GET | `/api/v1/research/runs`, `/api/v1/research/runs/{runId}` | `ResearchRunController` |
| GET | `/api/v1/search/ai` | `AiSearchRestController` |
| POST | `/api/v1/stripe/checkout` | `StripeCheckoutController` |
| POST | `/api/v1/stripe/webhook` | `StripeWebhookController` |
| POST | `/api/v1/companies/discover` | `CompanyDiscoveryController` |
| POST | `/monitoring/wiki/compile` | `WikiCompilationController` |
| GET/POST | `/api/v1/trends/latest`, `/api/v1/trends/detect` | `TrendRestController` |
| GET/GET/POST | `/api/v1/deals`, `/api/v1/deals/{signalId}`, `/api/v1/deals/detect` | `DealSignalRestController` |
| POST/GET/GET/POST/GET/GET/GET/GET | `/api/v1/enterprise/data/jobs`, `/api/v1/enterprise/data/jobs/{jobId}`, `/api/v1/enterprise/data/jobs`, `/api/v1/enterprise/data/jobs/{jobId}/cancel`, `/api/v1/enterprise/data/feeds`, `/api/v1/enterprise/data/feeds/{feedId}/prompts`, `/api/v1/enterprise/data/jobs/{jobId}/log`, `/api/v1/enterprise/data/jobs/{jobId}/artifact` | `EnterpriseDataRestController` |
| POST/GET/GET/PUT/DELETE | `/api/v1/enterprise/connections`, `/api/v1/enterprise/connections/{id}`, `/api/v1/enterprise/connections`, `/api/v1/enterprise/connections/{id}`, `/api/v1/enterprise/connections/{id}` | `EnterpriseConnectionRestController` |
| POST/GET/PUT/DELETE/POST/GET | `/api/v1/enterprise/data/schedules`, `/api/v1/enterprise/data/schedules`, `/api/v1/enterprise/data/schedules/{scheduleId}`, `/api/v1/enterprise/data/schedules/{scheduleId}`, `/api/v1/enterprise/data/schedules/{scheduleId}/run`, `/api/v1/enterprise/data/schedules/preview` | `EnterpriseScheduleRestController` |
| GET | `/d/{token}` | `SignedDownloadController` — HMAC-SHA256 signed artifact download (no login required) |

---

## Persistence Schema

| Table | Entity | Notes |
|-------|--------|-------|
| `topics` | `TopicEntity` | Seeded via `data.sql` |
| `news_articles` | `NewsArticleEntity` | url VARCHAR(2048), bodyText TEXT; deduped by URL |
| `newsletter_runs` | `NewsletterRunEntity` | htmlContent + plainTextContent as CLOB |
| `subscribers` | `SubscriberEntity` | email unique, active flag |
| `prompt_variants` | `PromptVariantEntity` | name, template, active |
| `evaluation_results` | `EvaluationResultEntity` | articleIds pipe-delimited |
| `comparison_results` | `ComparisonResultEntity` | variantA vs variantB |
| `page_content_hashes` | `PageContentHashEntity` | SHA-256 change detection for web pages |
| `search_prompts` | `SearchPromptEntity` | engine, templateText, active |
| `research_runs` | `ResearchRunEntity` | runId, query, mode, citationCount, researchedAt |
| `topic_summaries` | `TopicSummaryEntity` | topic PK, summaryText TEXT, generatedAt; upsert by topic |
| `wiki_pages` | `WikiPageEntity` | slug PK (natural key), pageType, tags/relatedSlugs pipe-delimited |
| `wiki_source_refs` | `WikiSourceRefEntity` | pageSlug FK, articleId, provenance link |
| `wiki_contradictions` | `WikiContradictionEntity` | pageSlug, prior/new claims, pipe-delimited sourceIds |
| `wiki_page_revisions` | `WikiPageRevisionEntity` | pageSlug, revision, contentMarkdown CLOB |
| `compilation_reports` | `CompilationReportEntity` | run timestamps, articlesProcessed, pipe-delimited slugs |
| `trend_snapshots` | `TrendSnapshotEntity` | generatedAt, windowDays, JSON-serialized rising/fading/new signals |
| `watchlist_items` | `WatchlistItemEntity` | itemId PK, userEmail, itemType, value, label, createdAt |
| `watchlist_matches` | `WatchlistMatchEntity` | matchId PK, itemId FK, articleId, matchedOn, snippet TEXT |
| `regulatory_events` | `RegulatoryEventEntity` | eventId PK, eventType, regulatoryBody, referenceNumber, applicantName, deviceName, aiHealthcareKeywords TEXT (pipe-delimited) |
| `enterprise_data_jobs` | `DataJobEntity` | jobId UUID PK, ownerEmail, feedId, format, status, rowCount, byteSize, contentSha256, artifactPath, logPath, heartbeatAt, expiresAt |
| `enterprise_data_audit` | `DataAccessAuditEntity` | id BIGSERIAL PK, occurredAt, ownerEmail, jobId, action, outcome, detail |
| `enterprise_canned_prompts` | `CannedPromptEntity` | promptId UUID PK, feedId, label, description, templateText, sortOrder |
| `enterprise_remote_connections` | `RemoteConnectionEntity` | connectionId UUID PK, ownerEmail, label, kind, baseUrl, authType, headerName, secretRef, active |
| `enterprise_push_schedules` | `DataPushScheduleEntity` | scheduleId UUID PK, ownerEmail, label, feedId, promptId, promptText, parameters TEXT, format, cronExpression, zoneId, recipients TEXT (pipe-delimited), active, nextRunAt, lastRunAt, lastStatus, lastJobId, consecutiveFailures, createdAt, updatedAt |

---

## Scheduler Summary

All cron expressions are externalized to `application.yml` — no hardcoded schedules.

| Scheduler | Trigger (UTC) | Config key | Action |
|-----------|---------------|------------|--------|
| `FeedHarvestScheduler` | 04:00 daily (ACAD/REG) + every 4h (INDUSTRY) | `aihealthcare.harvest.daily-cron`, `industry-rate-ms` | RSS harvest → DB → topic summary generation → wiki compilation |
| `ResearchHarvestScheduler` | 04:00 daily | `aihealthcare.research.harvest.cron` | COMBINED pipeline → ResearchRun records |
| `WebMonitoringScheduler` | 05:00 daily (competitors) + 05:30 (HuggingFace) | `aihealthcare.harvest.competitor-cron`, `huggingface-cron` | Web scrape + HF discovery → DB |
| `EmbeddingScheduler` | 07:00 daily | `aihealthcare.embedding.schedule` | Embed all articles into vector store |
| `NewsletterGenerationScheduler` | 00:00 daily (midnight) | `aihealthcare.newsletter.schedule` | Ingest → generate DRAFT (no auto-send) |
| `TrendDetectionScheduler` | Sunday 08:00 | `aihealthcare.trends.schedule` | Keyword frequency analysis → TrendSnapshot |
| `RegulatoryHarvestScheduler` | 04:30 daily | `aihealthcare.regulatory.schedule` | FDA/CMS harvest → dedup → save → watchlist match |
| `EnterpriseDataJobReaper` | Every 5 min | `aihealthcare.enterprise.data.reaper-cron` | Mark stale RUNNING jobs as FAILED/ORPHANED |
| `EnterpriseDataRetentionScheduler` | 03:15 daily | `aihealthcare.enterprise.data.retention-cron` | Delete expired artifacts + logs, mark jobs EXPIRED |
| `EnterpriseDataPushScheduler` | Every 1 min | `aihealthcare.enterprise.data.push.sweep-cron` | DB sweeper: query due schedules, atomic claim via conditional UPDATE on `next_run_at`, execute job + email delivery |

---

## OpenAPI → Code Flow
1. Edit `api/src/main/resources/openapi.yaml`
2. Run `mvn generate-sources -pl api`
3. Generated interfaces land in `api/target/generated-sources/`
4. `web` controllers implement the generated delegate interfaces
5. Never edit generated files by hand

---

## Testing Pyramid

| Layer | Test type | AI calls? | Profile needed |
|-------|-----------|-----------|----------------|
| domain | Pure unit | No | none |
| domain service | Unit + mock ports | No (mock) | none |
| web | MockMvc slice (`@WebMvcTest`) | No (mock) | none |
| infrastructure/persistence | `@DataJpaTest` | No | none |
| infrastructure/ai | Smoke test | Yes | `ai-integration` |

**1,837 tests** across 249 test classes — all pass with `mvn test` (no live AI or network calls).

---

## LLM Wiki Layer

The wiki is a persistent, LLM-compiled knowledge base providing longitudinal context
to the newsletter.  Three ownership layers:

```
Immutable sources (NewsArticle)
        ↓ read-only
LLM-owned wiki (WikiPage, Contradiction, CompilationReport)
        ↓ query
Newsletter drafting (WikiQueryPort)
```

- **Compilation:** `KnowledgeCompilationPort.compileNewSources()` reads new articles,
  creates/updates wiki pages, detects contradictions, returns a `CompilationReport`.
  Raw sources are never mutated.
- **Query:** `WikiQueryPort` decouples consumers from compilation — semantic search
  via `findRelevantPages()`, direct lookup via `getPage()`, and contradiction feed
  via `recentContradictions()`.
- **Provenance:** Every wiki claim traces to `SourceRef` records linking back to
  `NewsArticle.articleId()`.

Domain records (`domain.model`): `WikiPageType`, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport`.
Ports (`domain.port.outbound`): `KnowledgeCompilationPort`, `WikiQueryPort`.

Adapter implementations completed in Slice W2.

---

## Deployment Shape

**Phase 1 (current plan):** Always-on Spring Boot app on a single small AWS instance
(EC2 or Lightsail).  The nightly pipeline is triggered internally by Spring `@Scheduled`
cron, which requires the JVM to be running at trigger time.  Chosen for operational
simplicity (one moving part, no AWS orchestration to learn) and because the planned
public wiki view (Slice W5) needs an always-on web app anyway.

**Phase 2 (optional later migration):** External trigger via Amazon EventBridge
Scheduler launching a run-and-exit ECS Fargate task (`ApplicationRunner` behind a
`batch` profile, exits after pipeline completes) to cut idle compute cost.  The
migration touches only the trigger mechanism, never the pipeline ports, so it can
be deferred safely.

**Note:** Postgres/pgvector (RDS) runs 24/7 in either shape and will dominate early
hosting cost, not the app.

**Hardening (ED-1):** The systemd unit enforces `ProtectSystem=strict` (read-only root),
`NoNewPrivileges`, `IPAddressDeny` blocking IMDS and private subnets, and `MemoryMax=3G`.
IMDSv2 is required on the instance (`--http-tokens required`). Enterprise data artifacts
live under `/var/lib/aihealthcare/data-exports` with a 14-day retention sweeper.
