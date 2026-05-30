# AIHealthcare

An automated AI-powered newsletter and research platform that discovers, summarizes, and delivers the latest artificial intelligence in healthcare news — built with Spring Boot and Spring AI.

## What It Does

AIHealthcare runs multiple automated pipelines:

1. **Harvest** — Scrapes articles from RSS feeds (PubMed, Beckers, Google News), competitor web pages (Anthropic, OpenAI, Amazon, Perplexity, Google), and the HuggingFace model API
2. **Store** — Persists articles in PostgreSQL and indexes them as vector embeddings (PGVector) for semantic search
3. **Summarize** — Uses Spring AI (Claude or OpenAI) to generate concise, topic-grouped newsletter sections with attributed sources
4. **Research** — Staged research pipeline combining Perplexity API + DB articles, with planning, retrieval, citation assembly, and AI synthesis
5. **Evaluate** — LLM-as-judge prompt evaluation scoring across 5 dimensions with A/B variant comparison
6. **Deliver** — Generates a daily newsletter draft for review; send manually after editing in the TinyMCE WYSIWYG editor; tier-aware content gating (FREE gets teaser, MEMBER gets full)
7. **Export** — NotebookLM-compatible article exports with HTML summaries grouped by source
8. **Gate** — Usage metering and feature gating per subscription tier (FREE vs MEMBER): archive depth (FREE=7 days, MEMBER=unlimited), newsletter teaser vs full content, AI query limits, with Stripe Billing integration

## Architecture

The project follows **hexagonal architecture** (ports and adapters), keeping the domain layer framework-free and all infrastructure concerns pluggable:

```
web (controllers + Thymeleaf)  -->  application (use cases)  -->  domain (models + ports)
                                                                       ^
                        infrastructure/* (adapters) -------------------+
                        - ai/          Spring AI adapter (summarize, evaluate, report)
                        - config/      AppConfig, SecurityConfig, properties
                        - ingestion/   RSS, web scraping, HuggingFace, Perplexity
                        - persistence/ JPA entities, repositories, storage adapters
                        - delivery/    Email (JavaMailSender) + NotebookLM export
                        - research/    Perplexity + legacy Google research adapters
```

Swapping the AI provider, database, or delivery mechanism requires no domain changes — only a new adapter.

### Key Flows

**Article Harvest → Newsletter Draft**
```
FeedHarvestScheduler (04:00 UTC)
  → RomeFeedHarvester.harvestAll()         fetch RSS from 50+ configured feeds
  → ArticleStoragePort.save()              persist to news_articles (dedup by URL)
  → TopicSummaryGenerationService          AI-summarize each topic (3 sentences)

NewsletterGenerationScheduler (00:00 UTC)
  → IngestArticlesUseCase.ingest()         load previous day's articles
  → GenerateNewsletterUseCase.generate()   AI-summarize into sections, render HTML
  → NewsletterRunPort.save()               persist as DRAFT

User reviews at /newsletter/runs/{runId}/edit (TinyMCE)
  → POST .../save                          save edits
  → POST .../send                          deliver to all active subscribers
```

**Research Pipeline**
```
POST /api/v1/research  (or ResearchHarvestScheduler daily at 04:00 UTC)
  → ResearchPlanningService                AI decomposes query into retrieval queries
  → SourceRetrievalPort adapters           Perplexity API + DB article fetch
  → CitationAssembler                      deduplicate + rank sources
  → ResearchSynthesisService               AI synthesizes answer with citations
  → ResearchRunPort.save()                 persist for audit trail
```

**Prompt Evaluation**
```
POST /api/v1/evaluations
  → AiSummarizationPort.summarizeWithTemplate()   generate section with variant prompt
  → AiEvaluationPort.evaluate()                   LLM-as-judge scores on 5 dimensions
  → EvaluationResultPort.save()                    persist scores

POST /api/v1/comparisons
  → runs two variants side-by-side, compares scores
```

## Tech Stack

