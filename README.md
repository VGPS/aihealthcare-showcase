# AIHealthcare

An automated AI-powered newsletter, research, and competitive intelligence platform that discovers, summarizes, and delivers the latest artificial intelligence in healthcare news — built with Spring Boot and Spring AI.

## Features

| Feature | Description |
|---------|-------------|
| **Multi-Source Article Harvesting** | Automated ingestion from 57+ RSS feeds, web scrapers, and APIs across academic, regulatory, and industry tiers |
| **AI Newsletter Generation** | Daily automated newsletter drafts with topic-grouped sections, attributed sources, and TinyMCE WYSIWYG editing |
| **Multi-Model AI Search** | Fan-out synthesis across Claude, GPT, Perplexity Sonar, and Gemini with numbered citation references |
| **Staged Research Pipeline** | AI-planned query decomposition, multi-source retrieval, citation assembly, and synthesized research answers |
| **LLM-Compiled Knowledge Wiki** | AI synthesizes harvested articles into a persistent, searchable wiki with revision history and cross-references |
| **Source Provenance Tracking** | Every wiki claim links to its original PubMed, FDA, or industry source for verifiable trust |
| **Contradiction Detection** | Automatically flags when new evidence contradicts prior wiki claims — a "Reversal Watch" for healthcare AI |
| **Regulatory Alert System** | Automated FDA 510(k)/De Novo clearance and CMS rule harvesting from openFDA and Federal Register APIs |
| **Sentiment & Risk Scoring** | LLM-powered per-article sentiment classification aggregated into company-level risk scores with confidence metrics |
| **Framework Competitive Analysis** | Config-driven 6-dimension competitive scoring (clinical validation, regulatory, market adoption, tech depth, data assets, partnerships) |
| **Deal Signal Detection** | LLM-enhanced deal classification extracting deal type, amount, counterparty, and confidence from article text |
| **Deal Context Enrichment** | Cross-references deals against sentiment, framework, regulatory, and company profile data for 360-degree context |
| **Public Company Directory** | No-login-required directory ranked by live signal scoring (article velocity + deal bonus − sentiment penalty) with sort tabs (Trending / Recently Funded / Watch List), sector filter pills, signal badges (🔥/💰/⚠) per card, CSV export, JSON-LD Organization schema for SEO, and a minimal public nav (Sign In / Pricing / About only — hides all member-only pages from unauthenticated visitors) |
| **Company Intelligence Profiles** | Persistent company pages with real article linking, event timelines, trend indicators, and homepage links |
| **Company Relationships** | Visual relationship mapping between companies showing partnerships, acquisitions, and competitive dynamics; force-directed graph with type-colored edges (blue=partnership, red=acquisition, green=investment, orange=competitor), amber hover highlight, sidebar-on-click detail panel |
| **Custom Watchlists** | Subscriber-defined keyword, company, and topic watchlists with automated matching against new articles and regulatory events |
| **Trend Detection** | Weekly keyword frequency analysis across 30/90/180-day windows identifying rising, fading, and new healthcare AI trends |
| **Trend History Archive** | Multi-line chart visualization of keyword trend momentum over time with clickable snapshot detail pages |
| **Legal Timeline** | Unified timeline view merging legal articles, policy events, and regulatory actions into a single chronological feed |
| **Clinical Trial Monitoring** | Tracking AI-related clinical trials with status, phase, and company linkage |
| **PubMed Historical Backfill** | E-utilities API integration retrieves years of academic articles to build longitudinal knowledge depth |
| **Prompt Evaluation Framework** | LLM-as-judge scoring across 5 quality dimensions with A/B variant comparison for prompt optimization |
| **Vendor Competitive Analysis** | AI-driven strengths/weaknesses assessment with Doc Frequency and TF-IDF relevance scoring |
| **Company Discovery Pipeline** | Scrapes YC and startup directories, classifies by healthcare AI subcategory, deduplicates, and renders reports |
| **Vector Semantic Search** | PGVector-powered similarity search across the full article archive for contextual retrieval |
| **RAG-Enhanced Summarization** | Retrieval-augmented generation enriches newsletter sections with relevant archived context |
| **Webhook Notifications** | Configurable webhook channels (Slack, Teams, custom) for pipeline events, deal signals, and regulatory alerts |
| **Analyst Notes** | Per-entity note-taking system for analysts to annotate companies, deals, and trends with private observations |
| **Data Export** | CSV and structured export of articles, companies, trends, and deal signals for external analysis |
| **Self-Maintaining Pipeline Orchestrator** | Sequences all 11 post-harvest pipelines with try-catch isolation per step — no single failure breaks the chain |
| **Subscription Tier Gating** | 4-tier access model (DEMO/FREE_PENDING/FREE/SUBSCRIBER) with Stripe Billing, usage metering, and feature-level gating |
| **Role-Based Access Control** | Spring Security with ADMIN/USER roles, session-based auth, and per-page authorization |
| **Hexagonal Architecture** | Framework-free domain layer with pluggable adapters — swap AI providers or databases with zero domain changes |
| **What Changed Digest** | Weekly activity dashboard showing new pages, updated pages, and detected contradictions with configurable time windows |
| **Evidence Grade Classification** | Automatic source credibility badges (Peer-Reviewed, Regulatory, Industry, Vendor, News) with color-coded provenance |
| **PII Masking** | LogSanitizer utility masks email addresses in log statements to prevent PII exposure in production logs |
| **Branded Error Handling** | Custom error pages replacing Spring Boot's Whitelabel Error Page with consistent branded UI |
| **51-Page Thymeleaf UI** | Dashboard, wiki, research, newsletter editor, admin panel, search, pricing, trends, regulatory, watchlist, sentiment, frameworks, deals, company pages, public company directory, and LinkedIn feature post rotation |
| **2,387+ Automated Tests** | Comprehensive test suite across 307 test classes spanning domain, web, persistence, and infrastructure layers — no live AI calls |

### Resume / LinkedIn Feature Bullets

