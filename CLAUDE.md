tim# AIHealthcare — Claude Code Project Memory

## What This Project Is
A Spring Boot application that scrapes AI-in-Healthcare articles from the web weekly,
summarizes them with Spring AI, and produces a formatted newsletter draft with attributed sources.

See @docs/architecture.md for full module layout and design decisions.
See @docs/CONVENTIONS.md for all coding rules (logging, loops, records, class headers).

---

## LLM Behavioral Guidelines
> Adapted from [Karpathy guidelines](CLAUDE_karpathy.md). These complement the project-specific conventions below.

### Think Before Coding
- State assumptions explicitly. If uncertain, ask before implementing.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.

### Simplicity First
- No features beyond what was asked. No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- If you write 200 lines and it could be 50, rewrite it.

### Surgical Changes
- Don't "improve" adjacent code, comments, or formatting — touch only what you must.
- Don't refactor things that aren't broken. Match existing style.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked — mention it instead.
- Every changed line should trace directly to the user's request.

### Goal-Driven Execution
- Transform tasks into verifiable goals (e.g. "write a test that reproduces the bug, then fix it").
- For multi-step tasks, state a brief plan with verification checks per step.

---

## Module Structure
```
AIHealthcare/
├── api/                        # OpenAPI spec + generated DTOs (openapi.yaml lives here)
├── domain/                     # Pure domain records and port interfaces (no Spring deps)
├── application/                # Use-case orchestration, calls ports
├── infrastructure/
│   ├── ai/                     # Spring AI adapter (OpenAI / Anthropic)
│   ├── ingestion/              # Web scraping / article fetching adapter
│   └── delivery/               # Email / export adapter
└── web/                        # Spring MVC controllers, wires API → application layer
```

---

## Architecture Rules
- **Hexagonal / Ports-and-Adapters**: dependencies point inward — domain has zero framework imports.
- `domain` module must NOT depend on Spring, Spring AI, or any infrastructure library.
- Port interfaces live in `domain`; adapters implementing them live in `infrastructure/*`.
- Controllers in `web` call `application` services only — never domain or infrastructure directly.
- OpenAPI spec (`api/src/main/resources/openapi.yaml`) is the contract; code is generated from it, not the reverse.

---

## Core Domain Types
| Record / Interface          | Package                              | Notes                                                      |
|-----------------------------|--------------------------------------|------------------------------------------------------------|
| `NewsArticle`               | `domain.model`                       | Harvested/ingested article; carries source metadata fields |
| `Topic`                     | `domain.model`                       | Newsletter topic (name, slug, promptContext, tone, active) |
| `NewsletterSection`         | `domain.model`                       | AI-summarized section for one topic                        |
| `NewsletterDraft`           | `domain.model`                       | Full draft: intro + sections + sources                     |
| `ArticleIngestionPort`      | `domain.port.outbound`               | Fetch stored articles for a topic/keyword (use-case side)  |
| `ArticleHarvestingPort`     | `domain.port.outbound`               | Bulk-harvest raw articles from all configured feeds        |
| `AiSummarizationPort`       | `domain.port.outbound`               | Summarize articles via AI                                  |
| `IngestArticlesUseCase`     | `domain.port.inbound`                | Inbound port — drive ingestion                             |
| `GenerateNewsletterUseCase` | `domain.port.inbound`                | Inbound port — drive generation                            |
| `AiSearchResult`            | `domain.model`                       | Wraps query + topK + selected model names                  |
| `AiSearchSynthesis`         | `domain.model`                       | Single model's synthesis (summary + key findings)          |
| `Company`                   | `domain.model`                       | AI healthcare company (name, url, categories, description) |
| `CompanyDiscoveryResult`    | `domain.model`                       | Company discovery pipeline result                          |
| `ConductAiSearchUseCase`    | `domain.port.inbound`                | Inbound port — multi-model AI search                       |
| `DiscoverCompaniesUseCase`  | `domain.port.inbound`                | Inbound port — company discovery pipeline                  |
| `AiSearchPort`              | `domain.port.outbound`               | AI model synthesis adapter interface                       |
| `TrendSignal`               | `domain.model`                       | Keyword trend signal (30/90/180-day frequency + momentum)  |
| `TrendSnapshot`             | `domain.model`                       | Point-in-time snapshot of rising/fading/new keyword trends |
| `DetectTrendsUseCase`       | `domain.port.inbound`                | Inbound port — trend detection + latest/all snapshot retrieval |
| `TrendSnapshotPort`         | `domain.port.outbound`               | Persist and query trend snapshots                          |
| `RegulatoryEventType`       | `domain.model`                       | Enum: FDA_510K_CLEARANCE, DE_NOVO, PMA, CMS rules, etc.   |
| `RegulatoryBody`            | `domain.model`                       | Enum: FDA, CMS, ONC, OTHER                                |
| `RegulatoryEvent`           | `domain.model`                       | 13-field record: regulatory event with ref#, applicant, device |
| `MonitorRegulatoryEventsUseCase` | `domain.port.inbound`           | Inbound port — regulatory event retrieval + harvest trigger |
| `RegulatoryEventPort`       | `domain.port.outbound`               | Persist and query regulatory events                        |
| `RegulatoryHarvestingPort`  | `domain.port.outbound`               | Harvest regulatory events from external APIs               |
| `SentimentLabel`            | `domain.model`                       | Enum: POSITIVE, NEGATIVE, MIXED, NEUTRAL                   |
| `ArticleSentiment`          | `domain.model`                       | Per-article sentiment: articleId, title, label, confidence, rationale |
| `CompanySentiment`          | `domain.model`                       | Aggregated company sentiment: slug, score, counts, riskSummary |
| `AnalyzeCompanySentimentUseCase` | `domain.port.inbound`           | Inbound port — company sentiment analysis + retrieval      |
| `SentimentAnalysisPort`     | `domain.port.outbound`               | LLM-powered article sentiment classification               |
| `CompanySentimentPort`      | `domain.port.outbound`               | Persist and query company sentiments                        |
| `FrameworkAnalysis`         | `domain.model`                       | 10-field record: competitive analysis with 6 dimensions + strengths/weaknesses |
| `FrameworkDimension`        | `domain.model`                       | Scored dimension (name, score 1-10, rationale)             |
| `FrameworkCompany`          | `domain.model`                       | Config record: slug, name, url, topics for article matching |
| `AnalyzeFrameworksUseCase`  | `domain.port.inbound`                | Inbound port — framework competitive analysis              |
| `FrameworkAnalysisPort`     | `domain.port.outbound`               | Persist and query framework analyses                        |
| `FrameworkLlmPort`          | `domain.port.outbound`               | LLM-powered 6-dimension competitive scoring                 |

### `NewsArticle` field inventory (11 fields)
```
articleId    String   required, non-blank (entry URI, link URL, or UUID fallback)
title        String   required, non-blank
url          URI      required, non-null
bodyText     String   optional (RSS descriptions can be empty)
topic        String   required, non-blank (feed name or search keyword for grouping)
author       String   optional (null if not determinable)
topicId      Long     optional — null in Slice 1; populated from FeedSourceConfig in Slice 2
sourceName   String   optional — feed label (e.g. "PubMed AI Healthcare")
sourceTier   String   optional — "ACADEMIC", "REGULATORY", or "INDUSTRY"
sourceWeight double   baseline relevance multiplier [0.0, 1.0]; 0.5 for unknown
publishedAt  Instant  optional — null if not determinable
```
`bodyText` non-blank validation was intentionally relaxed — RSS descriptions are frequently empty.

