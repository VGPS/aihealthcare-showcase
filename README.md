# AI Healthcare Intelligence Platform

A production AI-powered market intelligence platform for the healthcare industry. Harvests, analyzes, and synthesizes data from 69 sources using 6 LLM providers, delivering actionable intelligence through 30+ dashboard pages.

**Live at [app.bigskylabs.ai](https://app.bigskylabs.ai)**

---

## Platform Metrics

| Metric | Count |
|--------|-------|
| Java classes | 592 |
| Automated tests | 1,837 |
| Test classes | 251 |
| Domain models | 103 |
| Port interfaces | 92 |
| Controllers | 72 |
| Infrastructure adapters | 67 |
| Thymeleaf UI pages | 56 |
| Data sources | 69 |
| LLM adapters | 20 |
| Scheduled pipelines | 15 |
| Prompt templates | 18 |
| REST API endpoints | 45+ |

---

## Architecture

Built using **hexagonal architecture** (ports and adapters) with strict dependency rules enforced by package boundaries. The domain layer is pure Java with zero framework imports.

### Dependency Flow

```
┌─────────────────────────────────────────────────────────────┐
│                        web (Controllers)                     │
│              Spring MVC + Thymeleaf + REST API               │
└──────────────────────────┬──────────────────────────────────┘
                           │ calls
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   application (Use Cases)                     │
│            Orchestration services, domain logic               │
└──────────────────────────┬──────────────────────────────────┘
                           │ depends on
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     domain (Pure Java)                        │
│        Records, Port Interfaces, Business Rules              │
│          Zero Spring / Zero Framework imports                │
└──────────────────────────▲──────────────────────────────────┘
                           │ implements
┌─────────────────────────────────────────────────────────────┐
│                 infrastructure (Adapters)                     │
│     AI · Persistence · Ingestion · Delivery · Scheduling     │
└─────────────────────────────────────────────────────────────┘
```

### Module Breakdown

```
AIHealthcare/
├── domain/                          # Pure Java — NO framework dependencies
│   ├── model/         (103 files)   # Immutable records: articles, companies,
│   │                                #   trends, deals, sentiment, regulatory,
│   │                                #   wiki pages, research, frameworks
│   ├── port/
│   │   ├── inbound/    (26 files)   # Use-case interfaces (what the app CAN do)
│   │   └── outbound/   (66 files)   # Adapter interfaces (what the app NEEDS)
│   ├── service/        (48 files)   # Domain logic: scoring, matching, rendering,
│   │                                #   classification, enrichment, dedup
│   └── exception/       (9 files)   # Domain-specific exceptions
│
├── infrastructure/                  # Framework-dependent implementations
│   ├── ai/             (20 files)   # LLM adapters (Claude, GPT, Gemini,
│   │                                #   Perplexity, Bedrock, pgvector)
│   ├── persistence/    (40+ files)  # JPA entities, repositories, adapters
│   ├── ingestion/      (25+ files)  # RSS, web scraping, HuggingFace,
│   │                                #   regulatory APIs, clinical trials
│   ├── delivery/       (10+ files)  # Email (SES), NotebookLM export
│   ├── scheduler/      (15 files)   # Cron pipelines, orchestrator
│   ├── research/        (5 files)   # Perplexity staged research pipeline
│   └── config/         (15+ files)  # Spring beans, security, rate limiting
│
├── web/                             # HTTP layer
│   ├── controller/     (72 files)   # REST + Thymeleaf controllers
│   └── dto/            (30+ files)  # Request/response records
│
└── api/                             # OpenAPI spec (contract-first)
    └── openapi.yaml                 # Generated DTOs, never hand-edited
```

---

## Data Pipeline Architecture

An 11-step automated pipeline runs daily, harvesting from 69 sources with fault isolation per step:

```
  ┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
  │  RSS Feeds   │     │  Competitor   │     │   HuggingFace    │
  │  (57 feeds)  │     │  Web Pages    │     │   Model API      │
  └──────┬───────┘     └──────┬───────┘     └────────┬─────────┘
         │                    │                      │
         ▼                    ▼                      ▼
  ┌─────────────────────────────────────────────────────────────┐
  │              Article Storage (PostgreSQL + pgvector)         │
  │                    Dedup by URL · 69 sources                │
  └──────────────────────────┬──────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌─────────────┐  ┌──────────────┐
     │ Regulatory  │  │  Clinical   │  │   Research    │
     │  Harvest    │  │   Trials    │  │   Pipeline    │
     │ FDA · CMS   │  │ ClinTrials  │  │  Perplexity   │
     └──────┬─────┘  └──────┬─────┘  └──────┬────────┘
            │               │               │
            ▼               ▼               ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                  LLM Processing Layer                       │
  │                                                             │
  │  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────┐  │
  │  │ Wiki     │ │ Sentiment │ │ Framework│ │    Deal      │  │
  │  │ Compile  │ │ Analysis  │ │ Scoring  │ │ Classification│  │
  │  └──────────┘ └───────────┘ └──────────┘ └──────────────┘  │
  │  ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────────┐  │
  │  │ Trend    │ │ Topic     │ │ Company  │ │  Embedding   │  │
  │  │ Detect   │ │ Summaries │ │ Discovery│ │  (pgvector)  │  │
  │  └──────────┘ └───────────┘ └──────────┘ └──────────────┘  │
  └──────────────────────────┬──────────────────────────────────┘
                             │
                             ▼
  ┌─────────────────────────────────────────────────────────────┐
  │                    Delivery Layer                            │
  │  Newsletter (SES) · Dashboard (Thymeleaf) · REST API       │
  │  NotebookLM Export · Data Export (CSV/JSON)                │
  └─────────────────────────────────────────────────────────────┘
```

---

## LLM Integration

6 providers behind a unified port interface — swap any provider with zero domain changes:

| Provider | Models | Usage |
|----------|--------|-------|
| **Anthropic Claude** | Claude Sonnet / Opus | Primary synthesis, wiki compilation, sentiment, deal classification |
| **OpenAI** | GPT-4o | Multi-model search synthesis, prompt evaluation |
| **Google Gemini** | Gemini Flash | Search synthesis (cost-optimized alternative) |
| **Perplexity** | Sonar Pro | Live web research, staged research pipeline |
| **AWS Bedrock** | Amazon Nova Lite | Enterprise-grade synthesis via Bedrock API |
| **pgvector** | Embeddings | Semantic similarity search across article corpus |

All LLM calls are routed through outbound port interfaces (`AiSearchPort`, `AiSummarizationPort`, `SentimentAnalysisPort`, etc.). Unit tests mock these ports — no real AI calls outside dedicated integration profiles.

---

## Intelligence Features

### Data Harvesting
- **69 RSS/API/web sources** across academic, regulatory, and industry tiers
- **FDA 510(k) and De Novo** clearance monitoring via openFDA API
- **CMS rulemaking** tracking via Federal Register API
- **ClinicalTrials.gov** AI-related trial harvesting
- **HuggingFace** healthcare model discovery
- **Competitor web page** change detection (SHA-256 hashing)

### AI-Powered Analysis
- **Multi-model search** — fan-out queries to up to 5 LLMs, synthesize with numbered citations
- **Sentiment & risk scoring** — per-company LLM classification (POSITIVE/NEGATIVE/MIXED/NEUTRAL)
- **Framework competitive analysis** — 6-dimension scoring with radar charts
- **Deal signal detection** — M&A, funding, partnership identification with LLM confirmation
- **Trend detection** — keyword frequency analysis across 30/90/180-day windows
- **Wiki knowledge base** — LLM-compiled longitudinal context with contradiction detection

### Content Delivery
- **Daily newsletter** — auto-generated drafts with WYSIWYG editor, sent via AWS SES
- **30+ dashboard pages** — Thymeleaf with Chart.js visualizations
- **REST API** — 45+ endpoints with API key auth and rate limiting
- **Data export** — CSV/JSON export for enterprise integration

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 17 (LTS) |
| **Framework** | Spring Boot 3.x + Spring AI 1.0.0 |
| **Architecture** | Hexagonal (Ports & Adapters) |
| **Database** | PostgreSQL + pgvector (RDS) |
| **ORM** | Spring Data JPA / Hibernate |
| **Search** | Spring AI VectorStore (pgvector) |
| **Email** | AWS SES (DKIM-verified domain) |
| **Payments** | Stripe (subscriptions + webhooks) |
| **Frontend** | Thymeleaf + Tailwind CSS + Chart.js + Alpine.js + htmx |
| **API Spec** | OpenAPI 3.0 (contract-first, code generated) |
| **Testing** | JUnit 5 + AssertJ + Mockito (1,837 tests, 0 failures) |
| **Deployment** | AWS EC2 + RDS + SES, systemd service |
| **CI/CD** | Maven + GitHub |

---

## Domain Model (Included in This Repo)

This repository contains the complete **domain layer** — 252 pure Java files with zero framework dependencies. This is the architectural core: every record, port interface, domain service, and business rule.

```
domain/
├── model/          # 103 immutable records
│   ├── NewsArticle, Topic, NewsletterSection, NewsletterDraft
│   ├── Company, CompanyDiscoveryResult, CompanyTags
│   ├── TrendSignal, TrendSnapshot, TrendDirection
│   ├── DealSignal, DealContext, DealClassificationPort
│   ├── RegulatoryEvent, RegulatoryEventType, RegulatoryBody
│   ├── ArticleSentiment, CompanySentiment, SentimentLabel
│   ├── FrameworkAnalysis, FrameworkDimension, FrameworkCompany
│   ├── WikiPage, WikiPageType, SourceRef, Contradiction
│   ├── ResearchRequest, ResearchAnswer, ResearchPlan
│   ├── ClinicalTrial, ClinicalTrialPhase, TrialStatus
│   ├── AiSearchResult, AiSearchSynthesis
│   └── ... (103 total)
│
├── port/
│   ├── inbound/    # 26 use-case interfaces
│   │   ├── GenerateNewsletterUseCase
│   │   ├── ConductAiSearchUseCase
│   │   ├── ConductResearchUseCase
│   │   ├── AnalyzeCompanySentimentUseCase
│   │   ├── AnalyzeFrameworksUseCase
│   │   ├── DetectDealSignalsUseCase
│   │   ├── DetectTrendsUseCase
│   │   ├── MonitorRegulatoryEventsUseCase
│   │   └── ... (26 total)
│   │
│   └── outbound/   # 66 adapter interfaces
│       ├── AiSummarizationPort, AiSearchPort
│       ├── ArticleIngestionPort, ArticleStoragePort
│       ├── SentimentAnalysisPort, CompanySentimentPort
│       ├── FrameworkAnalysisPort, FrameworkLlmPort
│       ├── DealClassificationPort, DealSignalPort
│       ├── RegulatoryEventPort, RegulatoryHarvestingPort
│       ├── KnowledgeCompilationPort, WikiQueryPort
│       └── ... (66 total)
│
├── service/        # 48 domain services (pure business logic)
│   ├── NewsletterService, NewsletterRenderer
│   ├── CompanySentimentService, CompanyClassifier
│   ├── FrameworkAnalysisService
│   ├── DealSignalDetectionService, DealEnrichmentService
│   ├── TrendDetectionService, TrendOrchestrationService
│   ├── ResearchOrchestratorService, CitationAssembler
│   ├── WatchlistMatchingService
│   └── ... (48 total)
│
└── exception/      # 9 domain exceptions
    ├── PromptVariantNotFoundException
    ├── EvaluationNotFoundException
    └── ... (9 total)
```

The domain layer compiles with `javac` alone — no Maven, no Spring, no Lombok. Every class uses standard Java records for immutable data and traditional `for` loops (no streams). This makes the architecture portable to any framework.

---

## Access Model

Four-tier subscription model with feature gating:

| Tier | Access |
|------|--------|
| **DEMO** | 48-hour full access trial, auto-expires to FREE |
| **FREE** | Limited articles (7-day window), 5 companies, 4 trend snapshots |
| **SUBSCRIBER** | Full access — all articles, all features, daily briefings |
| **ADMIN** | Subscriber + pipeline management + user administration |

Payments via Stripe with webhook-driven tier upgrades and subscription management.

---

## Why This Architecture

**Hexagonal architecture** was chosen because this platform integrates with volatile external dependencies — LLM APIs change pricing and capabilities monthly, RSS feeds go down, regulatory APIs change formats. The port/adapter pattern means:

- Swapping Claude for GPT requires one new adapter class, zero domain changes
- Adding a 7th LLM provider is a single `implements AiSearchPort` class
- The entire AI layer can be mocked for 1,837 tests that run in under 2 minutes with no API calls
- Data sources can be added or removed via configuration, not code

---

## Author

**Bill Blackmon** — Senior Java/Spring Engineer

- Platform: [app.bigskylabs.ai](https://app.bigskylabs.ai)
- About: [app.bigskylabs.ai/about](https://app.bigskylabs.ai/about)
- LinkedIn: [linkedin.com/in/wgblackmon](https://www.linkedin.com/in/wgblackmon/)
- GitHub: [github.com/VGPS](https://github.com/VGPS)