**Platform & Architecture**
- Designed and built a full-stack AI intelligence platform using **Spring Boot 3.4.5, Java 17, Spring AI 1.0.0**, and **hexagonal architecture** (ports-and-adapters) with 103 domain model records, 92 port interfaces, and 70 controllers — framework-free domain layer enables swapping AI providers with zero business logic changes
- Wrote **2,387+ automated tests** across 307 test classes (JUnit 5, AssertJ, Mockito, MockMvc, @DataJpaTest) achieving comprehensive coverage across domain, web, persistence, and infrastructure layers with no live AI calls in CI

**Multi-Model AI Integration**
- Integrated **5 LLM providers** (Anthropic Claude, OpenAI GPT, Google Gemini, Perplexity Sonar, AWS Bedrock) via Spring AI ChatClient and RestClient adapters, with fan-out multi-model search returning synthesized answers with numbered `[N]` citation references
- Built **18 externalized prompt templates** powering newsletter summarization, sentiment analysis, framework scoring, deal classification, wiki compilation, trend extraction, and LLM-as-judge evaluation across 5 quality dimensions

**Data Pipeline & Ingestion**
- Engineered an **11-step self-maintaining pipeline orchestrator** that sequences competitor scraping, regulatory harvesting, article embedding, framework analysis, sentiment scoring, trend detection, and wiki compilation — each step isolated with try-catch to prevent cascade failures
- Automated ingestion from **57+ configurable data sources** (RSS via Rome, web scraping via Jsoup, openFDA/Federal Register APIs, HuggingFace model API, PubMed E-utilities, Perplexity Sonar) with URL-based deduplication and SHA-256 change detection
- Implemented **PGVector-powered semantic search** with Spring AI vector store for contextual article retrieval and RAG-enhanced newsletter summarization

**Intelligence & Analytics**
- Developed **LLM-powered sentiment analysis** classifying per-article sentiment (POSITIVE/NEGATIVE/MIXED/NEUTRAL) with confidence scores and rationale, aggregated into company-level risk dashboards with Chart.js doughnut and bar chart visualization
- Built **config-driven 6-dimension competitive framework analysis** (clinical validation, regulatory positioning, market adoption, technology depth, data assets, partnerships) with radar chart visualization — add companies via YAML, no code changes required
- Created **LLM-enhanced deal signal detection** pipeline with keyword pre-filter + batch LLM classification extracting deal type, amount, counterparty, and confidence, enriched with cross-referenced sentiment/framework/regulatory/company context
- Implemented **weekly trend detection** across 30/90/180-day rolling windows identifying rising, fading, and emerging healthcare AI keywords with multi-line historical trend charts

**Regulatory & Compliance**
- Built automated **FDA 510(k)/De Novo clearance and CMS rule harvesting** from openFDA and Federal Register APIs with deduplication, watchlist matching, and tier-gated display
- Developed subscriber-defined **custom watchlists** (keyword, company, topic) with automated matching against incoming articles and regulatory events

**Knowledge Management**
- Architected an **LLM-compiled knowledge wiki** that synthesizes harvested articles into persistent, versioned pages with source provenance tracking, cross-references, contradiction detection ("Reversal Watch"), and orphan/stale-ref linting
- Built a **company intelligence platform** with persistent profiles, real article linking, event timelines, relationship mapping (partnerships, acquisitions, competitive dynamics), and a signal-scored public directory (article velocity + deal bonus + sentiment penalty) with sort tabs (Trending / Recently Funded / Watch List), canonical 7-category classifier, and CSV export

**Newsletter & Content Delivery**
- Developed **automated daily newsletter generation** with AI-summarized topic sections, TinyMCE 7.9.0 WYSIWYG editing, and tier-routed delivery (SUBSCRIBER=full newsletter, FREE=digest summary) via Spring Boot Mail + Amazon SES
- Built a **staged research pipeline** with AI-planned query decomposition, multi-source retrieval (Perplexity API + PostgreSQL), citation assembly, and synthesized research answers persisted for audit trail

**SaaS & Monetization**
- Implemented **4-tier subscription model** (DEMO/FREE_PENDING/FREE/SUBSCRIBER) with **Stripe Billing** integration (SDK 28.2.0), Checkout sessions, customer portal, webhook signature verification, and per-feature usage metering
- Built **Spring Security 6** session-based authentication with ADMIN/USER roles, BCrypt password hashing, tier-based feature gating, and API key authentication for REST endpoints
- Delivered **50-page Thymeleaf + Tailwind CSS UI** with Chart.js visualizations, responsive dashboard, Swagger UI API documentation, and branded error pages

**Performance Engineering & Load Testing**
- Eliminated a critical **N+1 query problem** on the wiki index page (1,299 DB queries/request → 1), confirmed by k6 load testing that revealed 2% pass rate for `/wiki` under 50 concurrent VUs
- Diagnosed and fixed **Hikari connection pool exhaustion** caused by Spring Boot's Open Session In View (OSIV) holding DB connections for the full HTTP request lifecycle including Thymeleaf template rendering — disabling OSIV (`spring.jpa.open-in-view: false`) released connections at service boundary; pool sized 25→50 to match VU count
- Introduced **Spring Data JPA interface projections** (`WikiPageIndexView`) excluding `content_markdown` from the wiki index query, eliminating ~833 KB/request of data transfer (avg 1,284 bytes/page × 649 pages) that was being discarded immediately — at 50 VUs this removed ~40 MB/s of unnecessary RDS-to-app data transfer
- Added **server-side pagination** to the wiki index (60 pages/page with `Pageable`) after profiling showed Thymeleaf CPU saturation rendering 649 card divs simultaneously across 50 concurrent users
- Added **JPA index annotations** on high-traffic FK columns: `news_articles(topic, published_at, url)`, `wiki_source_refs(page_slug)`, `wiki_contradictions(page_slug)`, `wiki_pages(page_type, updated_at)` — eliminates full table scans on the 9,120-row articles table and 649-page wiki
- Verified all optimizations with **k6 smoke tests** (50 VUs, 5-min staged ramp) achieving **0.00% error rate** and **100% pass rate across all 8 tested endpoints** (dashboard, news, search, trends, deals, regulatory, wiki, REST API) — up from wiki's 0% and overall 9.38% error rate before fixes

