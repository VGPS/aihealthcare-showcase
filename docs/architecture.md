# AIHealthcare — Architecture Reference

> Last updated: 2026-05-13 | Reflects Slice 18 (18 slices complete, 460 tests passing)

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
| 9 | Market Intelligence Service (monthly AI report) | 358 |
| 10 | Analytics Dashboard REST (ingestion / runs / evaluations) | 372 |
| 11 | Analytics Dashboard UI — Thymeleaf at `/dashboard` | 379 |
| 12 | Research Refactor: Perplexity-style staged research architecture | 415 |
| 13 | Research Compare UI: side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH | ~420 |
| 14 | Perplexity Live Integration + Research Compare Resilience | ~430 |
| 15 | Research Result Persistence: durable ResearchRun records + export | ~445 |
| 16 | COMBINED Research Mode: merge Perplexity + DB sources before synthesis | ~455 |
| 17 | ResearchHarvestScheduler: proactive daily COMBINED pipeline per topic | ~458 |
| 18 | Research Dashboard UI — Thymeleaf at `/research/runs` | 460 |

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
com.wgblackmon.aihealthcare.infrastructure.ingestion.web
com.wgblackmon.aihealthcare.infrastructure.persistence
com.wgblackmon.aihealthcare.infrastructure.research
com.wgblackmon.aihealthcare.infrastructure.scheduler
com.wgblackmon.aihealthcare.web.controller
com.wgblackmon.aihealthcare.web.dto
```

---

## AI Integration Pattern

Spring AI `ChatClient` is wrapped by three AI adapters, all in `infrastructure.ai`:

| Adapter | Port implemented | Purpose |
|---------|-----------------|---------|
| `AiSummarizationAdapter` | `AiSummarizationPort` | Newsletter section summarization (standard + RAG) |
| `AiEvaluationAdapter` | `AiEvaluationPort` | LLM-as-judge prompt evaluation scoring |
| `AiReportAdapter` | `AiReportPort` | Monthly market intelligence report generation |

Unit tests inject mock ports — no real AI calls outside `@Profile("ai-integration")`.

Prompt templates (`application/src/main/resources/prompts/`):
- `summarize-articles.txt` — standard newsletter summarization
- `summarize-articles-rag.txt` — RAG-augmented summarization
- `evaluate-section.txt` — LLM-as-judge scoring (5 dimensions)
- `generate-introduction.txt` — newsletter intro generation
- `research-plan.txt` — research query decomposition
- `research-synthesis.txt` — research answer synthesis

---

## Article Ingestion Pattern

Multiple harvesters feed into `ArticleStoragePort` → `ArticleStorageAdapter` → `news_articles` table.

```
FeedHarvestScheduler     → RomeFeedHarvester     → RSS feeds (ACADEMIC/REGULATORY/INDUSTRY)
WebMonitoringScheduler   → WebPageHarvester       → Competitor web pages (SHA-256 change detection)
                         → HuggingFaceHarvester   → HuggingFace model API
ResearchHarvestScheduler → PerplexityHarvester    → Perplexity API (COMBINED research mode, daily)
```

All harvested articles share the `NewsArticle` domain record (11 fields). `ArticleStorageAdapter`
deduplicates by URL before persisting.

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

---

## REST API Surface

| Method | Path | Controller |
|--------|------|-----------|
| GET | `/api/v1/articles` | `ArticleController` |
| GET/GET | `/api/v1/runs`, `/api/v1/runs/{runId}` | `NewsletterRunController` |
| POST | `/api/v1/newsletter/deliver` | `NewsletterDeliveryController` |
| POST/GET/DELETE | `/api/v1/subscribers` | `SubscriberController` |
| POST/GET/DELETE | `/api/v1/variants`, `/api/v1/variants/{id}` | `PromptVariantController` |
| POST/GET | `/api/v1/evaluations`, `/api/v1/evaluations/{id}` | `PromptEvaluationController` |
| POST | `/api/v1/comparisons` | `PromptEvaluationController` |
| POST/POST/POST/GET | `/monitoring/harvest`, `/monitoring/competitor`, `/monitoring/huggingface`, `/monitoring/hashes` | `WebMonitoringController` |
| GET/GET/PUT | `/api/v1/search-prompts`, `/api/v1/search-prompts/{engine}` | `SearchPromptController` |
| POST | `/api/v1/documents/ingest` | `DocumentIngestionController` |
| POST | `/api/v1/market-intelligence/refresh` | `MarketIntelligenceController` |
| GET | `/api/v1/analytics/ingestion`, `/api/v1/analytics/runs`, `/api/v1/analytics/evaluations` | `AnalyticsController` |
| POST | `/api/v1/research` | `ResearchController` |
| GET/GET | `/api/v1/research/runs`, `/api/v1/research/runs/{runId}` | `ResearchRunController` |

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

---

## Scheduler Summary

| Scheduler | Trigger | Action |
|-----------|---------|--------|
| `FeedHarvestScheduler` | Daily (ACADEMIC/REGULATORY) + every 4h (INDUSTRY) | RSS harvest → DB |
| `WebMonitoringScheduler` | Daily 07:00 UTC (competitors) + 07:30 UTC (HuggingFace) | Web scrape + HF discovery → DB |
| `EmbeddingScheduler` | Daily at midnight | Embed all articles into vector store |
| `NewsletterGenerationScheduler` | Configurable cron (default: Mondays 8 AM) | Ingest → generate → deliver |
| `MarketIntelligenceScheduler` | 1st of month, 8 AM | AI-generated market intelligence report |
| `ResearchHarvestScheduler` | Daily 06:00 UTC (configurable topics) | COMBINED pipeline → ResearchRun records |

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

**460 tests** across 58 test classes — all pass with `mvn test` (no live AI or network calls).
