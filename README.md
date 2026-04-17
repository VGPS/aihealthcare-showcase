# AIHealthcare

An automated AI-powered newsletter platform that discovers, summarizes, and delivers the latest artificial intelligence in healthcare news — built with Spring Boot and Spring AI.

## What It Does

AIHealthcare runs an end-to-end pipeline on a configurable schedule:

1. **Harvest** — Scrapes articles from RSS feeds across academic (PubMed), regulatory, and industry sources
2. **Store** — Persists articles in PostgreSQL and indexes them as vector embeddings in PGVector for semantic search
3. **Summarize** — Uses Spring AI (Claude or OpenAI) to generate concise, topic-grouped newsletter sections with attributed sources
4. **Deliver** — Emails the formatted newsletter (HTML + plain-text) to all active subscribers

## Architecture

The project follows **hexagonal architecture** (ports and adapters), keeping the domain layer framework-free and all infrastructure concerns pluggable:

```
web (controllers)  -->  application (use cases)  -->  domain (models + ports)
                                                          ^
                        infrastructure/* (adapters) ------+
                        - ai/          Spring AI adapter
                        - ingestion/   RSS feed harvester
                        - persistence/ PostgreSQL + PGVector
                        - delivery/    Email (JavaMailSender)
```

Swapping the AI provider, database, or delivery mechanism requires no domain changes — only a new adapter.

## Tech Stack

| Component         | Technology                                         |
|-------------------|----------------------------------------------------|
| Framework         | Spring Boot 3.4.5, Java 17                         |
| AI                | Spring AI 1.0.0 (Anthropic Claude / OpenAI)        |
| Relational DB     | PostgreSQL 16                                      |
| Vector Store      | PGVector (PostgreSQL extension)                    |
| RSS Parsing       | Rome 2.1.0                                         |
| Email (dev)       | MailHog (SMTP trap)                                |
| Email (prod)      | Amazon SES                                         |
| Build             | Maven                                              |
| Testing           | JUnit 5 + AssertJ + Mockito (137 tests)            |

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

## API Endpoints

| Method | Endpoint                        | Description                      |
|--------|---------------------------------|----------------------------------|
| GET    | `/api/v1/articles?topic=&limit=`| List harvested articles          |
| GET    | `/api/v1/runs`                  | List all newsletter runs         |
| GET    | `/api/v1/runs/{runId}`          | Get a specific newsletter run    |
| POST   | `/api/v1/subscribers`           | Subscribe an email address       |
| GET    | `/api/v1/subscribers`           | List all subscribers             |
| DELETE | `/api/v1/subscribers?email=`    | Unsubscribe an email address     |
| POST   | `/api/v1/newsletter/deliver`    | Trigger newsletter delivery      |

## Scheduled Jobs

| Job                    | Default Schedule     | Description                                    |
|------------------------|----------------------|------------------------------------------------|
| Feed Harvesting        | Daily / every 4 hours| Scrapes RSS feeds (academic daily, industry 4h)|
| Embedding              | Daily at midnight    | Indexes articles into the vector store         |
| Newsletter Generation  | Mondays at 8:00 AM   | Full pipeline: ingest, summarize, deliver      |

Schedules are configurable via `application.yml` cron expressions.

## Configuration

### Profiles

- **dev** (`application-dev.yml`) — MailHog on `localhost:1025`
- **prod** (`application-prod.yml`) — Amazon SES with STARTTLS

## Testing

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
│   │   ├── model/           # NewsArticle, Topic, NewsletterDraft, Subscriber
│   │   ├── port/inbound/    # Use-case interfaces
│   │   ├── port/outbound/   # Port interfaces (implemented by adapters)
│   │   └── service/         # NewsletterRenderer, NewsletterService, DeliveryService
│   ├── infrastructure/
│   │   ├── ai/              # Spring AI adapter, EmbeddingScheduler, VectorStoreAdapter
│   │   ├── config/          # AppConfig, bean wiring
│   │   ├── delivery/        # EmailDeliveryAdapter
│   │   ├── ingestion/       # RomeFeedHarvester, FeedHarvestScheduler
│   │   ├── persistence/     # JPA entities, repositories, storage adapters
│   │   └── scheduler/       # NewsletterGenerationScheduler
│   └── web/
│       ├── controller/      # REST controllers
│       └── dto/             # Request/response records
├── docs/                    # Architecture and conventions documentation
├── pom.xml
└── CLAUDE.md                # AI assistant project context
```

## Author

**Bill Blackmon**

## License

This project is proprietary. All rights reserved.
