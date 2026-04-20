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
**Slice 5 — Web Monitoring & Competitive Intelligence — COMPLETE — 264 tests passing**
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
