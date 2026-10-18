# PC-2 Plan: Per-Pipeline LLM Token Usage Tracking

## Problem

After the 2026-10-18 Anthropic spend-limit incident, there was no way to see which
scheduled pipeline was consuming tokens — not in the app, not without SSH-ing into
EC2 and grepping logs. The Anthropic Console shows aggregate spend by model, but
not by pipeline, so there was no way to decide which specific lever (frequency,
model tier, context size, pause) to pull.

## What This Is NOT

- Not prompt caching (see PC-1 — deferred, no template clears the cache minimum
  for its model).
- Not a billing/invoicing system — estimates only, from a configurable local
  pricing table, not a replacement for the Anthropic Console.
- Not tracking non-Spring-AI providers — Perplexity and Gemini adapters call their
  REST APIs directly (not through `ChatClient`), so their usage is not captured
  here. This only covers calls made through the shared default `ChatClient.Builder`
  (Anthropic, by `@Primary` wiring).

## Design

### Domain (`domain.model`, `domain.port.outbound`, `domain.service`)

| Type | Purpose |
|------|---------|
| `LlmCallUsage` | One row per completed LLM call — pipelineId, model, token counts, timestamp |
| `ModelPricing` | `$/1M` input/output rate for one model |
| `LlmUsageSummary` | Aggregation result — key (model/pipeline/day), call count, tokens, estimated cost |
| `LlmCallUsagePort` | `save(usage)`, `findSince(instant)` |
| `LlmUsageSummaryService` | Pure Java aggregation: `summarizeByModel/ByPipeline/ByDay` + cost estimation from an injected pricing map; unpriced models report $0, never a guessed rate |

### Infrastructure

| Class | Package | Purpose |
|-------|---------|---------|
| `PipelineContext` | `infrastructure.scheduler` | Static `ThreadLocal<String>` — the currently-executing pipeline id. `UNTAGGED = "interactive"` default. |
| `TokenUsageLoggingAdvisor` | `infrastructure.ai` | Spring AI `CallAdvisor` — reads `ChatResponse.getMetadata().getUsage()` after every call, tags with `PipelineContext.get()`, persists via `LlmCallUsagePort`. Skips Spring AI's `EmptyUsage` placeholder (returns 0 for every field rather than null) so calls with genuinely no usage data don't pollute call counts. Never breaks the underlying call — persistence failures are caught and logged. |
| `LlmCallUsageEntity`/`Repository`/`Adapter` | `infrastructure.persistence` | JPA-backed `LlmCallUsagePort`, table `llm_call_usage` |
| `LlmPricingProperties` | `infrastructure.config` | `@ConfigurationProperties(prefix="aihealthcare.llm-pricing")` — mutable YAML-bound POJO, converted to the immutable pricing map in `AppConfig` |

### Wiring

`AppConfig` registers:
- `TokenUsageLoggingAdvisor` bean
- A `ChatClientCustomizer` bean (`builder -> builder.defaultAdvisors(advisor)`) — Spring AI invokes every `ChatClientCustomizer` before injecting the auto-configured `ChatClient.Builder`, so this covers all ~15 Anthropic adapters with zero per-adapter changes
- `LlmUsageSummaryService` bean, wired from `LlmPricingProperties`

### Pipeline tagging — where `PipelineContext.set()/clear()` was added

Two trigger paths exist for most pipelines (cascade + the pipeline's own standalone
cron), both needed tagging for accurate attribution:

- `StartupPipelineOrchestrator.runStep()` — single choke point for all 16 cascade
  steps, tagged via the existing `toPipelineId(stepName)` mapping. Added a case for
  `"Deal signal detection (standalone)"` → `deal-signals` (the standalone cron's
  step name didn't match the cascade step's name, which would have fallen through
  to an inconsistent auto-generated id).
- Standalone schedulers whose own cron bypasses the orchestrator (confirmed by
  reading live EC2 logs during the 2026-10-18 incident — `LegalTrendScheduler`
  fired independently of the cascade on the same day): `LegalTrendScheduler`,
  `MarketAnalysisScheduler.runDailyDigest()`, `TrendDetectionScheduler`,
  `NewsletterGenerationScheduler`, `ResearchHarvestScheduler`, and three private
  methods inside `FeedHarvestScheduler` (`generateTopicSummaries`,
  `compileWikiPages`, `analyzeWikiGaps`) that run on every harvest cycle (daily +
  every 4h) and aren't cascade steps at all.
- Deliberately NOT tagged: `WebMonitoringScheduler`, `RegulatoryHarvestScheduler`'s
  own cron, `ClinicalTrialHarvestScheduler`, `EmbeddingScheduler`,
  `CompanyDiscoveryScheduler`, `WikiLintScheduler`, `OutreachSyncService`,
  `LegislationMonitorScheduler` — none of these call an LLM through `ChatClient`
  (REST APIs, deterministic checks, or the Embeddings API, not Chat), so tagging
  them would set context that's never read.

### Admin UI

`AdminPipelineController.pipelines()` loads the last 7 days of usage via
`LlmCallUsagePort.findSince()`, summarizes by pipeline and by model via
`LlmUsageSummaryService`, and adds them to the model. `admin-pipelines.html` gets
a new "LLM Token Usage (Last 7 Days)" panel — two side-by-side tables (by pipeline,
by model), sorted most-expensive-first, plus a total call/token/cost line.

## Fixed in passing

`MarketAnalysisSchedulerTest` was missing a `@Mock PipelineHealthService
healthService` field — a pre-existing gap from an earlier commit
(`001a18a5`, "pipeline last-run tracking for all 7 schedulers") that added
`healthService.recordRun(...)` calls to the scheduler without updating this test.
It was silently broken (NPE on every test method) before this slice; added the
missing mock since it was blocking verification of this slice's changes.

## Test Targets (actual)

| Test class | Count | What it covers |
|---|---|---|
| `LlmUsageSummaryServiceTest` | 7 | Per-model/pipeline/day aggregation, cost math, sort order, unpriced models, null pricing map |
| `LlmCallUsageAdapterTest` | 3 | Save + findSince ordering/filtering |
| `TokenUsageLoggingAdvisorTest` | 6 | Pipeline tagging, untagged fallback, `EmptyUsage` skip, null-safety, persistence-failure isolation |
| `AdminPipelineControllerTest` | +2 (29 total) | New model attributes present; populated-table Thymeleaf branch renders without error |
| Scheduler tests (Legal/Market/Trend/Newsletter/Research/Orchestrator) | 25 | Unaffected by `PipelineContext` wrapping |

## Verification Performed

- `mvn compile` — BUILD SUCCESS after each wiring step
- `mvn test` on all touched/new test classes — 54 tests green
- `mvn package -DskipTests` — JAR builds
- `mvn spring-boot:run` locally — clean boot, `/login` returns 200, no startup errors
- Could not do a real browser check of `/admin/pipelines` (no admin password
  available in this session) — closed the gap by adding a MockMvc test that
  stubs non-empty `LlmUsageSummary` data to exercise the populated `th:each`
  table branch, not just the empty-state branch.
