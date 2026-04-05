# AIHealthcare — Claude Code Project Memory

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
| Record / Interface       | Package                              | Notes                              |
|--------------------------|--------------------------------------|------------------------------------|
| `NewsArticle`            | `domain.model`                       | Scraped article + source URL       |
| `NewsletterSection`      | `domain.model`                       | AI-summarized section for one topic|
| `NewsletterDraft`        | `domain.model`                       | Full draft: intro + sections + sources |
| `ArticleIngestionPort`   | `domain.port.outbound`               | Fetch articles for a topic/keyword |
| `AiSummarizationPort`    | `domain.port.outbound`               | Summarize articles via AI          |
| `NewsletterGenerationPort` | `domain.port.outbound`             | Orchestrate draft generation       |
| `IngestArticlesUseCase`  | `application.usecase`                | Inbound port — drive ingestion     |
| `GenerateNewsletterUseCase` | `application.usecase`             | Inbound port — drive generation    |

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

## Current Slice
**Slice 1 — Ingest & Summarize (single vertical)**
- [x] `pom.xml` (Spring Boot 3.4.5, Spring AI 1.0.0, Lombok, JUnit 5 + Mockito)
- [x] Domain records: `NewsArticle`, `NewsletterSection`, `NewsletterDraft`
- [x] Port interfaces: `ArticleIngestionPort`, `AiSummarizationPort`
- [x] Use cases: `IngestArticlesUseCase`, `GenerateNewsletterUseCase`
- [x] Application service: `NewsletterService` (in-memory, no persistence)
- [x] Unit tests — 35 passing (domain record validation + mock-AI pattern)
- [ ] Spring AI adapter stub (`infrastructure/ai` — `AiSummarizationAdapter`)
- [ ] Web controller wired to use cases

---

## What NOT to Do
- Do not add persistence (JPA/DB) until Slice 2 — use in-memory maps for now.
- Do not add auth/security until explicitly requested.
- Do not modify `openapi.yaml` without confirming the change first.
- Do not place business logic in controllers or adapters.
- Do not use `@Autowired` field injection — constructor injection only.
