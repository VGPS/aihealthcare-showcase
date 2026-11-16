# AIHealthcare — Claude Code Project Memory

## What This Project Is
A Spring Boot application that scrapes AI-in-Healthcare articles from the web weekly,
summarizes them with Spring AI, and produces a formatted newsletter draft with attributed sources.

See @docs/architecture.md for full module layout and design decisions.
See @docs/CONVENTIONS.md for all coding rules (logging, loops, records, class headers).

---

## LLM Behavioral Guidelines
> Adapted from [Karpathy guidelines](CLAUDE_karpathy.md). These complement the project-specific conventions below.

### Think Before Coding
- State assumptions explicitly. If uncertain, ask before implementing.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.

### Simplicity First
- No features beyond what was asked. No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- If you write 200 lines and it could be 50, rewrite it.

### Surgical Changes
- Don't "improve" adjacent code, comments, or formatting — touch only what you must.
- Don't refactor things that aren't broken. Match existing style.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked — mention it instead.
- Every changed line should trace directly to the user's request.

### Goal-Driven Execution
- Transform tasks into verifiable goals (e.g. "write a test that reproduces the bug, then fix it").
- For multi-step tasks, state a brief plan with verification checks per step.

### Session Cost Discipline
- **One task per session.** Unrelated work goes in a new session after `/clear`.
- **Two failed attempts = stop.** After two fix attempts that don't work, pause and state: what is known for certain vs. what is being guessed. Do not iterate a third time without a new hypothesis.
- **No unfiltered large output.** Never pipe full log files or `mvn dependency:tree` into context. Grep for the relevant signal first (e.g. `grep ERROR app.log | tail -20`).
- **One rebuild per hypothesis.** Don't rebuild the app to verify the same theory twice.
- **State the task upfront.** If the user hasn't written "The task is X. Done when Y," ask before starting exploratory work.
- **Earn the next step.** Don't read a file, run a command, or spawn a subagent unless the result will directly inform the next action. No speculative exploration.

---

## Production LLM Cost Discipline

> Governs the **deployed app's own** API spend (Anthropic/Perplexity/Gemini/Alpaca), not Claude Code sessions.
> Initiative started 2026-11-02, targeting a 50% cut vs. October baseline.

See `docs/llm-cost-discipline.md` for implemented rules, candidates, and verification approach.

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

See `docs/domain-model.md` for the full domain type table, `NewsArticle` field inventory, wiki records, and feed YAML shape.

Key types to know without looking up:
- `NewsArticle` — harvested article, 11 fields, in `domain.model`
- Port interfaces in `domain.port.outbound`; use-case interfaces in `domain.port.inbound`
- Base package: `com.wgblackmon.aihealthcare.*`

---

## Development Conventions
- **Language**: Java 21 (LTS), records for all immutable data types. EC2 runs Amazon Corretto 21.
- **Build**: Maven single-module; run `mvn verify` from root to build.
- **Testing**: JUnit 5 + AssertJ + Mockito. AI smoke tests are `@Profile("ai-integration")` only.
- **Spec-Driven**: Always update `openapi.yaml` before implementing an endpoint.
- **Incremental slices**: Build one vertical slice at a time; each slice must be green before starting the next.
- **No orphan code**: Don't create classes, methods, or fields not yet required by the current slice.
- **Streams**: Permitted in new code as of 2026-09-02. Existing `for` loops are not being retroactively converted.
- **Logging**: Lombok `@Slf4j` on every concrete class. First line logs all args: `log.debug("methodName() | param={}", val)`. Last line before every `return` logs the result. Void methods log `return=void`.
- **Class headers**: Every file must have a Javadoc block with `@author Bill Blackmon`, `@since`, and `@updated` fields.
- **DI**: Constructor injection only — no `@Autowired` field injection.
- **Domain purity**: `domain` module has zero Spring/Lombok/AI imports — JDK only.

---

## Key Commands
```bash
# Build and verify
mvn verify

# Build the way CI does (skips Windows Tailwind binary)
mvn -B -ntp -Pci verify

# Run the app locally
mvn spring-boot:run

# Run unit tests (no AI calls)
mvn test

# Run AI integration smoke tests
mvn test -Dspring.profiles.active=ai-integration
```

---

## Git and CI Discipline

