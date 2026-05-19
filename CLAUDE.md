tim# AIHealthcare — Claude Code Project Memory

## What This Project Is
A Spring Boot application that scrapes AI-in-Healthcare articles from the web weekly,
summarizes them with Spring AI, and produces a formatted newsletter draft with attributed sources.

See @docs/architecture.md for full module layout and design decisions.
See @docs/CONVENTIONS.md for all coding rules (logging, loops, records, class headers).

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
**Slice 21 — Newsletter Preview/Edit UI with TinyMCE — COMPLETE — 485 tests passing**
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

## What NOT to Do
- Do not add auth/security until explicitly requested.
- Do not modify `openapi.yaml` without confirming the change first.
- Do not place business logic in controllers or adapters.
- Do not use `@Autowired` field injection — constructor injection only.
- Do not put outbound port interfaces in `infrastructure` packages — they belong in `domain.port.outbound`.
- Do not use `com.aihealthcare.*` as the base package — all code uses `com.wgblackmon.aihealthcare.*`.