---

## Development Conventions
- **Language**: Java 17 (LTS, minimum required by Spring AI 1.0.0), records for all immutable data types (domain models, DTOs, result types).
- **Build**: Maven single-module; run `mvn verify` from root to build.
- **Testing**: JUnit 5 + AssertJ + Mockito. AI smoke tests are `@Profile("ai-integration")` only — never run in CI.
- **Spec-Driven**: Always update `openapi.yaml` before implementing an endpoint. Use OpenAPI Generator Maven plugin to regenerate DTOs after spec changes.
- **Incremental slices**: Build one vertical slice at a time; each slice must be green before starting the next.
- **No orphan code**: Don't create classes, methods, or fields not yet required by the current slice.
- **No Streams**: Use traditional `for` loops for all iteration and transformation. No `.stream()`, `.map()`, `.collect()` etc.
- **Logging**: Lombok `@Slf4j` on every concrete class. First line logs all args: `log.debug("methodName() | param={}", val)`. Last line before every `return` logs the result: `log.debug("methodName() | return={}", result)`. Void methods log `return=void`.
- **Class headers**: Every file must have a Javadoc block with `@author Bill Blackmon`, `@since`, and `@updated` fields.
- **DI**: Constructor injection only — no `@Autowired` field injection.
- **Domain purity**: `domain` module has zero Spring/Lombok/AI imports — JDK only.

---

## Key Commands
```bash
# Build and verify
mvn verify

# Run the app locally
mvn spring-boot:run

# Run unit tests (no AI calls)
mvn test

# Run AI integration smoke tests
mvn test -Dspring.profiles.active=ai-integration
```

---

## Testing Strategy — Selective Test Execution

**Do NOT run the full test suite (`mvn test`) on every code change.** The suite has 1500+ tests
and takes 30+ minutes. Run only tests relevant to the changed code.

### Rules
1. **After modifying code**, run only the test classes whose production counterparts changed:
   ```bash
   mvn test -Dtest="FooServiceTest,BarControllerTest"
   ```
2. **Mapping changed files to tests**: for each modified `.java` file, find its test counterpart.
   - `FooService.java` → `FooServiceTest.java`
   - `FooController.java` → `FooControllerTest.java`
   - `FooAdapter.java` → `FooAdapterTest.java`
   - Domain records/enums → test classes that use them (check `import` statements)
3. **Template/CSS/YAML changes**: no test run needed unless a controller was also changed.
4. **Full suite**: run `mvn test` only when:
   - The user explicitly asks for it (e.g., `/test-run full`)
   - A pre-release or pre-merge validation is needed
   - A cross-cutting change affects many modules (e.g., security config, base class)
5. **If no test counterpart exists** for a changed file, skip testing for that file.
6. **Report** which tests were run and their pass/fail count.

---

## Infrastructure Classes (Slice 1 additions)
| Class                    | Package                                    | Notes                                              |
|--------------------------|--------------------------------------------|----------------------------------------------------|
| `FeedSourceConfig`       | `infrastructure.ingestion.feed`            | Immutable record: topicId, name, url, tier, weight |
| `FeedSourceProperties`   | `infrastructure.ingestion.feed`            | `@ConfigurationProperties(prefix="aihealthcare.feeds")` + `@Component` |
| `RomeFeedHarvester`      | `infrastructure.ingestion.feed`            | Implements `ArticleHarvestingPort` via Rome library |
| `FeedHarvestScheduler`   | `infrastructure.ingestion.feed`            | `@Scheduled` — daily (ACADEMIC/REGULATORY) + 4 h (INDUSTRY); calls `ArticleStoragePort.save()` |
| `ArticleIngestionAdapter`| `infrastructure.ingestion`                 | DB-backed; queries `NewsArticleRepository` by topic |
| `AiSummarizationAdapter` | `infrastructure.ai`                        | Spring AI `ChatClient` adapter                     |
| `AppConfig`              | `infrastructure.config`                    | `@EnableScheduling` + `@EnableConfigurationProperties(FeedSourceProperties.class)` |
| `StartupPipelineOrchestrator` | `infrastructure.scheduler`            | Sequences all 11 post-harvest pipelines; each step try-catch isolated |
| `FrameworkCompanyProperties` | `infrastructure.config`                | `@ConfigurationProperties(prefix="aihealthcare.frameworks")` — YAML-only company config |
| `FrameworkAnalysisLlmAdapter` | `infrastructure.ai`                   | ChatClient adapter: 6-dimension competitive scoring with structured parsing |
| `FrameworkAnalysisPersistenceAdapter` | `infrastructure.persistence`  | JSON-serialized dimensions/strengths/weaknesses/recentDevelopments |

### Feed harvesting YAML shape
```yaml
aihealthcare:
  feeds:
    sources:
      - topic-id: 1
        name: PubMed AI Healthcare
        url: https://pubmed.ncbi.nlm.nih.gov/rss/search/?term=artificial+intelligence+healthcare&format=rss
        tier: ACADEMIC        # ACADEMIC | REGULATORY | INDUSTRY
        base-weight: 0.9      # [0.0, 1.0]
        max-items: 50
```

### Harvesting pipeline (Slice 2a state)
```
FeedHarvestScheduler  →  RomeFeedHarvester  →  List<NewsArticle>
                                                       ↓
                                           ArticleStoragePort.save()
                                                       ↓
                                            ArticleStorageAdapter
                                                       ↓
                                            news_articles (H2 table)
```

---

## Infrastructure Classes (Slice 2a additions)
| Class                     | Package                              | Notes                                                    |
|---------------------------|--------------------------------------|----------------------------------------------------------|
| `NewsletterRenderer`      | `domain.service`                     | Pure Java — renders HTML + plain-text from `NewsletterDraft` |
| `NewsletterRunStatus`     | `domain.model`                       | Enum: DRAFT → SENT → ARCHIVED                            |
| `NewsletterRun`           | `domain.model`                       | Record: persisted newsletter run (html + plainText + status) |
| `ArticleStoragePort`      | `domain.port.outbound`               | `void save(List<NewsArticle>)` — silently skips duplicate URLs |
| `NewsletterRunPort`       | `domain.port.outbound`               | `save`, `findByRunId`, `findAll`                         |
| `TopicEntity`             | `infrastructure.persistence`         | JPA entity for `topics` table                            |
| `NewsArticleEntity`       | `infrastructure.persistence`         | JPA entity for `news_articles` table; url VARCHAR(2048), bodyText TEXT |
| `NewsletterRunEntity`     | `infrastructure.persistence`         | JPA entity for `newsletter_runs` table; htmlContent + plainTextContent as CLOB |
| `TopicRepository`         | `infrastructure.persistence`         | `JpaRepository` + `findByName(String)`                   |
| `NewsArticleRepository`   | `infrastructure.persistence`         | `JpaRepository` + `existsByUrl`, `findByTopic`           |
| `NewsletterRunRepository` | `infrastructure.persistence`         | `JpaRepository<NewsletterRunEntity, String>`             |
| `ArticleStorageAdapter`   | `infrastructure.persistence`         | Implements `ArticleStoragePort`; dedup via `existsByUrl` |
| `NewsletterRunAdapter`    | `infrastructure.persistence`         | Implements `NewsletterRunPort`; `toEntity`/`toDomain` helpers |
| `ArticleController`       | `web.controller`                     | `GET /api/v1/articles?topic=&limit=` (default 20)        |
| `NewsletterRunController` | `web.controller`                     | `GET /api/v1/runs` + `GET /api/v1/runs/{runId}`          |
| `ArticleResponse`         | `web.dto`                            | 9-field response record                                  |
| `RunSummaryResponse`      | `web.dto`                            | List-level summary (no HTML/text content)                |
| `RunDetailResponse`       | `web.dto`                            | Full run detail including htmlContent + plainTextContent |