| Component         | Technology                                         |
|-------------------|----------------------------------------------------|
| Framework         | Spring Boot 3.4.5, Java 17                         |
| AI                | Spring AI 1.0.0 (Anthropic Claude / OpenAI)        |
| Relational DB     | PostgreSQL 16                                      |
| Vector Store      | PGVector (PostgreSQL extension)                    |
| RSS Parsing       | Rome 2.1.0                                         |
| Web Scraping      | Jsoup 1.18.3                                       |
| Document Parsing  | PDFBox 3.0.3, POI-OOXML 5.3.0                     |
| Newsletter Editor | TinyMCE 7.9.0 (WebJar)                             |
| Authentication    | Spring Security 6 (session-based form login, BCrypt)|
| Billing           | Stripe Billing (webhooks + checkout)               |
| Email (dev)       | MailHog (SMTP trap)                                |
| Email (prod)      | Amazon SES                                         |
| UI                | Thymeleaf + Spring Security extras                 |
| Build             | Maven                                              |
| Testing           | JUnit 5 + AssertJ + Mockito (559 tests)            |

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker** (for PostgreSQL and MailHog)
- **API Key** — at least one of `ANTHROPIC_API_KEY` or `OPENAI_API_KEY`

## Getting Started

### 1. Start Infrastructure (Docker)

```bash
# PostgreSQL + PGVector (relational DB + vector store)
docker run -d --name aihealthcare-postgres \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=1454 \
  -e POSTGRES_DB=aihealthcaredb \
  -p 5432:5432 \
  pgvector/pgvector:pg16

# MailHog (dev email catcher — view captured emails at http://localhost:8025)
docker run -d --name aihealthcare-mailhog \
  -p 1025:1025 \
  -p 8025:8025 \
  mailhog/mailhog
```

### 2. Set Environment Variables

```bash
export ANTHROPIC_API_KEY=your-key-here
# or
export OPENAI_API_KEY=your-key-here

# Optional — enables Perplexity research pipeline
export PERPLEXITY_API_KEY=your-key-here
```

### 3. Build and Run

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
| `/login`, `/pricing` | Public |
| `/api/**`, `/monitoring/**`, `/stripe/**` | Public (secured separately via API keys / Stripe signatures) |
| `/dashboard`, `/newsletter/**`, `/research/**` | Requires login |

## Web UI (Thymeleaf)

| URL | Description |
|-----|-------------|
| `/dashboard` | Analytics overview — ingestion stats, run history |
| `/dashboard/articles` | Article list with topic filter and sort |
| `/dashboard/news` | Articles grouped by 11 configurable topic sections with AI summaries |
| `/research/compare` | Side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH results |
| `/research/runs` | Research run history table |
| `/research/runs/{runId}` | Research run detail |
| `/research/vendors` | Vendor comparison card grid with strengths/weaknesses |
| `/newsletter/runs` | Newsletter run list with status badges and edit links |
| `/newsletter/runs/{runId}/edit` | TinyMCE WYSIWYG editor — edit and send newsletter drafts |
| `/pricing` | Two-tier comparison (Free vs Member) with feature limits |
| `/login` | Session-based form login |

## REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/articles?topic=&limit=` | List harvested articles |
| GET | `/api/v1/runs` | List all newsletter runs |
| GET | `/api/v1/runs/{runId}` | Get a specific newsletter run |
| POST | `/api/v1/subscribers` | Subscribe an email address |
| GET | `/api/v1/subscribers` | List all subscribers |
| DELETE | `/api/v1/subscribers?email=` | Unsubscribe an email address |
| POST | `/api/v1/newsletter/deliver` | Trigger newsletter delivery |
| POST | `/api/v1/research` | Execute staged research query |
| GET | `/api/v1/research/runs` | List research run history |
| GET | `/api/v1/research/runs/{runId}` | Get research run detail |
| POST | `/api/v1/documents/ingest` | Ingest documents for RAG |
| POST | `/api/v1/market-intelligence/refresh` | Trigger market intelligence report |
| GET | `/api/v1/analytics/ingestion` | Ingestion analytics |
| GET | `/api/v1/analytics/runs` | Newsletter run analytics |
| GET | `/api/v1/analytics/evaluations` | Evaluation analytics |
| POST | `/monitoring/harvest` | Trigger RSS feed harvest |
| POST | `/monitoring/competitor` | Trigger competitor page harvest |
| POST | `/monitoring/huggingface` | Trigger HuggingFace model discovery |
| POST | `/monitoring/summaries` | Trigger AI topic summary generation |
| GET | `/monitoring/hashes` | List page content hashes |
| GET/PUT | `/api/v1/search-prompts/{engine}` | View/update search prompt templates |
| POST/GET/DELETE | `/api/v1/variants` | Manage prompt variants |
| POST/GET | `/api/v1/evaluations` | Run/view prompt evaluations |
| POST | `/api/v1/comparisons` | Compare two prompt variants |
| POST | `/stripe/create-checkout-session` | Create Stripe Checkout session for upgrade |
| POST | `/stripe/webhook` | Stripe webhook receiver (tier updates) |