- **Commit directly to `master`** — no feature branches required (solo developer).
- **One feature per commit.** If a change does two things, make two commits.
- **Commit message format:** imperative subject under 72 chars. Body only when the "why" isn't obvious.
- **Git remote is `AIHealthcare_Origin`, NOT `origin`.** Push with: `git push AIHealthcare_Origin master`.
- **Never push directly to the `showcase` remote.** It's a public mirror that strips sale/outreach-strategy files before publishing. Run `bash scripts/sync-showcase.sh` instead — it fetches, resets, strips, and pushes atomically. A `PreToolUse` hook in `.claude/settings.json` blocks any other command touching `showcase` + push/`--force`. (A direct `git push showcase master` leaked `docs/Acquire_notes.md` into the public repo on 2026-11-16 — see `feedback_showcase_mirror` memory for the incident history.)
- **Before pushing, run:** `mvn -Pci verify`
- **Never commit** `.env`, `*.key`, `*.pem`, dump files, or logs.
- **CI workflow:** `.github/workflows/ci.yml` — triggers on pushes to `master`. Runs with `-Pci` profile (skips Tailwind), pgvector Postgres service container, Java 21 Temurin.

---

## Testing Strategy — Selective Test Execution

**Do NOT run the full test suite (`mvn test`) on every code change.** The suite has 3,000+ tests
and takes 30+ minutes. Run only tests relevant to the changed code.

### Rules
1. **After modifying code**, run only the test classes whose production counterparts changed:
   ```bash
   mvn test -Dtest="FooServiceTest,BarControllerTest"
   ```
2. **Mapping changed files to tests**: for each modified `.java` file, find its test counterpart.
   - `FooService.java` → `FooServiceTest.java`
   - `FooController.java` → `FooControllerTest.java`
   - `FooAdapter.java` → `FooAdapterTest.java`
3. **Template/CSS/YAML changes**: no test run needed unless a controller was also changed.
4. **Full suite**: run `mvn test` only when explicitly asked, pre-release, or for cross-cutting changes.
5. **If no test counterpart exists** for a changed file, skip testing for that file.
6. **Report** which tests were run and their pass/fail count.

---

## Current Slice — W7 (Wiki Hybrid Search) — COMPLETE

See `docs/slice-history.md` for the full W7 checklist and all previously completed slices.

**W7 summary:** `WikiQueryAdapter.findRelevantPages()` now merges keyword/LIKE hits with a
pgvector semantic-similarity leg for non-blank queries. `WikiEmbeddingScheduler` (new) embeds
wiki pages into the shared `PgVectorStore` (tagged `type=wiki_page`). Blank-query contract
(used by `WikiLintScheduler` / `WikiLintController`) is unchanged. 75 selective tests passing.

---

## Feature Module Specs

See `docs/feature-specs.md` for detailed architectural specs on:
- **Market Analysis** — daily digest, qualifying bar, Alpaca/Perplexity adapters, 3-phase build order
- **State Health-AI Legislation Registry** — seed data, weekly monitor, candidate review workflow
- **Enterprise Data PULL (ED-1)** — async jobs, `ConfinedFileStore`, `RemoteEndpointGuard`, 6 security invariants
- **Enterprise Data PUSH (ED-2)** — per-minute DB sweeper, atomic claim, HMAC signed links

---

## LLM Wiki Layer

The wiki is a persistent, LLM-compiled knowledge base providing longitudinal context.

Three ownership layers:
1. **Immutable sources** — `NewsArticle` records. Never mutated by the wiki layer.
2. **LLM-owned wiki** — `WikiPage` records compiled by `KnowledgeCompilationPort`.
3. **Query layer** — `WikiQueryPort` decouples newsletter drafting from compilation.

Domain records: `WikiPageType`, `SourceRef`, `WikiPage`, `Contradiction`, `CompilationReport` in `domain.model`.
Ports: `KnowledgeCompilationPort`, `WikiQueryPort` in `domain.port.outbound`.

---

## LinkedIn Group Post Convention

See @docs/linkedInRules.md for all LinkedIn post and newsletter formatting rules,
including: NO API CHARGES constraint, Anti-Slop lint requirement, combined output
file format, First Comment link order, Data Provenance format, and known conflicts.

---

## What NOT to Do
- Do not add auth/security until explicitly requested.
- Do not modify `openapi.yaml` without confirming the change first.
- Do not place business logic in controllers or adapters.
- Do not use `@Autowired` field injection — constructor injection only.
- Do not put outbound port interfaces in `infrastructure` packages — they belong in `domain.port.outbound`.
- Do not use `com.aihealthcare.*` as the base package — all code uses `com.wgblackmon.aihealthcare.*`.