### Persistence notes
- `application.yml` uses H2 in-memory, `ddl-auto: create-drop`, `defer-datasource-initialization: true`
- `data.sql` seeds one `topics` row + 3 prompt variants with `WHERE NOT EXISTS` guards
- `NewsletterService.generate()` now calls `NewsletterRenderer` then `NewsletterRunPort.save()` after building the draft
- `NewsletterService` retains in-memory `draftByDraftId` map for same-session `getDraft()` calls (sections/articles not stored in `newsletter_runs`)

---

## Current Slice
**Framework Competitive Analysis + Self-Maintaining Pipeline Orchestrator — COMPLETE — 1509 tests passing**
- [x] Domain: `FrameworkAnalysis` record (10 fields), `FrameworkDimension` record (score 1-10 validation), `FrameworkCompany` config record
- [x] Ports: `AnalyzeFrameworksUseCase` inbound (analyzeAll/getBySlug/getAll), `FrameworkAnalysisPort` + `FrameworkLlmPort` outbound
- [x] Service: `FrameworkAnalysisService` — collects articles per company, dedupes by articleId, MIN_ARTICLES=3 threshold, 6-dimension LLM scoring
- [x] Infrastructure: `FrameworkAnalysisLlmAdapter` (ChatClient structured parsing), `FrameworkAnalysisPersistenceAdapter` (JSON-serialized dimensions/strengths/weaknesses), `FrameworkCompanyProperties` (@ConfigurationProperties)
- [x] Persistence: `FrameworkAnalysisEntity` (company_slug PK, JSON TEXT columns), `FrameworkAnalysisRepository`
- [x] Web: `FrameworkDashboardController` at `GET /dashboard/frameworks` (radar chart + company cards) + `GET /dashboard/frameworks/{slug}` (detail with paragraph assessment)
- [x] Web: `FrameworkRestController` at `GET /api/v1/frameworks`, `GET /api/v1/frameworks/{slug}`, `POST /api/v1/frameworks/analyze`
- [x] Templates: `framework-analysis.html` (Chart.js radar chart + score cards), `framework-detail.html` (6-dimension breakdown + strengths/weaknesses)
- [x] Nav: "Framework Analysis" link in Reference dropdown
- [x] Config: `aihealthcare.frameworks.companies` YAML — adding companies is config-only, no code changes
- [x] `StartupPipelineOrchestrator` — sequences ALL 11 post-harvest pipelines (competitor pages → HuggingFace → regulatory → clinical trials → embedding → framework analysis → company discovery → sentiment → trends → legal trends → research). Each step isolated in try-catch.
- [x] `FeedHarvestScheduler` — `@PostConstruct` startup now cascades through full pipeline orchestrator; daily/industry harvests also trigger orchestrator
- [x] Tests: `FrameworkAnalysisTest` (13), `FrameworkAnalysisServiceTest` (7), `FrameworkDashboardControllerTest` (6), `FrameworkRestControllerTest` (5), `FrameworkAnalysisLlmAdapterTest` (8), `FrameworkAnalysisPersistenceAdapterTest` (5)

**Previously complete: Sentiment & Risk Scoring (S-SENT) — COMPLETE — 1465 tests passing**
- [x] Domain: `SentimentLabel` enum (POSITIVE/NEGATIVE/MIXED/NEUTRAL), `ArticleSentiment` record, `CompanySentiment` record (12 fields)
- [x] Ports: `AnalyzeCompanySentimentUseCase` inbound, `SentimentAnalysisPort` + `CompanySentimentPort` outbound
- [x] Service: `CompanySentimentService` — loads company profiles, fetches articles, delegates to LLM for per-article classification, aggregates into company-level scores
- [x] Infrastructure: `SentimentAnalysisAdapter` (ChatClient batch adapter with response parsing), `CompanySentimentEntity`, `CompanySentimentRepository`, `CompanySentimentAdapter` (JSON-serialized article sentiments)
- [x] Prompt: `sentiment-analysis.txt` — structured SENTIMENT_RESULTS format with label/confidence/rationale
- [x] Web: `SentimentDashboardController` at `GET /dashboard/risk` (overview + chart) + `GET /dashboard/risk/{slug}` (company detail with doughnut chart)
- [x] Web: `SentimentRestController` at `GET /api/v1/sentiment`, `GET /api/v1/sentiment/{slug}`, `POST /api/v1/sentiment/analyze`
- [x] Templates: `risk-dashboard.html` (horizontal bar chart + company cards with mini distribution bars), `risk-detail.html` (doughnut chart + article-level table)
- [x] Nav: "Sentiment & Risk" link in Reference dropdown
- [x] Tier gating: FREE=5 companies, SUBSCRIBER/DEMO/ADMIN=all
- [x] AppConfig: `companySentimentService()` bean wiring 4 ports
- [x] Tests: `CompanySentimentTest` (12), `CompanySentimentServiceTest` (10), `SentimentAnalysisAdapterTest` (8), `CompanySentimentAdapterTest` (5), `SentimentDashboardControllerTest` (8), `SentimentRestControllerTest` (6)

**Previously complete: Historical Trend Archive (T-HIST) — COMPLETE — 1416 tests passing**
- [x] Domain: `DetectTrendsUseCase.getAllSnapshots()` added to inbound port
- [x] Service: `TrendOrchestrationService.getAllSnapshots()` delegates to `TrendSnapshotPort.findAll()`
- [x] Web: `TrendHistoryController` — `GET /dashboard/trends/history` (multi-line chart + timeline table), `GET /dashboard/trends/history/{epochMillis}` (snapshot detail)
- [x] Web: `TrendRestController` — `GET /api/v1/trends/history` returns all snapshots as JSON
- [x] DTO: `TrendSnapshotSummary` — lightweight record for timeline table
- [x] Templates: `trend-history.html` (Chart.js multi-line chart + clickable snapshot table), `trend-history-detail.html` (bar chart + rising/new keyword cards with articles)
- [x] Nav: "Trend History" link in Content dropdown
- [x] Tier gating: FREE=4 snapshots, SUBSCRIBER/DEMO/ADMIN=full history
- [x] Tests: `TrendHistoryControllerTest` (8), `TrendRestControllerTest` updated (+2)

