---
name: hexagonal-architecture
description: Use when making any architectural decision about where a class belongs, how ports and adapters relate, or when reviewing module boundaries in the AIHealthcare project.
---

# Hexagonal Architecture Skill

## Layer Rules

| Module | Allowed Dependencies | Forbidden |
|---|---|---|
| `domain` | Java stdlib only | Spring, JPA, any framework |
| `application` | `domain` only | Infrastructure, Spring MVC |
| `infrastructure/*` | `domain`, `application`, Spring | Direct domain mutation |
| `web` | `application`, Spring MVC | Direct domain/infra access |
| `api` | OpenAPI Generator output | Business logic |

## Port Types

**Driving (inbound) ports** — called by `web`, defined in `application` or `domain`:
```java
// application/src/main/java/.../NewsletterApplicationService.java
public interface NewsletterUseCase {
    NewsletterDraft generateDraft(DraftRequest request);
}
```

**Driven (outbound) ports** — defined in `domain`, implemented in `infrastructure`:
```java
// domain/src/main/java/.../NewsletterAiPort.java
public interface NewsletterAiPort {
    NewsletterSection summarise(List<NewsArticle> articles);
}

// infrastructure/ai/src/.../NewsletterAiAdapter.java
@Component
public class NewsletterAiAdapter implements NewsletterAiPort { ... }
```

## Package Naming
```
com.aihealthcare.domain           — records, port interfaces
com.aihealthcare.application      — use cases, application services
com.aihealthcare.infrastructure   — adapters (ai, ingestion, delivery)
com.aihealthcare.web              — REST controllers
com.aihealthcare.api              — generated OpenAPI stubs
```

## Common Mistakes to Avoid
- ❌ Injecting `ChatClient` directly into an application service
- ❌ Returning JPA `@Entity` from a domain port
- ❌ Using `@Autowired` in `domain` module classes
- ✅ Application service depends only on port interfaces, injected via constructor