**Security & Operations**
- Applied **PII masking** via domain-pure LogSanitizer utility across 40+ log statements, preventing email exposure in production logs
- Deployed to **AWS EC2** with Amazon SES email delivery, externalized cron scheduling (11 configurable jobs), and profile-based configuration (dev/aws/prod)

## What It Does

AIHealthcare runs multiple automated pipelines that collectively build a comprehensive intelligence platform for the AI-in-healthcare industry:

1. **Harvest** — Scrapes articles from 57+ RSS feeds (PubMed, Beckers, Healthcare Dive, Google News), competitor web pages (Anthropic, OpenAI, Amazon, Perplexity, Google), and the HuggingFace model API
2. **Store** — Persists articles in PostgreSQL with URL-based deduplication and indexes them as vector embeddings (PGVector) for semantic search
3. **Summarize** — Uses Spring AI (Claude or OpenAI) to generate concise, topic-grouped newsletter sections with attributed sources and per-topic 3-sentence AI summaries
4. **Compile Wiki** — LLM synthesizes harvested articles into a persistent knowledge wiki with provenance, contradiction detection, revision tracking, and orphan/stale-ref linting
5. **Backfill** — PubMed E-utilities API retrieves historical academic articles (2022-2025) across 10 configurable query topics for longitudinal depth
6. **Research** — Staged research pipeline combining Perplexity API + DB articles, with AI-planned query decomposition, multi-source retrieval, citation assembly, and synthesis
7. **Analyze Sentiment** — LLM classifies per-article sentiment (POSITIVE/NEGATIVE/MIXED/NEUTRAL) with confidence scores and rationale, aggregated into company-level risk dashboards
8. **Score Frameworks** — Config-driven 6-dimension competitive analysis (clinical validation, regulatory positioning, market adoption, technology depth, data assets, partnerships) with radar chart visualization
9. **Detect Deals** — Keyword pre-filter + LLM refinement identifies M&A, partnerships, funding rounds, and licensing deals with extracted amounts, counterparties, and confidence levels
10. **Enrich Deals** — Cross-references detected deals against sentiment scores, framework analyses, regulatory events, and company profiles for 360-degree deal context
11. **Evaluate Prompts** — LLM-as-judge scoring across 5 quality dimensions with A/B variant comparison for prompt optimization
12. **Deliver** — Generates a daily newsletter draft for review; SUBSCRIBER tier gets full newsletter, FREE tier gets a digest summary; send manually after editing in the TinyMCE WYSIWYG editor
13. **Export** — NotebookLM-compatible article exports with numbered HTML summaries (citation [N] anchors match visible article numbers); CSV export for articles, companies, trends, and deals; LinkedIn research summary post generator at `/dashboard/linkedin/research-summary`; config-driven LinkedIn feature post rotation (6-week weekday cycle, no AI call) at `/dashboard/linkedin/features`
14. **Regulate** — Harvests FDA 510(k) clearances, De Novo authorizations, and CMS rules from openFDA and Federal Register APIs; matches against subscriber watchlists
15. **Discover** — Scrapes startup directories (YC, TopStartups), classifies companies by healthcare AI subcategory, builds persistent intelligence profiles with real article linking
16. **Watch** — Subscriber-defined keyword, company, and topic watchlists with automated matching against incoming articles and regulatory events
17. **Trend** — Weekly keyword frequency analysis across rolling 30/90/180-day windows identifying rising, fading, and emerging healthcare AI trends with historical archive
18. **Gate** — Usage metering and feature gating per 4-tier subscription model (DEMO/FREE_PENDING/FREE/SUBSCRIBER): archive depth, AI query limits, with Stripe Billing integration
19. **Orchestrate** — Self-maintaining pipeline orchestrator sequences all 11 post-harvest steps with per-step try-catch isolation, ensuring no single failure breaks the pipeline chain

## Architecture

The project follows **hexagonal architecture** (ports and adapters), keeping the domain layer framework-free and all infrastructure concerns pluggable:

```
web (70 controllers + 48 Thymeleaf pages)  -->  application (use cases)  -->  domain (103 models + 92 ports)
                                                                                    ^
                              infrastructure/* (adapters) --------------------------+
                              - ai/          Spring AI adapters (9 adapters: summarize, evaluate, search x4, wiki, sentiment, deals, frameworks)
                              - config/      AppConfig, SecurityConfig, bean wiring, properties
                              - ingestion/   RSS, web scraping, HuggingFace, Perplexity, PubMed backfill, regulatory
                              - persistence/ JPA entities (41), repositories, storage adapters
                              - delivery/    Email (JavaMailSender + SES) + NotebookLM export + transactional emails
                              - research/    Perplexity + legacy Google research adapters
                              - scheduler/   Pipeline orchestrator, newsletter generation
```

Swapping the AI provider, database, or delivery mechanism requires no domain changes — only a new adapter.

### Key Flows