**Previously complete: Regulatory Alert System (R-REG) — COMPLETE — 1081 tests passing**
- [x] Domain: `RegulatoryEventType` enum (10 values), `RegulatoryBody` enum, `RegulatoryEvent` record (13 fields)
- [x] Ports: `MonitorRegulatoryEventsUseCase` inbound, `RegulatoryEventPort` + `RegulatoryHarvestingPort` outbound
- [x] Services: `RegulatoryEventService` (harvest dedup + retrieval), `RegulatoryWatchlistMatcher` (pure domain, keyword/company/topic matching against events)
- [x] Persistence: `RegulatoryEventEntity`, `RegulatoryEventRepository`, `RegulatoryEventAdapter` (pipe-delimited keywords)
- [x] Harvesters: `Fda510kHarvester` (openFDA 510(k) API), `FdaDeNovoHarvester` (openFDA classification API), `CmsRuleHarvester` (Federal Register API)
- [x] Infrastructure: `RegulatorySourceHarvester` interface, `CompositeRegulatoryHarvester` (aggregates sources, isolates failures), `RegulatoryHarvestProperties`
- [x] Scheduler: `RegulatoryHarvestScheduler` — daily 04:30 UTC via `${aihealthcare.regulatory.schedule}`, integrates with watchlist matching
- [x] Web: `RegulatoryController` at `GET /dashboard/regulatory` — tier-gated (FREE=5, SUBSCRIBER/DEMO/ADMIN=50), filter by FDA/CMS
- [x] Template: `regulatory.html` — summary badges, filter tabs, events table with color-coded type badges, upgrade prompt
- [x] Nav: "Regulatory" link added to all Thymeleaf templates
- [x] Tests: `RegulatoryEventTest` (13), `RegulatoryEventServiceTest` (10), `RegulatoryWatchlistMatcherTest` (12), `RegulatoryEventAdapterTest` (8), `RegulatoryHarvestSchedulerTest` (4), `Fda510kHarvesterTest` (3), `CompositeRegulatoryHarvesterTest` (3), `RegulatoryControllerTest` (7)

**Previously complete: Custom Watchlists (W-WATCH) — COMPLETE — 1021 tests passing**
- [x] Domain: `WatchlistItemType` enum, `WatchlistItem` record, `WatchlistMatch` record
- [x] Ports: `WatchlistPort`, `WatchlistMatchPort` outbound ports
- [x] Service: `WatchlistMatchingService` — keyword/company/topic matching with snippet extraction
- [x] Persistence: `WatchlistItemEntity`, `WatchlistMatchEntity`, repositories, `WatchlistItemAdapter`, `WatchlistMatchAdapter`
- [x] Scheduler: `FeedHarvestScheduler` — watchlist matching after wiki lint in both daily + industry harvests
- [x] Web: `WatchlistController` at `GET /watchlist` (Thymeleaf) — tier-gated to SUBSCRIBER/DEMO/ADMIN
- [x] Template: `watchlist.html` — add form, items grouped by type (pills), recent matches table
- [x] Nav: "Watchlist" link added to all Thymeleaf templates
- [x] Tests: `WatchlistMatchingServiceTest` (11), `WatchlistItemAdapterTest` (5), `WatchlistMatchAdapterTest` (4), `WatchlistControllerTest` (9)

**Previously complete: Trend Detection — COMPLETE — 965 tests passing**
- [x] Domain: `TrendDirection` enum, `TrendSignal` record, `TrendSnapshot` record
- [x] Ports: `DetectTrendsUseCase` inbound, `TrendSnapshotPort` outbound
- [x] Services: `TrendDetectionService` (keyword frequency analysis across 30/90/180-day windows), `TrendOrchestrationService` (article retrieval + analysis + persistence)
- [x] `ArticleIngestionPort.fetchRecentArticles(days)` + `ArticleIngestionAdapter` implementation
- [x] Persistence: `TrendSnapshotEntity`, `TrendSnapshotRepository`, `TrendSnapshotAdapter`
- [x] Scheduler: `TrendDetectionScheduler` — weekly Sunday 08:00 UTC via `${aihealthcare.trends.schedule}`
- [x] Web: `TrendController` at `GET /dashboard/trends` (Thymeleaf), `TrendRestController` at `/api/v1/trends/latest` + `/api/v1/trends/detect`
- [x] Template: `trends.html` — rising/fading/new keyword cards with momentum indicators
- [x] Nav: "Trends" link added to all Thymeleaf templates
- [x] Tests: `TrendDetectionServiceTest` (30), `TrendOrchestrationServiceTest` (4), `TrendSnapshotAdapterTest` (4), `TrendDetectionSchedulerTest` (3), `TrendControllerTest` (6), `TrendRestControllerTest` (4)

**Previously complete: Access Model Redesign (R1–R8) — COMPLETE — 909 tests passing**
_(see git log for details — 4-tier DEMO/FREE_PENDING/FREE/SUBSCRIBER, demo expiration, digest email, Stripe re-enable)_

**Previously complete: Vendor Compare UX Overhaul — COMPLETE — 862 tests passing**
_(see git log for details — vendor checkbox grid, compareSelected() port, VendorAssessmentService overload, vendor-compare.html)_

**Previously complete: Slice W5 — Reader-Facing Wiki Provenance UI — COMPLETE — 808 tests passing**
- [x] Web: `WikiController` — `GET /wiki` (index), `GET /wiki/{slug}` (detail), `GET /wiki/contradictions` (reversal watch)
- [x] Templates: `wiki-index.html` (searchable page grid with type filter), `wiki-detail.html` (rendered markdown + provenance table + contradictions + related pages + revision history), `wiki-contradictions.html` (side-by-side claims with date filter)
- [x] Dependency: `commonmark:0.24.0` — markdown→HTML rendering for wiki content
- [x] Security: `/wiki`, `/wiki/**` added to `permitAll()` (public read-only)
- [x] Repository: `WikiContradictionRepository.findByPageSlug()` — per-page contradiction lookup
- [x] Nav: "Wiki" link added to all 14 existing Thymeleaf templates
- [x] Tests: `WikiControllerTest` (8 MockMvc tests)

**Previously complete: Slice W2 — Wiki Persistence + Compilation Adapter — COMPLETE — 800 tests passing**
_(see git log for details — JPA entities, repositories, WikiCompilationAdapter, WikiResponseParser, REST trigger, scheduler wiring)_

