# AIHealthcare — Coding Conventions

> **This file is referenced by `CLAUDE.md` and must be kept up to date.**
> All rules here apply to every Java file in this project unless explicitly noted otherwise.
> Last updated: 2026-09-02 | Author: Bill Blackmon

---

## 1. Class Header Comments

Every Java class, interface, enum, and record **must** include a Javadoc block at the top
with the following fields:

```java
/**
 * <Short one-line description of what this type is.>
 *
 * <Longer explanation of purpose, responsibilities, and any important
 * design decisions. Include what this type does NOT do if relevant.>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   YYYY-MM-DD  (date this file was first created)
 * @updated YYYY-MM-DD  (date of last meaningful change — update this on every edit)
 */
```

**Rules:**
- `@since` is set once at creation and never changed.
- `@updated` is revised every time the file is meaningfully edited.
- The description must explain *purpose and functionality*, not just restate the class name.
- Records and interfaces follow the same convention.

### Example

```java
/**
 * Immutable domain record representing a single article discovered
 * during a newsletter ingestion run.
 *
 * Holds the article's title, source URL, extracted body text, the search
 * topic that led to its discovery, and an optional publication date.
 * This record is the primary input to {@link AiSummarizationPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2025-01-27
 */
public record NewsArticle(...) { }
```

---

## 2. Per-Method Logging

Every concrete method (public, protected, and package-private) **must**:
1. Log all input arguments on the **first line** of the method body.
2. Log the return value on the line **immediately before every `return` statement**.
3. Log `return=void` before the end of void methods.

This enables full call tracing during development with zero overhead when `DEBUG` is off,
since SLF4J's `{}` placeholders are lazily evaluated.

**Logging library:** SLF4J API + Logback — included automatically via `spring-boot-starter-web`.
No additional `pom.xml` entry is required.
**Declaration:** Use Lombok's `@Slf4j` annotation on the class — do NOT declare a
`Logger` field manually.

```java
@Slf4j
public class NewsletterService {

    // Non-void method — log all args on entry, log result before every return
    public NewsletterDraft generate(String runId, String draftId,
                                    String title, NewsletterTone tone,
                                    int maxSectionsPerTopic) {
        log.debug("generate() | runId={}, draftId={}, title={}, tone={}, maxSectionsPerTopic={}",
                  runId, draftId, title, tone, maxSectionsPerTopic);

        // ... business logic ...

        NewsletterDraft result = new NewsletterDraft(...);
        log.debug("generate() | return={}", result);
        return result;
    }

    // Void method — log args on entry, log return=void before end of method
    public void archive(String draftId) {
        log.debug("archive() | draftId={}", draftId);

        // ... business logic ...

        log.debug("archive() | return=void");
    }
}
```

**Rules:**
- Entry format: `log.debug("methodName() | param1={}, param2={}", val1, val2)`
- Exit format: `log.debug("methodName() | return={}", result)` — immediately before `return`
- Void exit format: `log.debug("methodName() | return=void")` — last line before method ends
- Use `log.debug()` for all entry/exit tracing.
- Use `log.info()` for significant business events (e.g. "Draft generated", "Ingestion complete").
- Use `log.warn()` for recoverable unexpected conditions.
- Use `log.error()` for caught exceptions that affect the outcome.
- Never log sensitive data (PII, credentials) — use `"[REDACTED]"` as a placeholder if the
  parameter must appear in the trace.
- Interfaces and abstract methods do not get log statements (only concrete implementations).
- Records with compact constructors may log validation failures at `log.warn()`.

---

## 3. Looping — Streams Allowed Going Forward

As of 2026-09-02, Java Streams (`stream()`, `.map()`, `.filter()`, `.collect()`, etc.)
are permitted in **new code**. This is a going-forward change only — existing `for`
loops are **not** being retroactively converted. Do not rewrite an existing loop to a
stream as a drive-by change while touching nearby code; only use streams when writing
new iteration/transformation logic.

**Original rationale (still applies — choose accordingly):** explicit `for` loops are
easier to step through in a debugger and simpler to attach `log.debug()` tracing to
(see Section 2, Per-Method Logging). Prefer a `for` loop when the loop body needs
per-iteration log statements, breakpoints, or non-trivial branching. Streams are fine
for simple, self-contained transformations that don't need step-through debugging.

### Example

```java
// Both are acceptable in new code
List<String> ids = new ArrayList<>();
for (NewsArticle article : articles) {
    ids.add(article.articleId());
}

List<String> ids = articles.stream()
    .map(NewsArticle::articleId)
    .collect(Collectors.toList());
```

---

## 4. Records for Data Mapping

Java `record` types **must** be used wherever a class exists solely to carry data
(DTOs, value objects, results, parameters). Do not use plain classes with fields,
getters, and setters for this purpose.

**Use records for:**
- Domain model types (`NewsArticle`, `NewsletterSection`, `NewsletterDraft`)
- Internal method result types that return multiple values
- Test fixture types
- Any type that is effectively immutable and has no behaviour beyond validation

**Do not use records for:**
- Spring `@Service`, `@Component`, `@Controller` classes (these are stateful beans)
- Classes with mutable state
- Classes that need inheritance

```java
// CORRECT — a result type carrying multiple values
public record IngestResult(String runId, int articleCount, List<String> topics) {}

// WRONG — don't use a plain class for this
public class IngestResult {
    private String runId;
    private int articleCount;
    // getters/setters...
}
```

---

## 5. Dependency Injection

Constructor injection **only**. Never use `@Autowired` field injection or setter injection.

```java
// CORRECT
public class NewsletterService {
    private final ArticleIngestionPort ingestionPort;
    private final AiSummarizationPort  summarizationPort;

    public NewsletterService(ArticleIngestionPort ingestionPort,
                             AiSummarizationPort summarizationPort) {
        this.ingestionPort     = ingestionPort;
        this.summarizationPort = summarizationPort;
    }
}
```

---

## 6. Domain Module Purity

The `domain` module must have **zero** dependencies on Spring, Spring AI, Lombok,
or any infrastructure library. It may only use the JDK.

Lombok's `@Slf4j` is therefore only available in `application`, `infrastructure/*`,
and `web` modules. Domain records may include validation in their compact constructors
but must use `System.Logger` (JDK built-in) if logging is needed — or omit logging
entirely (preferred for pure domain records).

---

## 7. Slice Discipline

No code is written that is not required by the current slice. Specifically:
- No persistence (JPA/DB) until Slice 2.
- No auth/security until explicitly requested.
- No speculative abstractions.
- Every slice must be fully green (unit tests passing) before the next begins.