**Article Harvest -> Pipeline Orchestrator -> Newsletter Draft**
```
FeedHarvestScheduler (04:00 UTC)
  -> RomeFeedHarvester.harvestAll()           fetch RSS from 57+ configured feeds
  -> ArticleStoragePort.save()                persist to news_articles (dedup by URL)
  -> StartupPipelineOrchestrator              sequences 11 post-harvest pipelines:
     1. Competitor page scraping              SHA-256 change detection
     2. HuggingFace model discovery           healthcare LLM tracking
     3. Regulatory event harvest              FDA/CMS from openFDA + Federal Register
     4. Clinical trial discovery              AI-related trial tracking
     5. Article embedding                     vector store refresh for semantic search
     6. Framework competitive analysis        6-dimension LLM scoring per company
     7. Company discovery                     startup directory scraping + classification
     8. Sentiment analysis                    per-article LLM classification -> company risk scores
     9. Trend detection                       keyword frequency analysis (30/90/180-day)
    10. Legal trend analysis                  policy/legal event extraction
    11. Wiki compilation                      LLM wiki page synthesis + contradiction detection

NewsletterGenerationScheduler (00:00 UTC)
  -> IngestArticlesUseCase.ingest()           load previous day's articles
  -> GenerateNewsletterUseCase.generate()     AI-summarize into sections, render HTML
  -> NewsletterRunPort.save()                 persist as DRAFT

User reviews at /newsletter/runs/{runId}/edit (TinyMCE WYSIWYG)
  -> POST .../save                            save edits
  -> POST .../send                            deliver to all active subscribers (tier-routed)
```

**Multi-Model AI Search**
```
GET /research/ai-search  (or GET /api/v1/search/ai)
  -> ArticleSearchPort.findSimilar()         vector similarity search across article archive
  -> Fan-out to selected models:
     - AnthropicAiSearchAdapter (Claude)     Spring AI ChatClient
     - OpenAiSearchAdapter (GPT)             Spring AI ChatClient
     - PerplexityAiSearchAdapter (Sonar)     RestClient to Perplexity API
     - GeminiAiSearchAdapter (Gemini)        RestClient to Gemini API
  -> Each model synthesizes articles with [N] citation references
  -> NO_MATCH gating: models return null when articles are irrelevant
  -> Results cached via Caffeine TTL
```

**Deal Signal Detection + Enrichment**
```
DealSignalDetectionService
  -> Keyword pre-filter (M&A, acquisition, partnership, funding, etc.)
  -> DealClassificationPort (LLM)           batch classification (10 articles/call)
  -> Extracts: type, amount, counterparty, confidence, analysis
  -> DealSignalPort.save()                  persist with 12-field records

DealEnrichmentService (pure domain)
  -> Cross-references deal against:
     - CompanySentimentPort                 sentiment scores + risk summary
     - FrameworkAnalysisPort                competitive analysis dimensions
     - RegulatoryEventPort                  relevant regulatory events
     - CompanyProfile data                  company context
  -> Returns DealContext with full 360-degree view
```

**Sentiment & Risk Analysis**
```
CompanySentimentService
  -> Load company profiles
  -> Fetch recent articles per company
  -> SentimentAnalysisPort (LLM)            batch article classification
  -> Per-article: label (POS/NEG/MIXED/NEUTRAL), confidence, rationale
  -> Aggregate into company-level scores    overall score, distribution, risk summary
  -> CompanySentimentPort.save()            persist for dashboard + deal enrichment
```

**Research Pipeline**
```
POST /api/v1/research  (or ResearchHarvestScheduler daily at 04:00 UTC)
  -> ResearchPlanningService                AI decomposes query into retrieval queries
  -> SourceRetrievalPort adapters           Perplexity API + DB article fetch
  -> CitationAssembler                      deduplicate + rank sources
  -> ResearchSynthesisService               AI synthesizes answer with citations
  -> ResearchRunPort.save()                 persist for audit trail
```

## Tech Stack

### Core Framework
| Component | Technology | Version |
|-----------|------------|---------|
| Application Framework | Spring Boot | 3.4.5 |
| Language | Java (LTS) | 17 |
| Build Tool | Apache Maven | 3.8+ |
| Dependency Management | Spring AI BOM | 1.0.0 |

### AI / LLM Integration
| Component | Technology | Version |
|-----------|------------|---------|
| AI Framework | Spring AI | 1.0.0 |
| Anthropic Claude | spring-ai-starter-model-anthropic | 1.0.0 |
| OpenAI GPT | spring-ai-starter-model-openai | 1.0.0 |
| AWS Bedrock | spring-ai-starter-model-bedrock-converse (Nova/Titan) | 1.0.0 |
| Perplexity Sonar | RestClient direct integration | — |
| Google Gemini | RestClient direct integration | — |
| Vector Store | spring-ai-starter-vector-store-pgvector | 1.0.0 |
| Prompt Templates | 18 externalized prompt files | — |

### Data & Persistence
| Component | Technology | Version |
|-----------|------------|---------|
| Relational DB | PostgreSQL | 16 |
| Vector Store | PGVector (PostgreSQL extension) | — |
| ORM | Spring Data JPA / Hibernate | — |
| Test DB | H2 (in-memory, @DataJpaTest) | — |
| Schema | 41 JPA entities across 20+ tables | — |

### Web & UI
| Component | Technology | Version |
|-----------|------------|---------|
| Server-Side Rendering | Thymeleaf | — |
| CSS Framework | Tailwind CSS | — |
| Newsletter Editor | TinyMCE (WebJar) | 7.9.0 |
| Charts & Visualization | Chart.js (radar, doughnut, bar, multi-line) | — |
| Markdown Rendering | CommonMark | 0.24.0 |
| API Documentation | springdoc-openapi / Swagger UI | 2.8.6 |
| Security Templating | thymeleaf-extras-springsecurity6 | — |

### Security & Auth
| Component | Technology | Version |
|-----------|------------|---------|
| Authentication | Spring Security 6 (session-based form login) | — |
| Password Hashing | BCrypt | — |
| Authorization | Role-based (ADMIN/USER) + tier-based (4-tier) | — |
| API Auth | X-API-Key header authentication | — |
| CSRF Protection | Spring Security CSRF tokens | — |

### Billing & Payments
| Component | Technology | Version |
|-----------|------------|---------|
| Payment Processing | Stripe Billing | SDK 28.2.0 |
| Checkout | Stripe Checkout (hosted) | — |
| Customer Portal | Stripe Customer Portal | — |
| Webhooks | Stripe webhook signature verification | — |
| JSON Serialization | Gson (Stripe SDK dependency) | — |