**Previously complete: Slice W1 — LLM Wiki Domain Records, Ports & Tests — COMPLETE — 759 tests passing**
- [x] Domain: `WikiPageType` enum, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport` records
- [x] Port: `KnowledgeCompilationPort`, `WikiQueryPort` outbound ports
- [x] Tests: 40 domain unit tests

**Previously complete: Slice 47 — AI Search Enhancements (Fix + Gemini + UI) — COMPLETE — 719 tests passing**
- [x] Fix: Claude output bug — aligned all adapters to use multi-line SUMMARY parsing (`OpenAiSearchAdapter`, `PerplexityAiSearchAdapter` now use `StringBuilder` + `inSummary` flag, matching `AnthropicAiSearchAdapter`)
- [x] Infrastructure: `GeminiAiSearchAdapter` — raw `RestClient` to Gemini REST API (`gemini-3.5-flash`), graceful fallback when API key absent
- [x] Infrastructure: `GeminiApiResponse` — deserialization record (nested `GeminiCandidate`/`GeminiContent`/`GeminiPart`)
- [x] Config: `aihealthcare.gemini.api-key` in `application.yml` (from `GEMINI_API_KEY` env var)
- [x] UI: Gemini checkbox added to model selection, `.model-badge.gemini` indigo styling
- [x] UI: Synthesis cards now stacked (full-width) with colored left borders and subtle background tints per model
- [x] UI: Each card shows "[Model] Synthesis" heading for clear differentiation
- [x] Tests: `GeminiAiSearchAdapterTest` (6 tests)

**Previously complete: Slices 42-46 — COMPLETE — 713 tests passing**
_(see git log for details — Search Result Caching, Profile Page, Email Template, API Key Management, Dashboard Charts)_

**Previously complete: Slice 41 — New AI Healthcare Companies — COMPLETE — 681 tests passing**
- [x] Domain: `Company`, `CompanyDiscoveryResult`, `CompanyTags` records
- [x] Domain: `DiscoverCompaniesUseCase` inbound port; `CompanyScrapingPort` outbound port
- [x] Domain services: `CompanyClassifier` (8 subcategories), `CompanyDeduplicator` (fuzzy name matching), `CompanyDiscoveryService`, `CompanyNewsletterRenderer`
- [x] Infrastructure: `StartupDirectoryHarvester` — scrapes YC, TopStartups, anchors incumbents
- [x] Web: `CompanyDiscoveryController` — `POST /api/v1/companies/discover` (REST, SUBSCRIBER-only)
- [x] Grounded vendor scoring: Doc Frequency + TF-IDF algorithms with source reference table
- [x] UI polish: inline sort arrows, article dedup by title, body text previews, loading spinners
- [x] Tests: `CompanyClassifierTest` (8), `CompanyDeduplicatorTest` (6), `CompanyDiscoveryServiceTest` (7), `CompanyNewsletterRendererTest` (9), `StartupDirectoryHarvesterTest` (8), `CompanyDiscoveryControllerTest` (6)

**Previously complete: Slice 40 — Merge Semantic Search into AI Search — 627 tests passing**
- [x] Unified search at `/research/ai-search` — merged Semantic Search + AI Search into single page
- [x] `SemanticSearchController` converted to 302 redirect → `/research/ai-search`
- [x] NO_MATCH relevance gating — LLMs return null when articles are irrelevant to query
- [x] Model selection checkboxes (default Claude only) for cost optimization
- [x] Tests: `AiSearchControllerTest` (7), `SemanticSearchControllerTest` (4)

**Previously complete: Slice 39 — Simplify Search Criteria + Expand AI Synthesis — 631 tests passing**
- [x] `ArticleSearchCriteria` reduced from 10 to 7 fields (removed `sourceTier`, `createdFrom`, `createdTo`)
- [x] AI synthesis depth expanded: 8-12 sentences + 5-10 key findings
- [x] Healthcare Dive AI feed added (INDUSTRY tier, keyword-filtered)
- [x] Articles search window widened: 7 → 60 days

**Previously complete: Slice 38 — AI Search UI Cleanup — 633 tests passing**
- [x] Numbered `[N]` article references with citation styling
- [x] Default topK: 10 → 20 for more comprehensive synthesis
- [x] Article cards simplified to topic badge only

**Previously complete: Slice 37 — AI-Enhanced Search with Multi-Model LLM Synthesis — 631 tests passing**
- [x] Domain: `AiSearchResult`, `AiSearchSynthesis` records
- [x] Domain: `ConductAiSearchUseCase` inbound port; `AiSearchPort` outbound port
- [x] Domain service: `AiSearchService` — vector search + parallel fan-out to AI adapters
- [x] Infrastructure: `AnthropicAiSearchAdapter` (Claude), `OpenAiSearchAdapter` (GPT), `PerplexityAiSearchAdapter` (Perplexity Sonar)
- [x] Web: `AiSearchController` at `/research/ai-search` — Thymeleaf with tier gating + usage metering
- [x] Web: `AiSearchRestController` — `GET /api/v1/search/ai` REST endpoint
- [x] Prompt: `ai-search-synthesis.txt` — shared multi-model synthesis template
- [x] Tests: `AiSearchServiceTest` (5), `AiSearchControllerTest` (6), `AiSearchRestControllerTest` (4), `PerplexityAiSearchAdapterTest` (6)

**Previously complete: Slice 36 — Multi-Field Article Search — 610 tests passing**
_(see git log for details)_

**Previously complete: Slice 35 — Admin User Management Actions — 585 tests passing**
_(see git log for details)_

**Previously complete: Slice 34 — HuggingFace Enrichment + Source Tier Display Cleanup — 577 tests passing**
_(see git log for details)_

**Previously complete: Slice 33 — Admin Panel — 577 tests passing**
_(see git log for details)_

**Previously complete: Slice 32 — Role-Based Access Control — 573 tests passing**
_(see git log for details)_

**Previously complete: Slice 31 — Member-Only Semantic Search — 567 tests passing**
_(see git log for details)_

**Previously complete: Slice 30 — Archive Depth Gating — 559 tests passing**
_(see git log for details)_

**Previously complete: Slice 29 — Spring Security Session-Based Authentication — 551 tests passing**
_(see git log for details)_

**Previously complete: Slice 28 — Tier Rename + Usage Metering + Feature Gating — 542 tests passing**
_(see git log for details)_

**Previously complete: Slice 27 — Tier-Based Content Gating — 520 tests passing**
_(see git log for details)_

**Previously complete: Slice 26 — Cron Job Consolidation + Newsletter Draft-First Workflow — COMPLETE — 501 tests passing**
- [x] All cron expressions externalized to `application.yml` — no more hardcoded `@Scheduled` annotations
- [x] `FeedHarvestScheduler` — daily cron via `${aihealthcare.harvest.daily-cron}`, industry rate via `${aihealthcare.harvest.industry-rate-ms}`
- [x] `WebMonitoringScheduler` — competitor cron via `${aihealthcare.harvest.competitor-cron}`, HuggingFace via `${aihealthcare.harvest.huggingface-cron}`
- [x] `NewsletterGenerationScheduler` — **draft-only**: generates DRAFT newsletter but does NOT auto-send; removed `DeliverNewsletterUseCase` dependency; renamed to `runDailyDraftGeneration()`
- [x] Newsletter workflow: scheduler creates draft at midnight → review/edit at `/newsletter/runs/{runId}/edit` → send manually via Send button
- [x] Consolidated daily timeline (all UTC): 04:00 RSS+Research → 05:00 Competitor → 05:30 HuggingFace → 06:00+ Industry (every 4h) → 07:00 Embedding → 00:00 Newsletter draft
- [x] Tests: `NewsletterGenerationSchedulerTest` updated (4 tests, draft-only assertions)

**Previously complete: Slice 25 — Topic Summary AI Generation — 502 tests passing**
- [x] Domain: `TopicSummary` record, `TopicSummaryPort`, `TopicSummaryGenerationService`
- [x] `AiSummarizationPort.generateTopicSummary()` + adapter + `topic-summary.txt` prompt
- [x] Persistence: `TopicSummaryEntity`, `TopicSummaryRepository`, `TopicSummaryAdapter`
- [x] `FeedHarvestScheduler` — auto-generates summaries after harvest; `POST /monitoring/summaries` manual trigger
- [x] `news-listing.html` — "AI Summary" box under each topic section
- [x] Tests: `TopicSummaryGenerationServiceTest` (5), `TopicSummaryAdapterTest` (4)

**Previously complete: Slice 24 — Perplexity Healthcare news source refactor — 502 tests passing**
- [x] 5 Perplexity feed sources: Hub (keyword-filtered), Health Blog, Premium Health Sources, PR Newswire (keyword-filtered), Sonar API
- [x] `application.yml` — Perplexity Healthcare topic entries under `feeds.sources` and `news.topics`

**Previously complete: Slice 23 — OpenAI Healthcare & Google Healthcare news source refactor — 502 tests passing**
- [x] OpenAI Healthcare: 7 sources — Google News, OpenAI for Healthcare, ChatGPT Health, OpenAI Index (keyword-filtered), MedCity News, Healthcare Dive, Crescendo AI
- [x] Google Healthcare: 3 sources — Google Health AI, Check Up Health AI, Med-PaLM Research
- [x] `application.yml` — topic entries under `feeds.sources` and `news.topics`

**Previously complete: Slice 22 — Anthropic Healthcare news source refactor — 502 tests passing**
- [x] 7 Anthropic feed sources: Healthcare AI page, General News (keyword-filtered), FierceHealthcare, ClinicalTrialsArena, IntrepidGP, Chartis, Goodie AI (keyword-filtered)
- [x] `FeedSourceConfig` — `keywords` field for content-based filtering on web-scraped pages
- [x] `WebPageHarvester` / `RomeFeedHarvester` — keyword matching support
- [x] `application.yml` — Anthropic Healthcare topic entries + Amazon Connect Health (15 sources)

**Previously complete: Slice 21 — Newsletter Preview/Edit UI with TinyMCE — 485 tests passing**
- [x] `NewsletterPreviewController` — `GET /newsletter/runs` (list), `GET /newsletter/runs/{runId}/edit` (TinyMCE editor), `POST .../save`, `POST .../send`
- [x] `newsletter-runs.html` — run list with status badges and edit links for DRAFT runs
- [x] `newsletter-edit.html` — TinyMCE 7.9.0 WYSIWYG editor with save/send buttons; Jsoup HTML→plain-text on save
- [x] TinyMCE 7.9.0 WebJar dependency in `pom.xml`
- [x] "Newsletter Preview" nav link added to all 6 existing Thymeleaf templates
- [x] Tests: `NewsletterPreviewControllerTest` (6 MockMvc tests)

**Previously complete: Slice 20 — News Listing UI + Beckers feed + topic/name separation — 475 tests passing**
- [x] `GET /dashboard/news` Thymeleaf page — articles grouped under 9 configurable topic section headers
- [x] `FeedSourceConfig` — new `topic` field + `effectiveTopic()` fallback separating feed identity from grouping
- [x] `NewsTopicProperties` — `@ConfigurationProperties` binding for topic display order
- [x] Beckers Hospital Review added as INDUSTRY RSS feed
- [x] `DashboardControllerTest` expanded (17 tests total)

**Previously complete: Slice 19 — Vendor Compare UI — 470 tests passing**
- [x] Domain: `CompareVendorsUseCase` inbound port
- [x] Domain: `VendorAssessmentService` — calls AI with vendor-compare prompt, parses `List<VendorAssessment>`
- [x] Prompt: `vendor-compare.txt` — structured `## VendorName` + `STRENGTHS/WEAKNESSES/RELEVANCE` format
- [x] `ResearchOrchestratorService` — now implements `CompareVendorsUseCase`; `compare()` runs COMBINED pipeline then delegates to `VendorAssessmentService`
- [x] `AppConfig` — loads `vendor-compare.txt`, wires `VendorAssessmentService` into orchestrator
- [x] Web: `VendorCompareController` at `GET /research/vendors` — form + vendor card grid, graceful error handling
- [x] Template: `vendor-compare.html` — vendor cards with relevance bar, strengths (+), weaknesses (−)
- [x] Tests: `VendorAssessmentServiceTest` (4), `VendorCompareControllerTest` (5)

