# AIHealthcare — Architecture Reference

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
- `web` → depends on `application` + `api` (generated DTOs); uses Spring MVC
- `api` → OpenAPI Generator output only; no hand-written code

---

## Slice Strategy
Each slice delivers one end-to-end vertical: spec → domain → application → infrastructure stub → web controller → test.
No slice is "done" until all layers compile and unit tests are green.

### Slice 1 — Ingest & Summarize
Single flow: accept topics → fetch articles → AI summarize → return `NewsletterDraft`.
All state is in-memory (no DB). AI calls are behind a port — mocked in unit tests.

### Slice 2 (planned) — Persistence
Introduce JPA + H2/Postgres. `NewsArticle`, `NewsletterDraft` persisted.
`RunId` becomes a proper entity. Retrieve draft by ID from DB.

### Slice 3 (planned) — Scheduling + Delivery
`@Scheduled` weekly trigger. Email delivery adapter. Unsubscribe/manage list.

---

## AI Integration Pattern
Spring AI `ChatClient` is wrapped by `AiSummarizationAdapter` (in `infrastructure/ai`).
The adapter implements `AiSummarizationPort` from `domain`.
Unit tests inject a `MockAiSummarizationPort` — no real AI calls outside `@Profile("ai-integration")`.

Prompt template location: `infrastructure/ai/src/main/resources/prompts/summarize-articles.st`

---

## Article Ingestion Pattern
`ArticleIngestionAdapter` (in `infrastructure/ingestion`) implements `ArticleIngestionPort`.
For Slice 1 it accepts a list of URLs or performs a simple HTTP fetch.
Future: RSS feed reader, news API integration, configurable source registry.

---

## OpenAPI → Code Flow
1. Edit `api/src/main/resources/openapi.yaml`
2. Run `mvn generate-sources -pl api`
3. Generated interfaces land in `api/target/generated-sources/`
4. `web` controllers implement the generated delegate interfaces
5. Never edit generated files by hand

---

## Testing Pyramid
| Layer        | Test type          | AI calls? | Profile needed      |
|--------------|--------------------|-----------|---------------------|
| domain       | Pure unit          | No        | none                |
| application  | Unit + mock ports  | No (mock) | none                |
| web          | MockMvc slice      | No (mock) | none                |
| infrastructure/ai | Smoke test    | Yes       | `ai-integration`    |

---

## Package Naming Convention
```
com.example.aihealthcare.domain.model
com.example.aihealthcare.domain.port.inbound
com.example.aihealthcare.domain.port.outbound
com.example.aihealthcare.application.usecase
com.example.aihealthcare.application.service
com.example.aihealthcare.infrastructure.ai
com.example.aihealthcare.infrastructure.ingestion
com.example.aihealthcare.infrastructure.delivery
com.example.aihealthcare.web.controller
```