### Data Ingestion & Scraping
| Component | Technology | Version |
|-----------|------------|---------|
| RSS/Atom Parsing | Rome | 2.1.0 |
| Web Scraping | Jsoup (HTML parsing + CSS selectors) | 1.18.3 |
| PDF Parsing | Apache PDFBox | 3.0.3 |
| DOCX Parsing | Apache POI-OOXML | 5.3.0 |
| External APIs | openFDA, Federal Register, HuggingFace, PubMed E-utilities, Perplexity | — |

### Email & Delivery
| Component | Technology | Version |
|-----------|------------|---------|
| Mail Framework | Spring Boot Mail (JavaMailSender) | — |
| Dev SMTP | MailHog (SMTP trap at localhost:1025) | — |
| Prod SMTP | Amazon SES (STARTTLS) | — |
| Transactional Email | Welcome, demo expiration, admin notifications | — |
| Newsletter Delivery | Tier-routed (SUBSCRIBER=full, FREE=digest) | — |

### Caching & Performance
| Component | Technology | Version |
|-----------|------------|---------|
| Cache Framework | Spring Cache | — |
| Cache Provider | Caffeine (TTL-based in-memory) | — |
| Use Case | AI search result caching | — |

### Testing
| Component | Technology | Version |
|-----------|------------|---------|
| Test Framework | JUnit 5 | — |
| Assertions | AssertJ | — |
| Mocking | Mockito | — |
| Security Testing | spring-security-test (@WithMockUser) | — |
| Web Testing | MockMvc (@WebMvcTest slices) | — |
| Persistence Testing | @DataJpaTest (H2 in-memory) | — |
| Coverage | 2,385+ tests across 307 test classes | — |

### Infrastructure & DevOps
| Component | Technology | Version |
|-----------|------------|---------|
| Cloud Provider | AWS (EC2, SES, Secrets Manager) | — |
| Code Generation | Lombok (@Slf4j, @Data) | — |
| Architecture | Hexagonal / Ports-and-Adapters | — |
| Scheduling | Spring @Scheduled (11 externalized cron jobs) | — |

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **PostgreSQL 16** with PGVector extension
- **API Key** — at least one of `ANTHROPIC_API_KEY` or `OPENAI_API_KEY`

## Getting Started

### 1. Set Environment Variables

```bash
export ANTHROPIC_API_KEY=your-key-here
# or
export OPENAI_API_KEY=your-key-here

# Optional — enables additional AI models and features
export PERPLEXITY_API_KEY=your-key-here    # Perplexity research pipeline + Sonar search
export GEMINI_API_KEY=your-key-here        # Google Gemini AI search synthesis
```

### 2. Build and Run

```bash
# Build and run tests
mvn verify

# Start the application
mvn spring-boot:run
```

The application starts on `http://localhost:8080`. You will be redirected to the login page.

### Default Login Credentials (dev only)

| Email | Password | Role |
|-------|----------|------|
| `admin@gmail.com` | `admin123` | ADMIN |
| `demo@gmail.com` | `demo123` | USER |

## Authentication

Spring Security protects all Thymeleaf UI pages behind session-based form login. REST API endpoints (`/api/**`), Stripe webhooks (`/stripe/**`), monitoring triggers (`/monitoring/**`), and the pricing page (`/pricing`) remain publicly accessible.

| Path Pattern | Access |
|-------------|--------|
| `/login`, `/register`, `/pricing`, `/privacy`, `/unsubscribe/**` | Public |
| `/directory`, `/directory/**` | Public (no login required) |
| `/api/**`, `/monitoring/**`, `/stripe/**` | Public (secured separately via API keys / Stripe signatures) |
| `/dashboard/**`, `/newsletter/**`, `/research/**`, `/wiki/**` | Requires login |
| `/admin/**`, `/monitoring/**` | Requires ADMIN role |
| `/watchlist`, `/api/v1/companies/discover` | Requires SUBSCRIBER tier |

## Web UI (Thymeleaf)

48 pages organized across 6 navigation sections:

### Dashboard & Analytics
| URL | Description |
|-----|-------------|
| `/dashboard` | Analytics overview — newsletter runs, evaluations, Chart.js trend + topic charts |
| `/dashboard/articles` | Article list with topic filter, sort, and pagination |
| `/dashboard/news` | Articles grouped by 13 configurable topic sections with per-topic AI summaries |
| `/dashboard/search` | Multi-field article search with criteria-based filtering (topic, author, date range) |

### Intelligence & Analysis
| URL | Description |
|-----|-------------|
| `/dashboard/risk` | Sentiment & risk overview — horizontal bar chart + company cards with sentiment distribution |
| `/dashboard/risk/{slug}` | Company sentiment detail — doughnut chart + article-level sentiment table with rationale |
| `/dashboard/frameworks` | Framework competitive analysis — Chart.js radar chart + score cards per company |
| `/dashboard/frameworks/{slug}` | Framework detail — 6-dimension breakdown with strengths, weaknesses, recent developments |
| `/dashboard/deals` | Deal signal feed — type filter pills, amount/counterparty columns, tier-gated (FREE=10, SUBSCRIBER=100) |
| `/dashboard/deals/{signalId}` | Deal context detail — cross-reference cards for sentiment, framework, regulatory, company profile |
| `/dashboard/trends` | Keyword trend analysis — rising, fading, and new healthcare AI trends with momentum charts |
| `/dashboard/trends/history` | Trend history archive — multi-line Chart.js chart + clickable snapshot timeline table |
| `/dashboard/trends/history/{epochMillis}` | Trend snapshot detail — bar chart + rising/new keyword cards with linked articles |
| `/dashboard/regulatory` | FDA/CMS regulatory alerts — summary badges, filter tabs (FDA/CMS), color-coded type badges, tier gating |
| `/dashboard/legal` | Legal timeline — unified chronological view of legal articles, policy, and regulatory events |