**Previously complete: Slice 18 — Research Dashboard UI — 460 tests passing**
- [x] Web: `ResearchDashboardController` — `GET /research/runs` (list), `GET /research/runs/{runId}` (detail)
- [x] Templates: `research-runs.html` (run history table with mode badge, citation count, UTC timestamp), `research-run-detail.html` (full detail card)
- [x] `dashboard.html` nav updated with Research Compare / Research Runs / Vendor Compare links
- [x] Fix: server-side timestamp formatting via `runTimestamps` map (avoids `#temporals` Instant zone issue)
- [x] Tests: `ResearchDashboardControllerTest` (5 MockMvc tests)

**Previously complete: Slice 17 — ResearchHarvestScheduler — ~458 tests passing**
- [x] `ResearchHarvestScheduler` + `ResearchHarvestProperties` — daily COMBINED pipeline per configured topic
- [x] `application.yml` — `aihealthcare.research.harvest.cron`, `max-sources-per-topic`, `topics` list
- [x] Proactively pre-warms `research_runs` table so UI has data before a user manually queries

**Previously complete: Slice 16 — COMBINED Research Mode — ~455 tests passing**
- [x] `ResearchMode.COMBINED` — merges Perplexity API + DB article sources before synthesis
- [x] `ResearchOrchestratorService` updated to fan out to both adapters and dedup by URL
- [x] `application.yml` — default mode changed to `COMBINED`

**Previously complete: Slice 15 — Research Result Persistence — ~445 tests passing**
- [x] `ResearchRunEntity` + `ResearchRunRepository` + `ResearchRunAdapter` — persists every successful research call
- [x] `ResearchRunController` — `GET /api/v1/research/runs`, `GET /api/v1/research/runs/{runId}`
- [x] `ResearchRunResponse` DTO; `ResearchExportPort` + `NotebookLMResearchExportAdapter`
- [x] Tests: `ResearchRunAdapterTest`, `ResearchRunControllerTest`

**Previously complete: Slice 14 — Perplexity Live Integration + Research Compare Resilience — ~430 tests passing**
- [x] `PerplexityHarvester` upgraded to live Perplexity Sonar API (requires `PERPLEXITY_API_KEY`)
- [x] `PerplexityApiResponse` deserialization record
- [x] `ResearchCompareController` resilience: graceful fallback when one mode fails

**Previously complete: Slice 13 — Research Compare UI — ~420 tests passing**
- [x] `ResearchCompareController` — `GET /research/compare` Thymeleaf page
- [x] `research-compare.html` — side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH results

**Previously complete: Slice 12 — Research Refactor: Perplexity-Style Staged Research Architecture — 415 tests passing**
- [x] Domain: `ResearchMode` enum, `ResearchRequest`, `ResearchPlan`, `RetrievalQuery`, `RetrievedSource`, `SourceCitation`, `ResearchSection`, `ResearchAnswer`, `VendorAssessment` records
- [x] Domain: `SourceRetrievalPort` outbound, `ConductResearchUseCase` inbound
- [x] Domain services: `CitationAssembler`, `ResearchPlanningService`, `ResearchSynthesisService`, `ResearchOrchestratorService`
- [x] Infrastructure: `LegacyGoogleResearchAdapter`, `PerplexityResearchAdapter` — package `infrastructure.research`
- [x] Prompt templates: `research-plan.txt`, `research-synthesis.txt`
- [x] Web: `ResearchController` — `POST /api/v1/research`; DTOs: `ResearchRequestDto`, `ResearchAnswerDto`, `ResearchSectionDto`, `SourceCitationDto`
- [x] Tests: 31 new tests

**Migration to STAGED_RESEARCH:** set `PERPLEXITY_API_KEY` env var + `aihealthcare.research.mode: STAGED_RESEARCH` in `application.yml`. Zero code changes required.

**Previously complete: Slice 11 — Analytics Dashboard UI — 379 tests passing**
- [x] Thymeleaf starter in `pom.xml`; `DashboardController` — `GET /dashboard`, `GET /dashboard/articles?topic=&sort=`
- [x] `ArticleIngestionPort.fetchAllByTopic()` + adapter; `dashboard.html` + `articles.html` templates

**Previously complete: Slice 10 — Analytics Dashboard REST — 372 tests passing**
- [x] `IngestionAnalytics`, `RunAnalytics`, `EvaluationAnalytics` records; `AnalyticsController` — 3 GET endpoints

