# AIHealthcare

An automated AI-powered newsletter and research platform that discovers, summarizes, and delivers the latest artificial intelligence in healthcare news — built with Spring Boot and Spring AI.

## What It Does

AIHealthcare runs multiple automated pipelines:

1. **Harvest** — Scrapes articles from RSS feeds (PubMed, Beckers, Google News), competitor web pages (Anthropic, Perplexity, Google), and the HuggingFace model API
2. **Store** — Persists articles in H2/PostgreSQL and indexes them as vector embeddings for semantic search
3. **Summarize** — Uses Spring AI (Claude or OpenAI) to generate concise, topic-grouped newsletter sections with attributed sources
4. **Research** — Staged research pipeline combining Perplexity API + DB articles, with planning, retrieval, citation assembly, and AI synthesis
5. **Evaluate** — LLM-as-judge prompt evaluation scoring across 5 dimensions with A/B variant comparison
6. **Deliver** — Emails the formatted newsletter (HTML + plain-text) to all active subscribers
7. **Export** — NotebookLM-compatible article exports with HTML summaries grouped by source

## Architecture

The project follows **hexagonal architecture** (ports and adapters), keeping the domain layer framework-free and all infrastructure concerns pluggable:

```
web (controllers + Thymeleaf)  -->  application (use cases)  -->  domain (models + ports)
                                                                       ^
                        infrastructure/* (adapters) -------------------+
                        - ai/          Spring AI adapter (summarize, evaluate, report)
                        - ingestion/   RSS, web scraping, HuggingFace, Perplexity
                        - persistence/ JPA entities, repositories, storage adapters
                        - delivery/    Email (JavaMailSender) + NotebookLM export
                        - research/    Perplexity + legacy Google research adapters
```

Swapping the AI provider, database, or delivery mechanism requires no domain changes — only a new adapter.

## Tech Stack

| Component         | Technology                                         |
|-------------------|----------------------------------------------------|
| Framework         | Spring Boot 3.4.5, Java 17                         |
| AI                | Spring AI 1.0.0 (Anthropic Claude / OpenAI)        |
| Relational DB     | H2 (dev) / PostgreSQL 16 (prod)                    |
| Vector Store      | PGVector (PostgreSQL extension)                    |
| RSS Parsing       | Rome 2.1.0                                         |
| Web Scraping      | Jsoup 1.18.3                                       |
| Document Parsing  | PDFBox 3.0.3, POI-OOXML 5.3.0                     |
| Email (dev)       | MailHog (SMTP trap)                                |
| Email (prod)      | Amazon SES                                         |
| UI                | Thymeleaf (server-side rendered)                   |
| Build             | Maven                                              |
| Testing           | JUnit 5 + AssertJ + Mockito (475 tests)            |

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
  -e POSTGRES_USER=**** \
  -e POSTGRES_PASSWORD=**** \
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
```

### 3. Build and Run

```bash
# Build and run tests
mvn verify

# Start the application
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

## Web UI (Thymeleaf)

| URL | Description |
|-----|-------------|
| `/dashboard` | Analytics overview — ingestion stats, run history |
| `/dashboard/articles` | Article list with topic filter and sort |
| `/dashboard/news` | Articles grouped by 9 configurable topic sections |
| `/research/compare` | Side-by-side LEGACY_GOOGLE vs STAGED_RESEARCH results |
| `/research/runs` | Research run history table |
| `/research/runs/{runId}` | Research run detail |
| `/research/vendors` | Vendor comparison card grid with strengths/weaknesses |

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
| POST | `/api/v1/documents/ingest` | Ingest documents for RAG |
| POST | `/api/v1/market-intelligence/refresh` | Trigger market intelligence report |
| GET | `/api/v1/analytics/ingestion` | Ingestion analytics |
| GET | `/api/v1/analytics/runs` | Newsletter run analytics |
| GET | `/api/v1/analytics/evaluations` | Evaluation analytics |
| POST | `/monitoring/harvest` | Trigger web page harvest |
| POST | `/monitoring/huggingface` | Trigger HuggingFace model discovery |

## Scheduled Jobs

| Job | Default Schedule | Description |
|-----|-----------------|-------------|
| Feed Harvesting | Daily (ACADEMIC/REGULATORY) + every 4h (INDUSTRY) | RSS harvest → DB |
| Web Monitoring | Daily 07:00 UTC (competitors) + 07:30 (HuggingFace) | Web scrape + model discovery |
| Embedding | Daily at midnight | Indexes articles into vector store |
| Newsletter Generation | Mondays at 8:00 AM | Ingest → generate → deliver |
| Market Intelligence | 1st of month, 8 AM | AI-generated market report |
| Research Harvest | Daily 06:00 UTC | COMBINED pipeline per topic |

Schedules are configurable via `application.yml` cron expressions.

## Configuration

### Profiles

- **dev** (`application-dev.yml`) — MailHog on `localhost:1025`
- **prod** (`application-prod.yml`) — Amazon SES with STARTTLS

## Testing

475 tests across 59 test classes — all pass with no live AI or network calls.

```bash
# Run all unit tests (no AI calls, uses H2 in-memory DB)
mvn test

# Run AI integration smoke tests (requires valid API key)
mvn test -Dspring.profiles.active=ai-integration
```

## Project Structure

```
AIHealthcare/
├── application/src/main/java/com/wgblackmon/aihealthcare/
│   ├── domain/
│   │   ├── model/           # NewsArticle, Topic, NewsletterDraft, Subscriber, ResearchAnswer...
│   │   ├── port/inbound/    # Use-case interfaces (12 inbound ports)
│   │   ├── port/outbound/   # Port interfaces (14 outbound ports)
│   │   ├── service/         # Domain services (Newsletter, Research, Evaluation, Delivery)
│   │   └── exception/       # Domain exceptions
│   ├── infrastructure/
│   │   ├── ai/              # Spring AI adapters (summarize, evaluate, report)
│   │   ├── config/          # AppConfig, bean wiring
│   │   ├── delivery/        # EmailDeliveryAdapter, NotebookLMService
│   │   ├── ingestion/       # RSS, web scraping, HuggingFace, document parsing
│   │   ├── persistence/     # JPA entities, repositories, storage adapters
│   │   ├── research/        # Perplexity + legacy Google research adapters
│   │   └── scheduler/       # 6 scheduled jobs
│   └── web/
│       ├── controller/      # REST + Thymeleaf controllers
│       └── dto/             # Request/response records
├── application/src/main/resources/
│   ├── prompts/             # AI prompt templates (6 templates)
│   └── templates/           # Thymeleaf HTML templates (7 pages)
├── docs/                    # Architecture and conventions documentation
├── pom.xml
└── CLAUDE.md                # AI assistant project context
```

## Author

**Bill Blackmon**

## License

This project is proprietary. All rights reserved.
