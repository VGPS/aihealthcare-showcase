# AI Healthcare Intelligence Platform

A production AI-powered market intelligence platform for the healthcare industry. Harvests, analyzes, and synthesizes data from 69 sources using 6 LLM providers, delivering actionable intelligence through 30+ dashboard pages — backed by a companion Claude Intelligence Service providing cross-domain synthesis and multi-LLM verification.

**Live at [app.bigskylabs.ai](https://app.bigskylabs.ai)** | **[JavaDoc (1,337 pages)](https://vgps.github.io/aihealthcare-showcase/)**

---

## Platform Metrics

| Metric | Count |
|--------|-------|
| Java classes | 592 |
| Automated tests | 2,037+ |
| Test classes | 251 |
| Domain models | 103 |
| Port interfaces | 92 |
| Controllers | 72 |
| Infrastructure adapters | 67 |
| Thymeleaf UI pages | 56 |
| Data sources | 69 |
| LLM adapters | 20 |
| Scheduled pipelines | 18 |
| Prompt templates | 26 |
| REST API endpoints | 65+ |

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

### Two-Service Architecture

```
AIHealthcare (port 8080)                AIHealthcare-Claude (port 8081)
├── 69 RSS/API/web sources              ├── Claude RAG chat
├── 17 automated pipelines              ├── Cross-domain intelligence reports
├── 30+ Thymeleaf dashboards            ├── Multi-LLM verification (AI Audit)
├── Daily newsletter (SES)              ├── AI Platform Race tracker
├── Stripe billing                      ├── Wiki intelligence
├── Intelligence Console proxy ────────── 21 REST endpoints
└── Writes to PostgreSQL ──────────────── Reads from PostgreSQL (read-only)
                                        │
                                        ├── LLM Providers:
                                        │   ├── Anthropic Claude (synthesis)
                                        │   ├── OpenAI GPT-4o (verification)
                                        │   ├── Google Gemini 2.5 Flash (verification)
                                        │   └── AWS Bedrock Nova Pro (verification)
```

Two independent JARs sharing a PostgreSQL database. No service mesh, no API gateway — the main app's Intelligence Console proxies requests to the Claude service via RestClient.

### Module Breakdown

```
AIHealthcare/
├── domain/                          # Pure Java — NO framework dependencies
│   ├── model/         (103 files)   # Immutable records: articles, companies,
│   │                                #   trends, deals, sentiment, regulatory,
│   │                                #   wiki pages, research, frameworks
│   ├── port/
│   │   ├── inbound/    (26 files)   # Use-case interfaces (what the app CAN do)
│   │   └── outbound/   (67 files)   # Adapter interfaces (what the app NEEDS)
│   ├── service/        (48 files)   # Domain logic: scoring, matching, rendering,
│   │                                #   classification, enrichment, dedup
│   └── exception/       (9 files)   # Domain-specific exceptions
│
├── infrastructure/                  # Framework-dependent implementations
│   ├── ai/             (21 files)   # LLM adapters (Claude, GPT, Gemini,
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

6 healthcare AI platform competitors tracked behind a unified port interface — swap any provider with zero domain changes:

| Provider | Models | Usage |
|----------|--------|-------|
| **Claude for Healthcare** | Claude Sonnet / Opus | Primary synthesis, wiki compilation, sentiment, deal classification, cross-domain intelligence |
| **OpenAI for Healthcare** | GPT-4o | Multi-model search synthesis, prompt evaluation, independent verification |
| **Google for Health** | Gemini 2.5 Flash | Search synthesis, independent verification |
| **Microsoft for Healthcare** | Copilot | Competitive tracking, article coverage |
| **Perplexity Health** | Sonar Pro | Live web research, staged research pipeline |
| **Amazon Health** | Nova Pro | Enterprise-grade synthesis, independent verification |
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
- **Multi-model search** — fan-out queries to up to 5 LLMs, synthesize with numbered citations that link directly to source articles
- **Sentiment & risk scoring** — per-company LLM classification (POSITIVE/NEGATIVE/MIXED/NEUTRAL)
- **Framework competitive analysis** — 6-dimension scoring with radar charts
- **Deal signal detection** — M&A, funding, partnership identification with LLM confirmation
- **Trend detection** — keyword frequency analysis across 30/90/180-day windows
- **Wiki knowledge base** — LLM-compiled longitudinal context with contradiction detection

### Content Delivery
- **Daily newsletter** — AI-summarized for ENTERPRISE/SUBSCRIBER/DEMO tiers; article digest for FREE tier featuring an **Article of the Day** block (top-scored article by LLM significance 1–10, formatted body with entity bolding) and 75-article feed capped by source weight; Reversal Watch section surfaces wiki contradictions as formatted bullet list; sent via AWS SES
- **30+ dashboard pages** — Thymeleaf with Chart.js visualizations
- **REST API** — 65+ endpoints with API key auth and rate limiting
- **Data export** — PDF/DOC/TXT/JSON/CSV export across all intelligence tabs
- **Feed tier hierarchy** — REGULATORY → LEGAL → RESEARCH → ACADEMIC → INDUSTRY → COMPETITOR; source weight (0.5–0.95) determines digest inclusion order

---

## Claude Intelligence Service

A companion Spring Boot service providing Claude-powered clinical intelligence, cross-domain analysis, and multi-LLM verification. Runs alongside the main app and shares the same PostgreSQL database (read-only access to data engine tables).

### Intelligence Console
- **"Enterprise Intelligence"** — 11-tab console (7 subscriber tabs + 4 enterprise-gated tabs) for interactive healthcare intelligence queries; server-side tier gating via Thymeleaf (non-bypassable)
- Rich HTML rendering with **entity-aware bold formatting** — companies, studies, legislation, and dollar amounts highlighted automatically by Claude
- **Live citation links** — `[N]` references in AI output scroll to and highlight the corresponding source article
- **Clickable scheduled reports** — daily reports expand inline with full rendered intelligence narratives
- PDF/DOC/TXT/JSON export on every output tab
- Shared footer with quick links, contact, and branding across both apps
- Live health check indicator, configurable API key authentication and rate limiting

### Cross-Domain Intelligence Reports
- **Fuses 7 data domains** — regulatory events, deal signals, company sentiment, framework analyses, trend snapshots, articles, and wiki knowledge — into unified narratives
- Cross-domain connections (e.g., an FDA clearance reshaping M&A pipelines)
- Leading indicators and strategic implications
- Configurable depth (quick/standard/deep) and lookback window

### Multi-LLM Cross-Verification (AI Audit)
- Claude generates the synthesis; a second LLM independently verifies every claim
- **3 verifier options:** OpenAI GPT-4o, Google Gemini 2.5 Flash, AWS Bedrock Nova Pro
- Structured output: confirmed claims, disputed claims (with counter-reasoning), and coverage gaps
- Confidence scoring with persistent audit trail in PostgreSQL
- **Source Provenance Dashboard** — traces each confirmed claim to specific source documents with domain-prefixed ref IDs (Article, Regulatory, Deal, Sentiment, Framework, Trend, Wiki)
- **Confidence Trend Heatmap** — append-only snapshots track confidence over time, CSS Grid heatmap shows improving/declining/stable trends
- Popular query tracking — frequently-audited topics load instantly from cache

### Intelligence Monitoring
- **Scheduled daily reports** — automated synthesis for configurable topics (6 AM cron, 30-day retention)
- **Intelligence trending** — track how analysis evolves over time with delta metrics and trend arrows
- **Synthesis Diff** — Claude-powered temporal diff comparing two intelligence reports, surfacing new/dropped/shifted findings and overall market direction
- **Historical daily archive** — paginated browsing of pre-generated daily summary reports
- **AI Platform Race Tracker** — vendor-neutral competitive landscape analysis across Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, and Amazon Health

### Operational Hardening
- Spring Boot Actuator health/metrics endpoints
- Request logging with elapsed time tracking
- Sliding-window rate limiting per client (IP or API key)
- Server timeout hardening for deep synthesis queries (5-minute LLM calls)
- Flyway-managed database migrations for audit and snapshot tables
- 191 automated tests (JUnit 5 + AssertJ + Mockito), 7 prompt templates

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 17 (LTS) |
| **Framework** | Spring Boot 3.x + Spring AI 1.1.6 |
| **Architecture** | Hexagonal (Ports & Adapters) |
| **Database** | PostgreSQL + pgvector (RDS) |
| **ORM** | Spring Data JPA / Hibernate |
| **Search** | Spring AI VectorStore (pgvector) |
| **Email** | AWS SES (DKIM-verified domain) |
| **Payments** | Stripe (subscriptions + webhooks) |
| **Frontend** | Thymeleaf + Tailwind CSS + Chart.js + Alpine.js + htmx |
| **API Spec** | OpenAPI 3.0 (contract-first, code generated) |
| **Testing** | JUnit 5 + AssertJ + Mockito (2,037+ tests, 0 failures) |
| **Deployment** | AWS EC2 + RDS + SES, systemd service |
| **CI/CD** | Maven + GitHub |

---

## Domain Model (Included in This Repo)

This repository contains the complete **domain layer** — 257 pure Java files with zero framework dependencies. This is the architectural core: every record, port interface, domain service, and business rule.

```
domain/
├── model/          # 105 immutable records
│   ├── NewsArticle, Topic, NewsletterSection, NewsletterDraft
│   ├── Company, CompanyDiscoveryResult, CompanyTags
│   ├── TrendSignal, TrendSnapshot, TrendDirection
│   ├── DealSignal, DealContext, DealClassificationPort
│   ├── RegulatoryEvent, RegulatoryEventType, RegulatoryBody
│   ├── ArticleSentiment, CompanySentiment, SentimentLabel
│   ├── FrameworkAnalysis, FrameworkDimension, FrameworkCompany
│   ├── WikiPage, WikiPageType, SourceRef, Contradiction
│   ├── GapItem, WikiGapAnalysisResult
│   ├── ResearchRequest, ResearchAnswer, ResearchPlan
│   ├── ClinicalTrial, ClinicalTrialPhase, TrialStatus
│   ├── AiSearchResult, AiSearchSynthesis
│   └── ... (105 total)
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
│   └── outbound/   # 68 adapter interfaces
│       ├── AiSummarizationPort, AiSearchPort
│       ├── ArticleIngestionPort, ArticleStoragePort
│       ├── SentimentAnalysisPort, CompanySentimentPort
│       ├── FrameworkAnalysisPort, FrameworkLlmPort
│       ├── DealClassificationPort, DealSignalPort
│       ├── RegulatoryEventPort, RegulatoryHarvestingPort
│       ├── KnowledgeCompilationPort, WikiQueryPort
│       ├── WikiGapAnalysisPort
│       └── ... (67 total)
│
├── service/        # 49 domain services (pure business logic)
│   ├── NewsletterService, NewsletterRenderer
│   ├── CompanySentimentService, CompanyClassifier
│   ├── FrameworkAnalysisService
│   ├── DealSignalDetectionService, DealEnrichmentService
│   ├── TrendDetectionService, TrendOrchestrationService
│   ├── ResearchOrchestratorService, CitationAssembler
│   ├── WatchlistMatchingService
│   ├── WikiGapAnalysisService
│   └── ... (49 total)
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
| **SUBSCRIBER** | Full access — all articles, all features, daily briefings; Intelligence Console shows 7 tabs |
| **ENTERPRISE** | Subscriber + 4 additional Intelligence Console tabs (AI Audit, What Changed?, Intelligence Trending, Confidence Trends) |
| **ADMIN** | Enterprise + pipeline management + user administration, all quota/tier checks bypassed |

Payments via Stripe with webhook-driven tier upgrades and subscription management.

---

## Why This Architecture

**Hexagonal architecture** was chosen because this platform integrates with volatile external dependencies — LLM APIs change pricing and capabilities monthly, RSS feeds go down, regulatory APIs change formats. The port/adapter pattern means:

- Swapping Claude for GPT requires one new adapter class, zero domain changes
- Adding a 7th LLM provider is a single `implements AiSearchPort` class
- The entire AI layer can be mocked for 2,037+ tests that run in under 2 minutes with no API calls
- Data sources can be added or removed via configuration, not code

---

## Author

**Bill Blackmon** — Senior Java/Spring Engineer

- Platform: [app.bigskylabs.ai](https://app.bigskylabs.ai)
- About: [app.bigskylabs.ai/about](https://app.bigskylabs.ai/about)
- JavaDoc: [vgps.github.io/aihealthcare-showcase](https://vgps.github.io/aihealthcare-showcase/) (1,337 pages)
- LinkedIn: [linkedin.com/in/wgblackmon](https://www.linkedin.com/in/wgblackmon/)
- GitHub: [github.com/VGPS](https://github.com/VGPS)