**Previously complete: Slice 7 — RAG Generation + Document Ingestion + HTML Export — COMPLETE — 333 tests passing**
- [x] `pom.xml` — added `pdfbox:3.0.3` and `poi-ooxml:5.3.0` for PDF/DOCX parsing
- [x] Domain: `DocumentChunk`, `DocumentIngestionResult` records
- [x] Domain: `IngestDocumentsUseCase` inbound port — `ingest(directory, sourceLabel, chunkSize)`
- [x] Domain: `DocumentVectorPort` outbound port — `store(List<DocumentChunk>)`
- [x] Domain: `FileParserPort` outbound port — `supports(Path)`, `parse(Path)`
- [x] Domain service: `DocumentIngestionService` — directory scan, multi-parser dispatch, chunk splitting, vector store write
- [x] `AiSummarizationPort` — added `summarizeWithContext(fresh, context, topic, tone, sectionId)` for RAG
- [x] `GenerateNewsletterUseCase` / `NewsletterService` — new `ragEnabled` + `ragContextCount` params; RAG path calls `searchPort.findSimilar()` + deduplicates + delegates to `summarizeWithContext`
- [x] `GenerateRequest` — new `ragEnabled` (Boolean) + `ragContextCount` (Integer) optional fields
- [x] `NewsletterController` — defaults `ragEnabled=false`, `ragContextCount=3` when fields are null
- [x] `AppConfig` — wires `ArticleSearchPort` into `NewsletterService`; no-op fallback beans for `ArticleSearchPort` and `IngestDocumentsUseCase` when pgvector is absent
- [x] Infrastructure: `PdfParser`, `DocxParser`, `PlainTextParser` — `@Component` adapters implementing `FileParserPort`
- [x] Infrastructure: `DocumentIngestionAdapter` — `@ConditionalOnBean(VectorStore.class)` implements `DocumentVectorPort`
- [x] Infrastructure: `NoOpArticleSearchAdapter` — `@ConditionalOnMissingBean` fallback (RAG disabled gracefully)
- [x] `NotebookLMService` — added companion `.html` export alongside `.txt` summary; articles grouped by source, styled card layout, inline CSS, HTML-escaped titles
- [x] Web: `DocumentIngestionController` — `POST /api/v1/documents/ingest`; maps `IllegalArgumentException` → 400 via `GlobalExceptionHandler`
- [x] DTOs: `DocumentIngestRequest`, `DocumentIngestResponse`
- [x] OpenAPI spec updated: `/api/v1/documents/ingest`, `/api/v1/search-prompts`, `/api/v1/search-prompts/{engine}`
- [x] `application-h2.yml` — H2 test profile config
- [x] `prompts/summarize-articles-rag.txt` — RAG-enhanced summarization prompt template
- [x] Tests: `DocumentIngestionServiceTest` (8), `NewsletterServiceRagTest` (4), `DocumentIngestionControllerTest` (5), `NotebookLMServiceTest` HTML export (5 new, 29 total)

**Previously complete: Slice 6 — Prompt Refactoring — 323 tests passing**
- [x] Domain: `SearchPromptConfig` record (engine, name, templateText, description, active)
- [x] Domain: `SearchPromptPort` outbound — `findByEngine`, `findAll`, `save`
- [x] JPA: `SearchPromptEntity` + `SearchPromptRepository` + `SearchPromptAdapter`
- [x] `PromptLoaderService` — filesystem-first template loader; falls back to classpath
- [x] `FeedTier.PERPLEXITY` — new enum value; `RomeFeedHarvester` filters it out
- [x] `PerplexityHarvester` — stub; returns empty list when `PERPLEXITY_API_KEY` absent
- [x] `data.sql` — `search_prompts` table + `GooglePrompt` + `PerplexityPrompt` seeds
- [x] Web: `SearchPromptController` — `GET /api/v1/search-prompts`, `GET /api/v1/search-prompts/{engine}`, `PUT /api/v1/search-prompts/{engine}`
- [x] OpenAPI spec updated with search-prompts endpoints
- [x] Tests: `SearchPromptAdapterTest` (6), `PerplexityHarvesterTest` (5), `SearchPromptControllerTest` (5)

**Previously complete: Slice 5 — Web Monitoring & Competitive Intelligence — COMPLETE — 264 tests passing**
- [x] `pom.xml` — added `org.jsoup:jsoup:1.18.3` for HTML scraping
- [x] `FeedTier` enum — added `COMPETITOR` (daily web page scraping) and `HUGGINGFACE` (API model discovery)
- [x] Domain: `ContentHashPort` outbound port — `getHash(url)`, `saveHash(url, hash)` for change detection
- [x] JPA: `PageContentHashEntity` (page_url PK, content_hash, last_checked_at) + `PageContentHashRepository`
- [x] `ContentHashAdapter` — implements `ContentHashPort` with upsert behavior
- [x] `WebPageHarvester` — jsoup scraper with SHA-256 change detection; CSS selector `main, article, [role=main]` with body fallback; 10K char truncation; snapshot URL fragments bypass dedup
- [x] `HuggingFaceHarvester` — public API client discovering healthcare LLMs; maps models to `NewsArticle`; `hf-{modelId}` article IDs
- [x] `HuggingFaceModelResponse` — infrastructure deserialization record
- [x] `WebMonitoringScheduler` — daily at 07:00 UTC (competitor pages) + 07:30 UTC (HuggingFace); separate from RSS `FeedHarvestScheduler`
- [x] `RomeFeedHarvester` — updated to filter out COMPETITOR/HUGGINGFACE tiers (RSS-only)
- [x] `application.yml` — 4 competitor sources (Anthropic x2, Perplexity, Google) + 1 HuggingFace source configured
- [x] Web: `WebMonitoringController` — `POST /monitoring/harvest`, `POST /monitoring/huggingface`, `GET /monitoring/hashes`
- [x] DTOs: `HarvestResultResponse`, `HuggingFaceHarvestResponse`, `PageHashResponse`
- [x] OpenAPI spec updated with 3 monitoring endpoints + 3 new schemas; `sourceTier` enum updated
- [x] Tests: `WebPageHarvesterTest` (9), `WebMonitoringSchedulerTest` (6), `ContentHashAdapterTest` (4), `HuggingFaceHarvesterTest` (6), `WebMonitoringControllerTest` (6)

**Previously complete: Slice 4 — Prompt Evaluation — 233 tests**
- [x] Domain: `PromptVariant`, `EvaluationResult`, `ComparisonResult`, `EvaluationScore` records
- [x] Domain: `PromptVariantNotFoundException`, `EvaluationNotFoundException` exceptions
- [x] Domain service: `PromptEvaluationService` — variant CRUD, evaluate, compare, result retrieval (11 operations)
- [x] Inbound port: `EvaluatePromptsUseCase`
- [x] Outbound ports: `PromptVariantPort`, `AiEvaluationPort`, `EvaluationResultPort`
- [x] JPA entities: `PromptVariantEntity`, `EvaluationResultEntity`, `ComparisonResultEntity`
- [x] JPA repositories: `PromptVariantRepository`, `EvaluationResultRepository`, `ComparisonResultRepository`
- [x] Adapters: `PromptVariantAdapter`, `EvaluationResultAdapter` (article IDs pipe-delimited), `AiEvaluationAdapter` (LLM-as-judge via ChatClient)
- [x] Web: `PromptVariantController`, `PromptEvaluationController`
- [x] DTOs: `VariantRequest`/`VariantResponse`, `EvaluateRequest`, `EvaluationResultResponse`, `CompareRequest`, `ComparisonResultResponse`, `EvalSectionResponse`, `EvaluationScoreResponse`
- [x] `GlobalExceptionHandler` — `PromptVariantNotFoundException` → 404, `EvaluationNotFoundException` → 404
- [x] `AppConfig` — `@Bean promptEvaluationService()` wiring 5 ports
- [x] Prompt template: `evaluate-section.txt` (LLM-as-judge scoring on 5 dimensions)
- [x] `data.sql` — 3 seed prompt variants (concise baseline, detailed analysis, plain language)
- [x] OpenAPI spec updated with `/api/v1/variants` (POST, GET, GET/{id}, DELETE), `/api/v1/evaluations` (POST, GET, GET/{id}), `/api/v1/comparisons` (POST, GET, GET/{id})
- [x] Tests: `PromptEvaluationServiceTest`, `AiEvaluationAdapterTest`, `PromptVariantAdapterTest`, `EvaluationResultAdapterTest`, `PromptVariantControllerTest` (6), `PromptEvaluationControllerTest` (9)