## Scheduled Jobs

All schedules are configurable via `application.yml` — no hardcoded cron expressions.

| Job | Default (UTC) | Config Key | Description |
|-----|---------------|------------|-------------|
| RSS Harvest (ACAD/REG) | 04:00 daily | `aihealthcare.harvest.daily-cron` | RSS feeds → DB + topic summaries |
| Research Harvest | 04:00 daily | `aihealthcare.research.harvest.cron` | COMBINED pipeline per topic |
| Competitor Scrape | 05:00 daily | `aihealthcare.harvest.competitor-cron` | Web page SHA-256 change detection |
| HuggingFace Discovery | 05:30 daily | `aihealthcare.harvest.huggingface-cron` | Healthcare LLM model API |
| Industry RSS | Every 4 hours | `aihealthcare.harvest.industry-rate-ms` | High-frequency industry feeds |
| Embedding | 07:00 daily | `aihealthcare.embedding.schedule` | Vector store refresh (after harvests) |
| Newsletter Draft | 00:00 daily | `aihealthcare.newsletter.schedule` | Generate DRAFT (review + send manually) |
| Market Intelligence | 1st of month, 08:00 | `aihealthcare.market-intelligence.schedule` | Monthly AI market report |

## Configuration

### News Topics

11 topic sections are configured in `application.yml`, each with multiple feed sources:

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

### Subscription Tiers

| Feature | Free | Member ($15/mo) |
|---------|------|-----------------|
| Newsletter content | Teaser (first section) | Full newsletter |
| Article archive | 7 days | Unlimited |
| AI research queries | 15/month | 200/month |
| Semantic search | No | Yes |

Tier upgrades are handled via Stripe Billing webhooks. Configure Stripe keys in `.env`:
```
STRIPE_API_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_MEMBER_PRICE_ID=price_...
```

### Profiles

- **demo** (default) — development mode with PostgreSQL on localhost
- **dev** (`application-dev.yml`) — MailHog on `localhost:1025`
- **prod** (`application-prod.yml`) — Amazon SES with STARTTLS

## Testing

559 tests across 67 test classes — all pass with no live AI or network calls.

```bash
# Run all unit tests (no AI calls, uses H2 in-memory DB for @DataJpaTest)
mvn test

# Run AI integration smoke tests (requires valid API key)
mvn test -Dspring.profiles.active=ai-integration
```

## Project Structure

```
AIHealthcare/
├── application/src/main/java/com/wgblackmon/aihealthcare/
│   ├── domain/
│   │   ├── model/           # NewsArticle, Topic, NewsletterDraft, AppUser, SubscriptionTier...
│   │   ├── port/inbound/    # Use-case interfaces (inbound ports)
│   │   ├── port/outbound/   # Port interfaces (outbound ports)
│   │   ├── service/         # Domain services (Newsletter, Research, Evaluation, TopicSummary)
│   │   └── exception/       # Domain exceptions
│   ├── infrastructure/
│   │   ├── ai/              # Spring AI adapters (summarize, evaluate, embed, report)
│   │   ├── config/          # AppConfig, SecurityConfig, bean wiring, properties
│   │   ├── delivery/        # EmailDeliveryAdapter, NotebookLMService
│   │   ├── ingestion/       # RSS, web scraping, HuggingFace, Perplexity, document parsing
│   │   ├── persistence/     # JPA entities, repositories, storage adapters (12 tables)
│   │   ├── research/        # Perplexity + legacy Google research adapters
│   │   └── scheduler/       # NewsletterGenerationScheduler
│   └── web/
│       ├── controller/      # REST + Thymeleaf controllers
│       └── dto/             # Request/response records
├── application/src/main/resources/
│   ├── prompts/             # AI prompt templates (8 templates)
│   └── templates/           # Thymeleaf HTML templates (11 pages)
├── docs/                    # Architecture and conventions documentation
├── pom.xml
└── CLAUDE.md                # AI assistant project context
```

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

**wgblackmonall@gmail.com**

### Security and Private Configuration

This public repository should not contain production secrets, API keys,
passwords, private credentials, subscriber data, sponsor data, or confidential
business information.

Any real deployment should use private configuration files, environment
variables, GitHub Actions secrets, AWS Secrets Manager, Parameter Store, Vault,
or a private companion repository.

If you discover a secret or sensitive file in this repository, please report it
privately to:

**wgblackmonall@gmail.com**