### Research & Search
| URL | Description |
|-----|-------------|
| `/research/ai-search` | Multi-model AI search — Claude, GPT, Perplexity, Gemini synthesis with model selection checkboxes |
| `/research/vendors` | Vendor comparison card grid — AI-scored strengths/weaknesses with relevance bars |
| `/research/runs` | Research run history table with mode badge, citation count, timestamp |
| `/research/runs/{runId}` | Research run detail card with full synthesized answer and source citations |

### Wiki & Knowledge Base
| URL | Description |
|-----|-------------|
| `/wiki` | Searchable wiki page grid with type filter (Entity/Concept/Comparison/Overview) |
| `/wiki/{slug}` | Wiki page detail — rendered markdown, provenance table, contradictions, related pages, revision history |
| `/wiki/contradictions` | Reversal Watch — contradiction feed with side-by-side claim comparison and date filter |
| `/wiki/digest` | What Changed digest — new pages, updates, and contradictions over configurable time window |
| `/wiki/ask` | Wiki Q&A — ask natural language questions answered from wiki knowledge base |

### Company Intelligence
| URL | Description |
|-----|-------------|
| `/companies` | Company intelligence index — all tracked companies with article counts and trend indicators |
| `/companies/{slug}` | Company detail — linked articles, event timeline, discovered/updated dates, homepage link |
| `/companies/{slug}/relationships` | Company relationship network — partnerships, acquisitions, integrations, competitive dynamics |

### Public Directory (no login required)
| URL | Description |
|-----|-------------|
| `/directory` | Public AI healthcare company directory — ranked by live signal score with sort tabs (Trending / Recently Funded / Watch List), sector filter pills, signal badges, CSV export |
| `/directory/export.csv` | CSV download of all companies with signal data (articles 90d, deal amount, sentiment score, relevance score) |
| `/directory/{slug}` | Company profile — category, funding stage, website, HQ, clickable `[N]` citation anchors linking to Discovery Sources |

### Newsletter & Content
| URL | Description |
|-----|-------------|
| `/newsletter/runs` | Newsletter run list with status badges (DRAFT/SENT/ARCHIVED) and edit links |
| `/newsletter/runs/{runId}/edit` | TinyMCE WYSIWYG editor — edit and send newsletter drafts |
| `/watchlist` | Subscriber watchlist — add keyword/company/topic items, view recent matches (SUBSCRIBER only) |
| `/notes` | Analyst notes dashboard — personal annotations on companies, deals, and trends |

### Admin & Account
| URL | Description |
|-----|-------------|
| `/admin` | User management table + system status dashboard (ADMIN only) |
| `/admin/pipelines` | Pipeline management — manual trigger controls for 23 individually-triggerable pipelines, including Deal Signal Detection (ADMIN only) |
| `/pricing` | Tier comparison (Demo/Free/Subscriber) with Stripe Checkout integration |
| `/profile` | Subscriber self-service — tier badge, usage meter, Stripe customer portal link |
| `/login` | Session-based form login with "Remember Me" |
| `/register` | Self-registration for new DEMO users (7-day trial) |
| `/developer` | Developer portal — API documentation, key management, usage examples |
| `/privacy` | Privacy policy — data collection, opt-in process, unsubscribe, and bounce/complaint handling disclosure |

## REST API Endpoints

### Articles & Content
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/articles?topic=&limit=` | List harvested articles |
| GET | `/api/v1/articles/search` | Multi-field article search with criteria filtering |

### Newsletter & Delivery
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/runs` | List all newsletter runs |
| GET | `/api/v1/runs/{runId}` | Get a specific newsletter run |
| POST | `/api/v1/newsletter/deliver` | Trigger newsletter delivery |
| POST | `/api/v1/subscribers` | Subscribe an email address |
| GET | `/api/v1/subscribers` | List all subscribers |
| DELETE | `/api/v1/subscribers?email=` | Unsubscribe an email address |

### Research & AI Search
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/research` | Execute staged research query |
| GET | `/api/v1/research/runs` | List research run history |
| GET | `/api/v1/research/runs/{runId}` | Get research run detail |
| GET | `/api/v1/search/ai` | Multi-model AI search with synthesis |

### Intelligence & Analysis
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/sentiment` | List all company sentiment scores |
| GET | `/api/v1/sentiment/{slug}` | Get company sentiment detail with article-level breakdown |
| POST | `/api/v1/sentiment/analyze` | Trigger sentiment analysis pipeline |
| GET | `/api/v1/frameworks` | List all framework competitive analyses |
| GET | `/api/v1/frameworks/{slug}` | Get framework analysis detail for a company |
| POST | `/api/v1/frameworks/analyze` | Trigger framework analysis pipeline |
| GET | `/api/v1/deals` | List deal signals with optional type filter |
| GET | `/api/v1/deals/{signalId}` | Get deal signal detail with cross-referenced context |
| POST | `/api/v1/deals/detect` | Trigger deal signal detection pipeline |
| GET/POST | `/api/v1/trends/latest`, `/api/v1/trends/detect` | Retrieve latest trend snapshot / trigger detection |
| GET | `/api/v1/trends/history` | Retrieve all historical trend snapshots |

### Company & Discovery
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/companies/discover` | AI healthcare company discovery pipeline (SUBSCRIBER only) |

### Documents & Export
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/documents/ingest` | Ingest documents for RAG (PDF, DOCX, TXT) |
| POST | `/api/v1/market-intelligence/refresh` | Trigger market intelligence report |

### Analytics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analytics/ingestion` | Ingestion analytics |
| GET | `/api/v1/analytics/runs` | Newsletter run analytics |
| GET | `/api/v1/analytics/evaluations` | Evaluation analytics |

### Prompt Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/PUT | `/api/v1/search-prompts/{engine}` | View/update search prompt templates |
| POST/GET/DELETE | `/api/v1/variants` | Manage prompt variants |
| POST/GET | `/api/v1/evaluations` | Run/view prompt evaluations |
| POST | `/api/v1/comparisons` | Compare two prompt variants |