**Previously complete: Slice 3 — Scheduling + Delivery — 137 tests**
- [x] Domain: `Subscriber` record, `DuplicateSubscriberException`, `SubscriberNotFoundException`
- [x] Ports: `ManageSubscribersUseCase`, `DeliverNewsletterUseCase` (inbound); `SubscriberPort`, `NewsletterDeliveryPort` (outbound)
- [x] `DeliveryService` — implements both use cases; wires run lookup → active-subscriber fetch → delivery dispatch → flip run status to `SENT`
- [x] Persistence: `SubscriberEntity`, `SubscriberRepository` (`findByEmail`, `findAllByActiveTrue`), `SubscriberAdapter`
- [x] `EmailDeliveryAdapter` — `JavaMailSender` with MIME multipart (HTML + plain-text alternative); per-recipient failures caught and logged
- [x] `NewsletterGenerationScheduler` — `@Scheduled(cron = "${aihealthcare.newsletter.schedule}")`; runs `ingest → generate → deliver` end-to-end; swallows exceptions so the thread survives; depends only on inbound ports
- [x] Web: `SubscriberController`, `NewsletterDeliveryController`, `SubscriberRequest`/`SubscriberResponse`/`DeliverRequest` DTOs
- [x] `GlobalExceptionHandler` — `DuplicateSubscriberException` → 409, `SubscriberNotFoundException` → 404
- [x] `application.yml` — `aihealthcare.newsletter.{from-address, schedule}`; profile-specific SMTP in `application-dev.yml` / `application-prod.yml`
- [x] OpenAPI spec updated with `/api/v1/subscribers` (POST, GET, DELETE) and `/api/v1/newsletter/deliver` (POST)
- [x] Tests: `DeliveryServiceTest` (8), `DeliveryServiceDeliverTest` (4), `SubscriberAdapterTest` (6 @DataJpaTest), `EmailDeliveryAdapterTest` (4), `SubscriberControllerTest` (7), `NewsletterDeliveryControllerTest` (2), `NewsletterGenerationSchedulerTest` (5 Mockito + InOrder)

**Previously complete: Slice 2b — Vector Store**
- [x] `pom.xml` — added `spring-ai-vector-store` (Spring AI 1.0.0 split this into its own module); added resources directory config so `application.yml` is on the classpath
- [x] `AppConfig` — manually registers `SimpleVectorStore` bean (Spring AI 1.0.0 has no auto-config for it; needs `EmbeddingModel` from the OpenAI starter)
- [x] `EmbeddingScheduler` — `@Scheduled` job embeds all `NewsArticleEntity` rows into the vector store; idempotent (re-run safe); swallows embedding exceptions so the scheduler thread stays alive
- [x] `VectorStoreArticleSearchAdapter` — implements `ArticleSearchPort`; fixed `SearchRequest` API to builder pattern (`SearchRequest.builder().query(...).topK(...).build()` — `SearchRequest.query()` was removed in Spring AI 1.0.0)
- [x] Tests: `EmbeddingSchedulerTest` (6 Mockito), `VectorStoreArticleSearchAdapterTest` (8 Mockito)

**Previously complete: Slice 2a — JPA Persistence (H2)**
- [x] `pom.xml` — added `spring-boot-starter-data-jpa`, `h2` runtime
- [x] Domain: `NewsletterRun`, `NewsletterRunStatus`, `NewsletterRenderer`, `ArticleStoragePort`, `NewsletterRunPort`
- [x] JPA entities: `TopicEntity`, `NewsArticleEntity`, `NewsletterRunEntity`
- [x] JPA repositories: `TopicRepository`, `NewsArticleRepository`, `NewsletterRunRepository`
- [x] Adapters: `ArticleStorageAdapter` (dedup by URL), `NewsletterRunAdapter`, `ArticleIngestionAdapter` (DB-backed)
- [x] Web: `ArticleController`, `NewsletterRunController`, `ArticleResponse`, `RunSummaryResponse`, `RunDetailResponse`
- [x] `application.yml` + `data.sql` (topics seed)
- [x] OpenAPI spec updated with all 6 endpoints
- [x] Tests: `NewsletterRendererTest` (12), `ArticleStorageAdapterTest` (5 @DataJpaTest), `NewsletterServiceGenerateTest` (4 Mockito/ArgumentCaptor)

---

## LLM Wiki Layer (Slice W1)

The wiki is a persistent, LLM-compiled knowledge base that gives the newsletter longitudinal
context ("this reverses the FDA's March guidance").  Three ownership layers:

1. **Immutable sources** — `NewsArticle` records harvested by the existing pipeline.  Never mutated by the wiki layer.
2. **LLM-owned wiki** — `WikiPage` records compiled from sources by the `KnowledgeCompilationPort`.  Pages carry first-class provenance (`SourceRef`) and structured contradiction tracking (`Contradiction`).  Every compilation run produces an observable `CompilationReport`.
3. **Query layer** — `WikiQueryPort` decouples newsletter drafting from compilation.  Consumers search by semantic similarity, retrieve pages by slug, or query recent contradictions.

### Domain records (Slice W1)
| Record | Package | Purpose |
|--------|---------|---------|
| `WikiPageType` | `domain.model` | Enum: `ENTITY`, `CONCEPT`, `COMPARISON`, `OVERVIEW` |
| `SourceRef` | `domain.model` | Provenance link: articleId + sourceName + harvestedOn + excerpt |
| `WikiPage` | `domain.model` | Compiled wiki page with slug, tags, markdown, sources, cross-refs |
| `Contradiction` | `domain.model` | Prior claim vs new claim with source lists on both sides |
| `CompilationReport` | `domain.model` | Run summary: pages created/updated, contradictions, warnings |

### Port interfaces (Slice W1)
| Port | Package | Methods |
|------|---------|---------|
| `KnowledgeCompilationPort` | `domain.port.outbound` | `compileNewSources(List<NewsArticle>)` → `CompilationReport` |
| `WikiQueryPort` | `domain.port.outbound` | `findRelevantPages(query, maxResults)`, `getPage(slug)`, `recentContradictions(since)` |

Compilation will run after harvest via the existing `@Scheduled` trigger (wiring deferred to Slice W2).

See `docs/ROADMAP.md` for wiki slice roadmap (W2–W8).

---

## What NOT to Do
- Do not add auth/security until explicitly requested.
- Do not modify `openapi.yaml` without confirming the change first.
- Do not place business logic in controllers or adapters.
- Do not use `@Autowired` field injection — constructor injection only.
- Do not put outbound port interfaces in `infrastructure` packages — they belong in `domain.port.outbound`.
- Do not use `com.aihealthcare.*` as the base package — all code uses `com.wgblackmon.aihealthcare.*`.