### Webhook Configuration
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/webhooks` | Create webhook channel (SUBSCRIBER only) |
| GET | `/api/v1/webhooks` | List user's webhook channels |
| DELETE | `/api/v1/webhooks/{id}` | Delete webhook channel |
| POST | `/api/v1/webhooks/{id}/test` | Send test notification to webhook |

### Monitoring & Pipeline Triggers (ADMIN)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/monitoring/harvest` | Trigger RSS feed harvest |
| POST | `/monitoring/feeds` | Trigger full RSS feed harvest across all tiers |
| POST | `/monitoring/competitor` | Trigger competitor page harvest |
| POST | `/monitoring/huggingface` | Trigger HuggingFace model discovery |
| POST | `/monitoring/summaries` | Trigger AI topic summary generation |
| POST | `/monitoring/embeddings` | Trigger article embedding into vector store |
| POST | `/monitoring/wiki/compile` | Trigger LLM wiki compilation from recent articles |
| POST | `/monitoring/backfill` | Trigger PubMed historical backfill (10 queries, 2022-2025) |
| POST | `/monitoring/backfill/custom?query=` | Run custom PubMed backfill query |
| POST | `/monitoring/regulatory/harvest` | Trigger regulatory event harvest (FDA/CMS) |
| GET | `/monitoring/hashes` | List page content hashes |

### Billing
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/stripe/checkout` | Create Stripe Checkout session for upgrade |
| POST | `/api/v1/stripe/webhook` | Stripe webhook receiver (tier updates) |

## Scheduled Jobs

All schedules are configurable via `application.yml` — no hardcoded cron expressions.

| Job | Default (UTC) | Config Key | Description |
|-----|---------------|------------|-------------|
| RSS Harvest (ACAD/REG) | 04:00 daily | `aihealthcare.harvest.daily-cron` | RSS feeds -> DB + topic summaries -> full pipeline orchestrator |
| Regulatory Harvest | 04:30 daily | `aihealthcare.regulatory.schedule` | FDA/CMS harvest -> dedup -> save -> watchlist match |
| Research Harvest | 04:00 daily | `aihealthcare.research.harvest.cron` | COMBINED pipeline per topic |
| Competitor Scrape | 05:00 daily | `aihealthcare.harvest.competitor-cron` | Web page SHA-256 change detection |
| HuggingFace Discovery | 05:30 daily | `aihealthcare.harvest.huggingface-cron` | Healthcare LLM model API |
| Industry RSS | Every 4 hours | `aihealthcare.harvest.industry-rate-ms` | High-frequency industry feeds |
| Embedding | 07:00 daily | `aihealthcare.embedding.schedule` | Vector store refresh (after harvests) |
| Trend Detection | Sunday 08:00 | `aihealthcare.trends.schedule` | Keyword frequency analysis -> TrendSnapshot |
| Newsletter Draft | 00:00 daily | `aihealthcare.newsletter.schedule` | Generate DRAFT (review + send manually) |
| Market Intelligence | 1st of month, 08:00 | `aihealthcare.market-intelligence.schedule` | Monthly AI market report |
| Demo Expiration | Daily | `aihealthcare.demo.expiration-cron` | Expire DEMO accounts after 7-day trial |

## Configuration

### News Topics

13 topic sections are configured in `application.yml`, each with multiple feed sources:

- General AI Healthcare News
- AI Healthcare Software Development
- Healthcare Outsourcing and Jobs Layoffs
- AI Healthcare Government Policy
- AI Healthcare Legal
- OpenAI Healthcare
- Anthropic Healthcare
- Amazon Connect Health
- Perplexity Healthcare
- Google Healthcare
- Beckers Hospital Review
- New AI Healthcare Companies (Subscriber only)
- Clinical Trials

### Subscription Tiers

| Feature | Demo (7-day trial) | Free | Subscriber ($19/mo) |
|---------|------|------|---------------------|
| Newsletter content | Full newsletter | Digest summary | Full newsletter |
| Article archive | Unlimited | 7 days | Unlimited |
| AI research queries | 200/month | 15/month | 200/month |
| Semantic search | Yes | No | Yes |
| Multi-model AI search | Yes | Limited | Yes |
| New AI Healthcare Companies | Yes | No | Yes |
| Custom Watchlists | Yes | No | Yes |
| Regulatory Alerts | 50 events | 5 events | 50 events |
| Sentiment & Risk Dashboard | All companies | 5 companies | All companies |
| Framework Analysis | Yes | Limited | Yes |
| Deal Signals | 100 signals | 10 signals | 100 signals |
| Trend History | Full archive | 4 snapshots | Full archive |
| Webhook Notifications | Yes | No | Yes |
| Trend Analysis | Yes | Yes | Yes |
| Company Profiles | Yes | Yes | Yes |

### Framework Company Configuration

Competitive framework analysis companies are configured via YAML — no code changes needed:

```yaml
aihealthcare:
  frameworks:
    companies:
      - slug: tempus-ai
        name: Tempus AI
        url: https://www.tempus.com
        topics:
          - precision medicine
          - clinical genomics
```

### Profiles

- **demo** (default) — development mode with PostgreSQL on localhost
- **dev** (`application-dev.yml`) — MailHog on `localhost:1025`
- **aws** (`application-aws.yml`) — production on AWS EC2 with Amazon SES
- **prod** (`application-prod.yml`) — Amazon SES with STARTTLS

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ANTHROPIC_API_KEY` | One of these | Claude AI model access |
| `OPENAI_API_KEY` | One of these | GPT AI model access |
| `PERPLEXITY_API_KEY` | Optional | Perplexity Sonar search + research pipeline |
| `GEMINI_API_KEY` | Optional | Google Gemini AI search synthesis |
| `STRIPE_API_KEY` | Optional | Stripe Billing backend |
| `STRIPE_PUBLISHABLE_KEY` | Optional | Stripe Checkout frontend |
| `STRIPE_WEBHOOK_SECRET` | Optional | Stripe webhook signature verification |
| `STRIPE_MEMBER_PRICE_ID` | Optional | Stripe Price ID for subscriber tier |

## Testing

2,385+ tests across 307 test classes — all pass with no live AI or network calls.

```bash
# Run all unit tests (no AI calls, uses H2 in-memory DB for @DataJpaTest)
mvn test

# Run specific tests for changed code (recommended for development)
mvn test -Dtest="FooServiceTest,BarControllerTest"

# Run AI integration smoke tests (requires valid API key)
mvn test -Dspring.profiles.active=ai-integration
```

### Test Pyramid

| Layer | Test Type | AI Calls? | Count |
|-------|-----------|-----------|-------|
| Domain models | Pure unit tests | No | ~200 |
| Domain services | Unit + mock ports | No (mocked) | ~300 |
| Web controllers | MockMvc slice (`@WebMvcTest`) | No (mocked) | ~350 |
| Persistence adapters | `@DataJpaTest` | No | ~150 |
| Infrastructure adapters | Unit + mock clients | No (mocked) | ~300 |
| AI smoke tests | Integration | Yes | `@Profile("ai-integration")` only |

## Project Structure

```
AIHealthcare/
├── application/src/main/java/com/wgblackmon/aihealthcare/
│   ├── domain/
│   │   ├── model/           # 103 records: NewsArticle, WikiPage, DealSignal, CompanySentiment, FrameworkAnalysis...
│   │   ├── port/inbound/    # Use-case interfaces (inbound ports)
│   │   ├── port/outbound/   # Port interfaces (outbound ports) — 92 total
│   │   ├── service/         # Domain services: Newsletter, Research, Evaluation, Wiki, Sentiment, Framework, Deal...
│   │   └── exception/       # Domain exceptions
│   ├── infrastructure/
│   │   ├── ai/              # 9 Spring AI adapters: summarize, evaluate, search x4, wiki, sentiment, deals, frameworks
│   │   ├── config/          # AppConfig, SecurityConfig, bean wiring, properties
│   │   ├── delivery/        # EmailDeliveryAdapter, TransactionalEmailAdapter, NotebookLMService
│   │   ├── ingestion/       # RSS, web scraping, HuggingFace, Perplexity, PubMed backfill, regulatory harvesters
│   │   ├── persistence/     # 41 JPA entities, repositories, storage adapters
│   │   ├── research/        # Perplexity + legacy Google research adapters
│   │   └── scheduler/       # Pipeline orchestrator, newsletter generation, demo expiration
│   └── web/
│       ├── controller/      # 71 REST + Thymeleaf controllers
│       └── dto/             # Request/response records
├── application/src/main/resources/
│   ├── prompts/             # 18 AI prompt templates
│   └── templates/           # 51 Thymeleaf HTML templates + 6 fragments
├── application/src/test/    # 307 test classes (2,385+ tests)
├── docs/                    # Architecture, conventions, QA plan documentation
├── pom.xml
└── CLAUDE.md                # AI assistant project context
```

## AI Prompt Templates

18 prompt templates drive the AI features:

| Template | Purpose |
|----------|---------|
| `summarize-articles.txt` | Standard newsletter section summarization |
| `summarize-articles-rag.txt` | RAG-augmented summarization with archived context |
| `evaluate-section.txt` | LLM-as-judge scoring on 5 quality dimensions |
| `generate-introduction.txt` | Newsletter introduction generation |
| `research-plan.txt` | Research query decomposition into retrieval queries |
| `research-synthesis.txt` | Research answer synthesis with citations |
| `vendor-compare.txt` | Vendor assessment (strengths/weaknesses/relevance) |
| `topic-summary.txt` | 3-sentence topic summary from article titles |
| `ai-search-synthesis.txt` | Multi-model AI search synthesis with `[N]` citations |
| `wiki-compile.txt` | Structured wiki compilation (PAGE/CONTRADICTION/WARNING) |
| `sentiment-analysis.txt` | Per-article sentiment classification (label/confidence/rationale) |
| `framework-analysis.txt` | 6-dimension competitive scoring with structured output |
| `deal-classification.txt` | Batch deal classification (TYPE/AMOUNT/COMPANY/COUNTERPARTY/CONFIDENCE) |
| `intel-report.txt` | Monthly market intelligence report generation |
| `digest-summary.txt` | FREE-tier daily digest newsletter summary |
| `tech-trend-score.txt` | Article significance scoring (1-10 scale) |
| `trend-extract.txt` | Keyword extraction from article corpus |
| `trend-summary.txt` | Trend narrative summary for analyst consumption |

## Author

**Bill Blackmon**

## License and Use Restrictions

This repository is **source-available, not open source**.

AIHealthcare is publicly visible for portfolio, demonstration, evaluation, and
transparency purposes only. Unless you have received prior written permission
from the owner, you may not copy, redistribute, sublicense, sell, commercialize,
host, operate, modify, or create derivative works from this repository or any
substantial portion of it.

All rights are reserved. See [`LICENSE`](./LICENSE) and [`NOTICE`](./NOTICE) for
the full terms.

### Commercial Use

Commercial use is not permitted without prior written permission. This includes,
but is not limited to:

- using this project to operate a newsletter, publication, SaaS product, content
  automation system, healthcare AI product, or competing service;
- copying or adapting the prompts, workflows, editorial process, source
  ingestion logic, summarization logic, or monetization strategy;
- republishing, reselling, sublicensing, or incorporating this project into a
  paid product or service.

For licensing, acquisition, partnership, or commercial-use inquiries, contact:

**newsletter@bigskylabs.ai**

### Security and Private Configuration

This public repository should not contain production secrets, API keys,
passwords, private credentials, subscriber data, sponsor data, or confidential
business information.

Any real deployment should use private configuration files, environment
variables, GitHub Actions secrets, AWS Secrets Manager, Parameter Store, Vault,
or a private companion repository.

If you discover a secret or sensitive file in this repository, please report it
privately to:

**newsletter@bigskylabs.ai**
