# Enterprise Remote Data Access — Design & Options

**Project:** AIHealthcare (`C:\workspaces\SpringAIClaude\AIHealthcare`)
**Author:** Bill Blackmon
**Date:** 2026-09-08
**Status:** Design accepted; implementation split into two slices — **ED-1 (PULL)** and **ED-2 (PUSH)**
**Audience:** developers implementing or reviewing the slices, and Claude Code executing them

---

## 0. How to read this document

This is the *why* document. It explores the options that were on the table for each
architectural decision, states which one was chosen, and gives the reasoning so that a
future reader (or a future slice) can revisit a decision without re-deriving it.

The *what* and *how* live in two companion specs, written in the project's
Spec-Driven Development format and meant to be handed to Claude Code one at a time:

| File | Slice | Scope |
|---|---|---|
| `.claude/plans/ed-1-enterprise-data-pull.md` | **ED-1** | On-demand PULL: feed registry, canned + free-text prompts, async jobs, artifacts, console page, per-job log file |
| `.claude/plans/ed-2-enterprise-data-push.md` | **ED-2** | Timed PUSH: per-customer schedules, DB-sweeper scheduler, email delivery with attachment, size fallback to signed link |

ED-1 must be complete and green before ED-2 begins. ED-2 reuses ED-1's entire execution
core and adds only *when to run* and *where to send*.

> **A note on completeness.** This design was written from the repository's documentation
> (`CLAUDE.md`, `docs/architecture.md`, `.claude/CONVENTIONS.md`, the skills folder), the
> full file/package inventory, `application.yml`, `openapi.yaml`'s surface as documented,
> and the Thymeleaf templates. A tooling limit prevented reading the `.java` sources
> directly. Every slice therefore opens with a **Increment 0 — Reconnaissance** step that
> tells the implementer exactly which existing classes to read and reconcile against
> before writing code. Where this document asserts a signature for an existing type, treat
> it as a strong expectation to verify, not as gospel.

---

## 1. The requirement, restated precisely

ENTERPRISE-tier customers need programmatic and interactive access to AIHealthcare data,
in two directions:

**PULL** — the customer asks a question and gets data back. Two entry modes:
- *Pre-determined prompts* — an admin-curated, parameterised catalogue ("AI legislation
  enacted in {state} during {year}", "FDA 510(k) AI clearances in the last {n} days").
- *Free-text prompts* — the customer types a question in their own words.

**PUSH** — the same request runs on a schedule the customer sets, and the result is
delivered to them without anyone clicking anything.

Behind both, three families of data source were confirmed in scope:

| Family | What it is | Examples in this codebase |
|---|---|---|
| `INTERNAL_CORPUS` | The app's own Postgres/pgvector on EC2 | `news_articles`, `state_laws`, `regulatory_events`, `clinical_trials`, `wiki_pages`, `trend_snapshots`, `deal_signals`, `healthcare_ai_companies` |
| `LLM_SYNTHESIS` | A synthesised, cited answer over that corpus | `AiSearchPort` (Anthropic / OpenAI / Perplexity / Gemini adapters), `ConductAiSearchUseCase`, `CachingAiSearchDecorator` |
| `CUSTOMER_REMOTE` | A system on the customer's side that we reach out to | New in ED-1: outbound HTTPS/JSON only |

And two cross-cutting requirements:
- **A single console page** that triggers both PUSH and PULL and shows results *visually*.
- **An output log file** per run, downloadable, so the feature is testable by a human.

---

## 2. What already exists — the seams we build on

The single most important design constraint is that AIHealthcare is a mature codebase
(~1,100+ tests, 99 controllers, 76 outbound ports). Almost nothing here needs to be
invented; it needs to be *composed*. This table is the inventory of what ED-1 and ED-2
reuse rather than rebuild.

| Need | Existing asset | Package |
|---|---|---|
| Tier gating | `TierGatingService`, `TierLimitProperties`, `SubscriptionTier`, `TierLimits`, `fragments/tier-utils.html` | `domain.service`, `infrastructure.config`, `domain.model` |
| Usage metering | `UsageTrackingPort`, `UsageTrackingAdapter`, `UsageRecordEntity`, `UsageRecordId` | `domain.port.outbound`, `infrastructure.persistence` |
| API-key auth for machine callers | `ApiKeyAuthenticationFilter`, `ApiKeyPort`, `ApiKey`, `ApiKeyController` | `infrastructure.config` |
| Rate limiting | `ApiRateLimitFilter`, `RateLimiter`, `RateLimitResult` | `infrastructure.config`, `domain.model` |
| Async background execution | `PipelineAsyncRunner`, `PipelineRunEvent`, `PipelineRunEventPort`, `PipelineStepStatus`, `PipelineHealthService` | `infrastructure.scheduler` |
| Admin trigger + status page pattern | `AdminPipelineController` + `admin-pipelines.html` | `web.controller`, `templates` |
| Structured corpus queries | `ArticleSearchQueryPort`, `ArticleSpecificationBuilder`, `ArticleSearchCriteria` | `domain.port.outbound`, `infrastructure.persistence` |
| LLM synthesis with citations | `AiSearchPort`, `AiSearchResult`, `AiSearchSynthesis`, `SourceCitation`, `CitationAssembler` | `domain.port.outbound`, `domain.model`, `domain.service` |
| Curated prompt catalogue | `SearchPromptConfig`, `SearchPromptPort`, `SearchPromptController`, `search_prompts` table | `domain.model`, `web.controller` |
| Prompt templates on disk | `PromptLoaderService`, `src/main/resources/prompts/*.txt`, `aihealthcare.prompts.directory` | `infrastructure.config` |
| Export formats | `ExportDataUseCase`, `DataExportService`, `DataExportRequest`, `DataExportResult`, `ExportFormat`, `DataExportController` | `domain.*`, `web.controller` |
| Transactional email | `TransactionalEmailPort`, `TransactionalEmailAdapter`, `EmailDeliveryAdapter`, `NewsletterDeliveryPort` | `domain.port.outbound`, `infrastructure.delivery` |
| Scheduled delivery precedent | `DigestDeliveryScheduler`, `NewsletterAutoSendPort`, `NewsletterSendSettingsEntity` | `infrastructure.scheduler` |
| Push notification precedent | `WebhookChannel`, `WebhookDispatcher`, `WebhookEventType`, `WebhookPayload`, `WebhookNotificationPort`, `webhooks.html` | `domain.model`, `infrastructure.delivery` |
| Log redaction | `LogSanitizer` | `domain.service` |
| Teams / multi-seat accounts | `Team`, `TeamMember`, `TeamRole`, `TeamPort`, `ManageTeamsUseCase` | `domain.model`, `domain.port` |
| Front-end toolkit | HTMX 1.x, Alpine.js, Tailwind, Chart.js (Slice 46), `htmx-csrf.js` | `static/js`, `static/css` |

**Consequence:** ED-1 introduces roughly one new *concept* — a data feed registry — and
otherwise wires existing parts together. That is the design working as intended.

### 2.1 One important existing-code reconciliation

There is already a `DataExport*` family (`ExportDataUseCase`, `DataExportService`,
`DataExportRequest`, `DataExportResult`, `ExportFormat`, `DataExportController`). It is
almost certainly a **synchronous, admin- or subscriber-facing export of a known dataset**.

The enterprise feature is deliberately *not* named `DataExport*` — it is
`EnterpriseData*` — to avoid collision, but the two must not duplicate rendering logic.
The rule for the implementer:

> Reuse `ExportFormat` as the format enum, and reuse whatever CSV/JSON serialisation
> `DataExportService` already contains by extracting it into a shared renderer if
> necessary. Do **not** copy it. If `ExportFormat` lacks a value the enterprise feature
> needs (`NDJSON`, say), add the value to the existing enum rather than creating a
> parallel one.

---

## 3. The core abstraction: a data feed registry

Everything else in this design follows from one decision.

Three source families (`INTERNAL_CORPUS`, `LLM_SYNTHESIS`, `CUSTOMER_REMOTE`) that all
answer the same customer-facing question — *"give me this data"* — should sit behind
**one outbound port with many adapters**, exactly as `AiSearchPort` already has four
adapters (Anthropic, OpenAI, Perplexity, Gemini).

```java
public interface EnterpriseDataSourcePort {
    String        feedId();          // stable id, e.g. "legislation"
    DataSourceKind kind();           // INTERNAL_CORPUS | LLM_SYNTHESIS | CUSTOMER_REMOTE
    DataFeed      describe();        // label, description, formats, parameters, limits
    boolean       supports(DataRequest request);
    DataSet       fetch(DataRequest request, DataJobLog log);
}
```

Spring injects `List<EnterpriseDataSourcePort>` into the domain service; the service
builds a `Map<String, EnterpriseDataSourcePort>` keyed on `feedId()` at construction.
Adding a new feed later — deals, clinical trials, company profiles, a second customer
system — is *one new adapter class and zero changes anywhere else*. That property is the
whole point, and it is what makes the enterprise data product extensible without a slice
per customer request.

### Why not one service per data type?

The alternative was a `LegislationExportService`, a `RegulatoryExportService`, an
`AiAnswerService`, each with its own controller and its own scheduling. Rejected because:

- The customer-facing contract (submit → job → artifact → log) is identical for all of
  them; duplicating it three times triples the surface area for tier checks, quota
  checks, audit and retention — the parts that are easy to get subtly wrong.
- PUSH would then need N schedulers instead of one.
- The console page would need N result renderers.

### Why not a generic "query any table" endpoint?

Because it hands customers a footgun and hands us an injection surface. Feeds are
explicit, curated, and shaped. See §8.

---

## 4. Decision 1 — How does a PULL request travel? *(async job + artifact)*

| Option | How it works | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Synchronous REST** | `POST /api/v1/enterprise/data` blocks, returns the payload in the response body | Trivial to build and to curl; no job table; no polling UI | Dies on anything slow. You already observe 504s from nginx on LLM-heavy endpoints (`goGetRecords` documents this explicitly). A 100k-row CSV or a Perplexity Deep Research call will not survive the proxy. No progress. No retry. No artifact to re-download. | ❌ |
| **B. Async job + artifact** ✅ | `POST` returns `202` + `jobId` immediately; work runs on a bounded executor; the console polls; the result is a stored artifact plus a stored log | Immune to proxy timeouts. Progress is observable. The result is durable and re-downloadable. **PUSH is then the same machinery with a different trigger and a different sink** — ED-2 becomes small. Job rows are the audit trail. | Needs a job table, a status endpoint, a polling UI, a reaper for jobs orphaned by a JVM restart | ✅ **Chosen** |
| **C. Hybrid — sync under a threshold, async above** | Small canned queries answer inline; large ones auto-promote to a job | Best interactive feel | Two code paths, two result renderers, two sets of tests, and the threshold becomes a support question ("why did mine return a job id this time?") | ❌ for now — revisit once real usage patterns exist |
| **D. GraphQL** | Single endpoint, customer composes the shape | Flexible | Adds a whole framework and a whole new authorisation model to a codebase that has neither. Solves a problem enterprise data buyers don't have — they want files and feeds, not ad-hoc graph traversal. | ❌ |
| **E. Read-replica DB credentials** | Hand the customer a read-only Postgres user against an RDS replica | Zero application code | No tier gating, no metering, no audit, no shaping, schema becomes a public contract you can never migrate, and PHI-adjacent tables sit one `SELECT *` away | ❌ |
| **F. S3 manifest + presigned objects** | Nightly dumps to S3; customer polls a manifest | Cheap, scalable, familiar to data teams | Doesn't answer prompts, only publishes fixed extracts. Complements the design; doesn't replace it. | Deferred — natural ED-3 |

**Chosen: B.** The decisive argument is that it makes ED-2 nearly free. A scheduled push
is *a job submitted by a cron sweeper instead of by a person*. One execution core, two
triggers.

---

## 5. Decision 2 — Where does the work actually run? *(in-JVM bounded executor + DB job table)*

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **A. Reuse `PipelineAsyncRunner`** | Already exists; already understood | It is sized and tuned for *your* nightly pipelines. A customer submitting a 100k-row extract at 04:05 would contend with the harvest. Different tenancy, different failure semantics, different back-pressure needs. | ❌ — but *do* emit `PipelineRunEvent` rows for admin visibility |
| **B. Dedicated `ThreadPoolTaskExecutor` + DB job table** ✅ | Isolated pool (core 2 / max 4 / queue 50 / `CallerRunsPolicy`) so enterprise traffic can never starve the pipelines. Job state lives in Postgres, so it survives a restart and is queryable. Fits the single-instance EC2 deployment shape documented in `docs/architecture.md`. | Needs a startup reaper for jobs left `RUNNING` by a crash | ✅ **Chosen** |
| **C. SQS + separate worker** | True horizontal scale; JVM restarts harmless | Contradicts the deployment shape ("one moving part, no AWS orchestration to learn"). Second deployable, second IAM story, second log stream — for a workload that today is a handful of jobs a day. | ❌ — the documented Phase 2 EventBridge/Fargate migration is where this belongs, if ever |
| **D. Spring Batch** | Chunking, restartability, job repository for free | Heavyweight; its job repository would sit beside your own domain model and confuse both | ❌ |
| **E. Quartz with a JDBC job store** | Clustering, misfire policy | Another scheduler abstraction on top of the `@Scheduled` one you already use | ❌ |

**Chosen: B**, with three specific hardening rules:

1. **Bounded queue with `CallerRunsPolicy`.** If the queue fills, submission blocks the
   web thread briefly rather than silently dropping work — and the per-account
   concurrency cap (§8) means one customer can't fill it.
2. **Heartbeat column.** The runner updates `heartbeat_at` periodically. A
   `@Scheduled` reaper marks any `RUNNING` job whose heartbeat is older than
   `stale-job-reap-minutes` as `FAILED` with `errorType = ORPHANED`. Without this, a
   deploy leaves ghost jobs spinning forever in the UI.
3. **Hard timeout.** `job-timeout-ms` (default 15 min) is enforced inside the runner;
   an over-running job is cancelled and marked `FAILED / TIMEOUT`.

---

## 6. Decision 3 — How does the page show progress? *(HTMX polling)*

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **A. HTMX polling** ✅ | `hx-trigger="every 2s"` on a fragment; ~6 lines of markup; works through nginx with zero config; degrades gracefully; matches the existing HTMX-first UI | A little chattier than a stream | ✅ **Chosen** |
| **B. Server-Sent Events (`SseEmitter`)** | Real-time, low overhead once open | Needs `proxy_buffering off; proxy_read_timeout 3600s;` added to the nginx site config, and an emitter registry that leaks if not carefully closed. One more thing to get wrong at deploy time. | Documented as an upgrade path, not shipped |
| **C. WebSocket / STOMP** | Full duplex | Enormous overkill for "is my CSV ready yet" | ❌ |

**Chosen: A.** Two polled fragments:

- `GET /enterprise/data/jobs/fragment` → the job table rows (2 s while any job is
  `QUEUED`/`RUNNING`, then the trigger is dropped).
- `GET /enterprise/data/jobs/{jobId}/log/fragment?from={byteOffset}` → appends new log
  lines into a `<pre>` pane, so a developer watches the run live.

If SSE is later wanted, the exact nginx additions are recorded in ED-1 §Deployment notes
so the migration is a config change plus one adapter, not a redesign.

---

## 7. Decision 4 — How does a *timed* push fire? *(DB sweeper, not dynamic scheduling)*

This is the least obvious decision in the design and the one most worth writing down.

Customers set their own cron expressions. Spring's `@Scheduled` is static — the
expression is fixed at startup from `application.yml`, which is exactly how every
existing scheduler in this project works (`aihealthcare.trends.schedule`,
`aihealthcare.regulatory.schedule`, …). Per-customer schedules need something else.

| Option | How it works | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Dynamic `TaskScheduler` registry** | On startup and on every CRUD, register/cancel a `ScheduledFuture` per schedule row | Fires exactly on time; no polling | An in-memory registry that must be rebuilt on every restart and kept in sync with the DB on every edit — a classic source of "the schedule silently stopped firing" bugs. Untestable without a real scheduler. Breaks outright the day a second instance runs. | ❌ |
| **B. Fixed-rate DB sweeper** ✅ | One `@Scheduled(cron = "0 * * * * *")` job wakes every minute, selects schedules where `active = true AND next_run_at <= now`, **claims** each with a conditional UPDATE, submits a `DataJob`, computes the next `next_run_at` from the stored `CronExpression` | State is entirely in the DB, so restarts are free. Trivially testable with a fixed `Clock`. Safe under multiple instances because the claim is atomic. Reuses the `@Scheduled` + externalised-cron pattern already used eight times in this codebase. One-minute granularity, which is far finer than any customer needs for a data feed. | Up to 60 s of jitter | ✅ **Chosen** |
| **C. EventBridge Scheduler → HTTPS endpoint** | AWS holds the schedules and calls back | No in-app scheduler at all | Schedule state lives outside the app, so the UI has to talk to AWS to render a table. Adds IAM and API-Gateway surface. Contradicts the Phase-1 deployment shape. | ❌ — reconsider only alongside the documented Phase-2 migration |
| **D. Quartz JDBC job store** | Purpose-built, clustered, misfire handling | A second scheduling framework, a second set of tables, a second mental model | ❌ |

**Chosen: B.** The claim statement is the heart of it:

```sql
UPDATE enterprise_push_schedules
   SET next_run_at = :computedNextRunAt,
       last_run_at = :now,
       last_status = 'RUNNING'
 WHERE schedule_id = :scheduleId
   AND active      = TRUE
   AND next_run_at = :observedNextRunAt
```

An update count of `1` means this sweeper owns the run. `0` means someone else took it,
or the row changed underneath — skip, no harm. `next_run_at` is advanced *before* the job
runs, so a job that crashes does not re-fire in a hot loop.

**Time zones matter here.** A customer who asks for "every weekday at 07:00" means 07:00
*their* time. Store `zone_id` alongside `cron_expression` and compute the next fire time
with `CronExpression.next(ZonedDateTime.now(ZoneId.of(zoneId)))`, then persist the result
as UTC. Getting this wrong is the single most likely support ticket.

**Failure back-off.** `consecutive_failures` increments on failure. At a configurable
threshold (default 5) the schedule auto-deactivates and an admin notification fires via
the existing `AdminNotificationPort`. A customer's broken schedule must not email them a
failure every hour forever.

---

## 8. Decision 5 — How is a *prompt* turned into data? *(three tiers, and never NL→SQL)*

This is the safety-critical decision.

| Option | Verdict |
|---|---|
| **A. LLM generates SQL, we execute it** | ❌ **Absolutely not.** Prompt injection becomes SQL injection with the application's own DB credentials. There is no prompt, no sandbox, and no read-only user that makes this acceptable for a multi-tenant product. |
| **B. Canned parameterised prompts only** | Safe, but too rigid — the requirement explicitly asks for free-text prompting |
| **C. LLM produces a *constrained query plan*, we execute the plan** ✅ | Safe and flexible |
| **D. RAG synthesis — retrieve first, then let the LLM answer over what was retrieved** ✅ | Safe; this is what `AiSearchPort` already does |

**Chosen: B + C + D, layered.**

```
                    ┌──────────────────────────────────────────┐
  customer input →  │  Is a promptId supplied?                 │
                    └───────────────┬──────────────────────────┘
                       yes │                    │ no (free text)
                           ▼                    ▼
             ┌──────────────────────┐   ┌───────────────────────────────┐
             │ CannedPrompt         │   │ PromptToQueryPort             │
             │ template + validated │   │ BeanOutputConverter<DataQuery │
             │ parameters           │   │ Plan> — whitelisted fields    │
             └──────────┬───────────┘   └───────────────┬───────────────┘
                        │                               │
                        └───────────┬───────────────────┘
                                    ▼
                          ┌────────────────────┐
                          │ DataQuery (record) │  ← the ONLY thing an adapter ever sees
                          └─────────┬──────────┘
                                    ▼
                  INTERNAL_CORPUS ──┼── LLM_SYNTHESIS ── CUSTOMER_REMOTE
                  JPA Specification │   AiSearchPort      RestClient
```

`DataQueryPlan` is a record with a **closed set of fields** — `feedId`, `keywords`,
`dateFrom`, `dateTo`, `states`, `categories`, `sortBy`, `limit`. The LLM fills it in via
`BeanOutputConverter`; anything it emits outside those fields is discarded by the
converter, and every value is then validated against the feed's declared parameter
schema before execution. The LLM never touches SQL, never names a table, and never sees
a connection.

Corpus execution goes through JPA `Specification` builders — the pattern
`ArticleSpecificationBuilder` already establishes — so the query is composed from typed
predicates, not string concatenation.

If the plan cannot be validated (unknown field value, limit over the tier cap, empty
plan), the job fails fast with `errorType = UNRESOLVABLE_PROMPT` and the log file records
the rejected plan. That is a *feature*: the customer sees exactly why their question
didn't map to a feed, and the developer sees it in the log.

### Where do canned prompts live?

Two options existed: the `search_prompts` table (`SearchPromptPort` /
`SearchPromptController` already manage engine-keyed templates) or a new table.

**Chosen: a new `enterprise_data_prompts` table.** `search_prompts` is keyed by *engine*
and shaped for AI-search templating; enterprise prompts need a feed binding, a parameter
schema, a minimum tier, and an admin CRUD lifecycle. Overloading the existing table would
force nullable columns into a working feature. The new table follows the same shape and
conventions, so the pattern is familiar.

---

## 9. Decision 6 — Reaching customer-side systems *(HTTPS/JSON only, in ED-1)*

`CUSTOMER_REMOTE` is the highest-risk source family. It is the only one where the
application makes an outbound connection to an address a customer supplied.

| Connector kind | Verdict |
|---|---|
| **HTTPS/JSON (GET or POST, JSON response)** ✅ | Shipped in ED-1. Widest coverage, no drivers, no VPC work, testable with `MockRestServiceServer`. |
| **JDBC to a customer database** | Deferred. Needs per-tenant drivers, connection pools, network path (VPC peering / PrivateLink), and a credential model far heavier than a header token. Its own slice. |
| **Customer S3 bucket** | Deferred. Needs cross-account IAM role assumption — an operations task as much as a code task. |
| **MCP server on the customer side** | Interesting for the course; premature for the product. |

### The non-negotiable controls on the HTTPS connector

Because the app runs on EC2, an unguarded outbound fetcher is an **SSRF weapon aimed at
the instance metadata service**. A customer who registers
`http://169.254.169.254/latest/meta-data/iam/security-credentials/` and pulls it as a
"feed" would exfiltrate the instance role's credentials. The connector therefore must:

1. Resolve the host **before** connecting and reject any address in a private, loopback,
   link-local, or unique-local range (`10/8`, `172.16/12`, `192.168/16`, `127/8`,
   `169.254/16`, `::1`, `fc00::/7`) — checked on the *resolved IP*, not the hostname,
   and re-checked after any redirect.
2. **Disable redirect following** entirely (`RestClient` with a request factory that does
   not follow redirects). A 3xx is an error.
3. Enforce an **allow-list** of hosts from `aihealthcare.enterprise.data.remote.allowed-hosts`
   when that list is non-empty, so an operator can lock this down hard per deployment.
4. Require **HTTPS**; reject `http://`, `file://`, `gopher://`, everything else.
5. Cap **connect timeout, read timeout, and response bytes** — a customer endpoint that
   streams forever must not pin a worker thread or exhaust the heap.
6. **Never store the secret.** `enterprise_remote_connections.secret_ref` holds the *name*
   of an environment variable or AWS Secrets Manager entry. The value is resolved at
   request time and never logged, never returned by any API, never rendered in the UI.
7. Independently: the EC2 instance should require **IMDSv2** (`HttpTokens=required`),
   which defeats this class of attack even if the code check regresses. That is an
   infrastructure task, recorded here so it isn't forgotten.

These are spelled out as explicit test cases in ED-1 so they cannot be quietly dropped.

---

## 10. Decision 7 — Delivering the PUSH payload *(email attachment, with an automatic link fallback)*

Chosen transport: **email with the extract attached**, reusing SES (the deploy scripts
already provision it via `setup-ses-domain.sh`) through the existing
`TransactionalEmailPort` family.

The wrinkle: **SES caps message size**, and a 200,000-row CSV will blow past any
attachment limit long before it blows past a customer's patience. So delivery is
size-aware:

```
artifact bytes ≤ max-attachment-bytes (default 8 MB)
        → email with attachment, plus a link as a convenience
artifact bytes >  max-attachment-bytes
        → email with a signed, expiring download link only
          subject line and body state the size and row count
```

The signed link is an HMAC-SHA256 token over `(artifactId, expiryEpochSeconds)` using
`aihealthcare.enterprise.data.signing-secret`, verified by a controller that requires no
session — so the customer's data team can `curl` it into their pipeline. Default TTL 7
days, configurable. This is why the "signed download URL" option from the original
menu isn't a competing choice: it's the graceful degradation path of the chosen one.

A new outbound port, `DataPushDeliveryPort`, is introduced rather than widening
`TransactionalEmailPort` with an attachment method. Rationale: the *surgical changes*
convention. `TransactionalEmailPort` is used by password reset, digests and admin
notifications; changing its contract touches all of them. A new port with one adapter
(`EmailDataPushAdapter`, wrapping `JavaMailSender` + `MimeMessageHelper`) touches
nothing existing — and it leaves the seam open for `S3DataPushAdapter` or
`SftpDataPushAdapter` later without another refactor.

---

## 11. Decision 8 — The output log file

The requirement — "an output log file that is available for testing" — is served by
three layers, each answering a different question.

| Layer | Answers | Implementation |
|---|---|---|
| **Per-job log file** ✅ primary | "What happened in *this* run?" | `data-exports/logs/{jobId}.log` — plain text, one line per lifecycle phase, written by `DataJobLog` as the job executes. Downloadable from the console and via `GET /api/v1/enterprise/data/jobs/{jobId}/log`. Tailed live by the polled fragment. |
| **Rolling application log** | "What has this subsystem been doing all week?" | A dedicated Logback logger `com.wgblackmon.aihealthcare.enterprise` → `logs/enterprise-data.log`, size- and time-rolling, so enterprise traffic is greppable without wading through `app.log` (which is already 2 MB+). |
| **DB audit table** | "Who accessed what, when, and how much?" — the compliance question | `enterprise_data_audit`, append-only, one row per meaningful action (`SUBMIT`, `TIER_DENY`, `QUOTA_DENY`, `FETCH`, `DOWNLOAD`, `PUSH_SEND`, `LINK_REDEEM`). Queryable, retained longer than the artifacts. |

The per-job file format is deliberately fixed-shape and greppable, because its main job
is to make a developer's test loop fast:

```
2026-09-08T14:02:11.482Z  INFO  job=6f2c8a1e  phase=SUBMIT       owner=acme@example.com feed=legislation format=CSV
2026-09-08T14:02:11.500Z  INFO  job=6f2c8a1e  phase=TIER_CHECK   tier=ENTERPRISE result=ALLOW
2026-09-08T14:02:11.512Z  INFO  job=6f2c8a1e  phase=QUOTA_CHECK  used=41 limit=500 result=ALLOW
2026-09-08T14:02:11.610Z  INFO  job=6f2c8a1e  phase=PLAN         source=CANNED promptId=legislation-by-state params={state=MT,year=2026}
2026-09-08T14:02:11.980Z  INFO  job=6f2c8a1e  phase=FETCH_START  adapter=LegislationCorpusDataSourceAdapter
2026-09-08T14:02:13.204Z  INFO  job=6f2c8a1e  phase=FETCH_END    rows=418 durationMs=1224
2026-09-08T14:02:13.260Z  INFO  job=6f2c8a1e  phase=RENDER_END   format=CSV bytes=96412 sha256=9f1c…
2026-09-08T14:02:13.301Z  INFO  job=6f2c8a1e  phase=COMPLETE     status=SUCCEEDED totalMs=1819
```

Every value that could carry customer text — prompts, parameters, error messages from a
remote endpoint — passes through the existing `LogSanitizer` before it is written. A log
file that leaks a customer's query into a shared operations log is a privacy incident,
not a debugging aid.

---

## 12. Decision 9 — Access control, quotas and tenancy

`SubscriptionTier.ENTERPRISE` is the gate. Beyond that, four independent limits, because
a single "queries per month" number does not stop any of the ways this feature can hurt
the box:

| Limit | Config key | Why it exists |
|---|---|---|
| Monthly jobs | `aihealthcare.tiers.enterprise.monthly-data-jobs` | Commercial metering; enforced through `UsageTrackingPort` alongside the existing counters |
| Monthly push runs | `…monthly-push-runs` | A customer with 20 hourly schedules is 14,400 runs/month |
| Rows per job | `aihealthcare.enterprise.data.max-rows-per-job` | Memory and artifact size — a hard server-side ceiling regardless of what the request asks for |
| Concurrent running jobs per account | `…max-concurrent-jobs-per-account` | Stops one customer filling the executor queue; over the cap returns `429` with `Retry-After` |

**Ownership is checked on every single read.** A job, an artifact, a log file and a
schedule are all owned by an email (and optionally a `team_id`). Every lookup is
`findByJobIdAndOwnerEmail`, never `findById` followed by a check — because the
"followed by" is what people forget. A job belonging to someone else returns `404`, not
`403`, so job ids aren't enumerable.

**Two authentication paths, one authorisation model.** The console page uses the session
(Spring Security) and CSRF. `/api/v1/enterprise/**` uses `X-API-Key` via
`ApiKeyAuthenticationFilter` and must be added to the CSRF ignore list in
`SecurityConfig` alongside the other API paths. Both resolve to the same principal and
the same tier check — the tier must come from the key's *owner*, which is a detail worth
verifying in `ApiKeyAuthenticationFilter` during reconnaissance.

---

## 13. The console page

One page, `GET /enterprise/data` → `enterprise-data-console.html`, ENTERPRISE-gated, four
Alpine-driven tabs:

**1 · Run** — feed selector; canned-prompt dropdown that renders its parameter form
dynamically; a free-text box as the alternative; format and row-limit controls; Submit.
Submitting POSTs and swaps a job card into the Jobs tab.

**2 · Jobs** — a table polled every 2 s while anything is in flight: status pill, feed,
rows, bytes, duration, and three actions — *Preview*, *Download data*, *Download log*.
Preview expands inline: four KPI tiles (rows / bytes / duration / sources), the first 50
rows as a real table, one Chart.js summary chart chosen by the feed's declared shape (a
time series where the data has a date axis, a horizontal bar where it has categories),
and the citation list for `LLM_SYNTHESIS` feeds. Charts follow the existing dashboard
conventions from Slice 46.

**3 · Schedules** *(ED-2)* — CRUD table: label, feed/prompt, cron with a
human-readable **"next three runs"** preview rendered server-side from the stored
`CronExpression` and zone, recipients, format, active toggle, last-run status, and a
*Run now* button that submits the identical job immediately. The next-runs preview is
small and it prevents most cron mistakes before they ship.

**4 · Log** — a `<pre>` pane bound to the selected job, appending via the polled
`?from={offset}` fragment. This is the "available for testing" surface: pick a job, watch
it run, read exactly why it failed.

---

## 14. Configuration block

Added to `application.yml` under the existing `aihealthcare:` root, following the
documented style — every cron externalised, every limit named, every block preceded by an
explanatory comment.

```yaml
aihealthcare:

  # -------------------------------------------------------------------------
  # Enterprise remote data access (Slices ED-1 / ED-2).
  # PULL  — customers submit prompts, jobs run async, artifacts + logs persist.
  # PUSH  — customer-owned cron schedules email the same artifacts on a timer.
  # Paths are relative to the working directory; created on startup if absent.
  # -------------------------------------------------------------------------
  enterprise:
    data:
      enabled: true
      artifact-directory: data-exports
      log-directory: data-exports/logs
      retention-days: 14                    # artifacts + per-job logs; audit rows kept longer
      max-rows-per-job: 100000
      max-artifact-bytes: 52428800          # 50 MB hard ceiling
      max-concurrent-jobs-per-account: 2
      job-timeout-ms: 900000                # 15 min
      stale-job-reap-minutes: 30
      reaper-cron: "0 */5 * * * *"
      retention-cron: "0 15 3 * * *"        # 03:15 UTC, before the 04:00 harvest wave
      signed-link-ttl-hours: 168            # 7 days
      signing-secret: ${ENTERPRISE_LINK_SECRET:}

      executor:
        core-pool-size: 2
        max-pool-size: 4
        queue-capacity: 50
        thread-name-prefix: ent-data-

      remote:                               # CUSTOMER_REMOTE connector
        allowed-hosts: []                   # empty = allow any public host that passes SSRF checks
        connect-timeout-ms: 5000
        read-timeout-ms: 30000
        max-response-bytes: 26214400        # 25 MB

      push:                                 # ED-2
        sweep-cron: "0 * * * * *"           # every minute, on the minute
        max-attachment-bytes: 8388608       # 8 MB — above this, send a signed link instead
        from-address: data@bigskylabs.ai
        max-recipients: 10
        failure-threshold: 5                # consecutive failures before auto-deactivating

  tiers:
    enterprise:
      archive-days: 0                       # unlimited, as SUBSCRIBER
      monthly-query-limit: 2000
      monthly-data-jobs: 500
      monthly-push-runs: 300
```

> `ENTERPRISE_LINK_SECRET` must be set on EC2 before ED-2 ships. If it is blank at
> startup and `enterprise.data.enabled` is true, the application should log a `WARN` and
> disable signed links (falling back to attachment-only), rather than signing with an
> empty key.

---

## 15. Persistence

Five new tables. Naming, column style and pipe-delimited list columns follow the existing
schema conventions documented in `docs/architecture.md`.

```sql
-- Every PULL and PUSH execution, whatever its origin.
CREATE TABLE enterprise_data_jobs (
  job_id               VARCHAR(36)   PRIMARY KEY,
  owner_email          VARCHAR(320)  NOT NULL,
  team_id              VARCHAR(36),
  mode                 VARCHAR(16)   NOT NULL,   -- PULL | PUSH
  feed_id              VARCHAR(64)   NOT NULL,
  prompt_id            VARCHAR(64),
  prompt_text          TEXT,                     -- sanitized before write
  parameters_json      TEXT,
  format               VARCHAR(16)   NOT NULL,
  status               VARCHAR(16)   NOT NULL,   -- QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELLED|EXPIRED
  row_count            INTEGER,
  byte_size            BIGINT,
  content_sha256       VARCHAR(64),
  artifact_path        VARCHAR(512),
  log_path             VARCHAR(512),
  error_type           VARCHAR(64),              -- see PipelineErrorType precedent
  error_message        VARCHAR(1024),
  submitted_at         TIMESTAMP     NOT NULL,
  started_at           TIMESTAMP,
  completed_at         TIMESTAMP,
  heartbeat_at         TIMESTAMP,
  expires_at           TIMESTAMP,
  schedule_id          VARCHAR(36)               -- non-null when mode = PUSH
);
CREATE INDEX idx_edj_owner_submitted ON enterprise_data_jobs (owner_email, submitted_at DESC);
CREATE INDEX idx_edj_status          ON enterprise_data_jobs (status);
CREATE INDEX idx_edj_schedule        ON enterprise_data_jobs (schedule_id);

-- Admin-curated, parameterised prompts ("pre-determined prompts").
CREATE TABLE enterprise_data_prompts (
  prompt_id       VARCHAR(64)  PRIMARY KEY,
  label           VARCHAR(160) NOT NULL,
  description     VARCHAR(1024),
  feed_id         VARCHAR(64)  NOT NULL,
  template_text   TEXT         NOT NULL,
  parameters_json TEXT,                          -- [{name,label,type,required,default,allowedValues[]}]
  min_tier        VARCHAR(32)  NOT NULL DEFAULT 'ENTERPRISE',
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMP    NOT NULL,
  updated_at      TIMESTAMP    NOT NULL
);

-- ED-2. Customer-owned recurring pushes.
CREATE TABLE enterprise_push_schedules (
  schedule_id          VARCHAR(36)  PRIMARY KEY,
  owner_email          VARCHAR(320) NOT NULL,
  label                VARCHAR(160) NOT NULL,
  feed_id              VARCHAR(64)  NOT NULL,
  prompt_id            VARCHAR(64),
  prompt_text          TEXT,
  parameters_json      TEXT,
  format               VARCHAR(16)  NOT NULL,
  cron_expression      VARCHAR(64)  NOT NULL,
  zone_id              VARCHAR(64)  NOT NULL DEFAULT 'UTC',
  recipients           TEXT         NOT NULL,    -- pipe-delimited, per existing convention
  active               BOOLEAN      NOT NULL DEFAULT TRUE,
  next_run_at          TIMESTAMP,
  last_run_at          TIMESTAMP,
  last_status          VARCHAR(16),
  last_job_id          VARCHAR(36),
  consecutive_failures INTEGER      NOT NULL DEFAULT 0,
  created_at           TIMESTAMP    NOT NULL,
  updated_at           TIMESTAMP    NOT NULL
);
CREATE INDEX idx_eps_due   ON enterprise_push_schedules (active, next_run_at);
CREATE INDEX idx_eps_owner ON enterprise_push_schedules (owner_email);

-- Append-only compliance trail. Outlives artifacts.
CREATE TABLE enterprise_data_audit (
  audit_id    BIGSERIAL     PRIMARY KEY,
  occurred_at TIMESTAMP     NOT NULL,
  owner_email VARCHAR(320)  NOT NULL,
  job_id      VARCHAR(36),
  schedule_id VARCHAR(36),
  action      VARCHAR(48)   NOT NULL,
  outcome     VARCHAR(16)   NOT NULL,            -- ALLOW | DENY | SUCCESS | FAILURE
  detail      VARCHAR(1024),
  row_count   INTEGER,
  byte_size   BIGINT
);
CREATE INDEX idx_eda_owner_time ON enterprise_data_audit (owner_email, occurred_at DESC);

-- CUSTOMER_REMOTE endpoints. Never holds a secret value — only a reference.
CREATE TABLE enterprise_remote_connections (
  connection_id VARCHAR(36)  PRIMARY KEY,
  owner_email   VARCHAR(320) NOT NULL,
  label         VARCHAR(160) NOT NULL,
  kind          VARCHAR(24)  NOT NULL,           -- HTTPS_JSON
  base_url      VARCHAR(512) NOT NULL,
  auth_type     VARCHAR(24)  NOT NULL,           -- NONE | API_KEY_HEADER | BEARER
  header_name   VARCHAR(64),
  secret_ref    VARCHAR(256),                    -- env var name or Secrets Manager id
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMP    NOT NULL,
  updated_at    TIMESTAMP    NOT NULL
);
```

---

## 16. HTTP surface (OpenAPI-first)

Per the project's SDD rule, `openapi.yaml` is edited *before* any controller.

**ED-1**

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/enterprise/data/feeds` | Registry: what this account may query |
| `GET` | `/api/v1/enterprise/data/prompts` | Canned prompts + parameter schemas |
| `POST` | `/api/v1/enterprise/data/jobs` | Submit. `202 Accepted`, `Location: …/jobs/{id}`, body `{jobId, status}` |
| `GET` | `/api/v1/enterprise/data/jobs` | Owner's jobs, paged, newest first |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}` | Status + metadata |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/artifact` | Streams the result, `Content-Disposition: attachment` |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/log` | `text/plain`; `?from=` byte offset for tailing |
| `DELETE` | `/api/v1/enterprise/data/jobs/{jobId}` | Cancel if `QUEUED`/`RUNNING` |
| `GET`/`POST`/`DELETE` | `/api/v1/enterprise/connections[/{id}]` | `CUSTOMER_REMOTE` endpoints; never returns `secret_ref` values |

**ED-2**

| Method | Path | Notes |
|---|---|---|
| `GET`/`POST` | `/api/v1/enterprise/data/schedules` | List / create; create validates the cron and returns `nextRuns[3]` |
| `PUT`/`DELETE` | `/api/v1/enterprise/data/schedules/{id}` | Update / delete |
| `POST` | `/api/v1/enterprise/data/schedules/{id}/run` | Fire once now, out of band |
| `GET` | `/d/{token}` | Signed artifact download. No session. HMAC + expiry verified. |

**Pages**

| Path | Template |
|---|---|
| `GET /enterprise/data` | `enterprise-data-console.html` |
| `GET /enterprise/data/jobs/fragment` | HTMX-polled job rows |
| `GET /enterprise/data/jobs/{id}/log/fragment` | HTMX-polled log tail |
| `GET /admin/enterprise/prompts` | Admin CRUD for canned prompts |

---

## 17. What is explicitly *not* in these two slices

Written down so nobody builds it speculatively, and so the roadmap is honest.

| Deferred | Why | Likely home |
|---|---|---|
| JDBC connector to customer databases | Drivers, pools, VPC peering, credential rotation — an infrastructure project wearing a code project's clothes | ED-3 |
| Cross-account S3 read/write | IAM role assumption is an ops task first | ED-3 |
| S3 / SFTP push transports | The `DataPushDeliveryPort` seam is deliberately open for these | ED-4 |
| Webhook push (Slack/Teams) | `WebhookDispatcher` already exists; adding an `ENTERPRISE_DATA_PUSH` event type is ~half a day once ED-2 lands | ED-4 |
| Sync fast-path for tiny canned queries | Needs real usage data to size the threshold | later |
| SSE progress streaming | Polling is sufficient; nginx changes documented in ED-1 | later |
| SQS / Fargate execution | Contradicts the documented Phase-1 deployment shape | Phase 2 |
| Per-customer data residency or row-level tenancy in the corpus | The corpus is shared editorial data; there is no customer-specific row today | n/a |

---

## 18. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| SSRF via `CUSTOMER_REMOTE` reaching EC2 instance metadata | **Critical** | Resolved-IP blocklist, no redirects, HTTPS-only, host allow-list, plus IMDSv2 required on the instance (§9) |
| Prompt injection → data exfiltration | **High** | No NL→SQL anywhere. Constrained `DataQueryPlan` with a closed field set; execution via typed JPA Specifications (§8) |
| One customer starving the nightly pipelines | High | Dedicated bounded executor, per-account concurrency cap, hard row and byte ceilings (§5, §12) |
| Artifacts filling the EC2 disk | High | `max-artifact-bytes`, nightly retention sweeper, `expires_at` on every job |
| Jobs stuck `RUNNING` after a deploy | Medium | Heartbeat column + reaper marking them `FAILED / ORPHANED` (§5) |
| Cron time-zone confusion | Medium | `zone_id` stored per schedule; "next three runs" preview in the UI (§7, §13) |
| A broken schedule emailing failures hourly forever | Medium | `consecutive_failures` back-off with auto-deactivate + admin notification (§7) |
| Oversized SES message rejected silently | Medium | Size-aware delivery with automatic signed-link fallback (§10) |
| Customer prompts leaking into shared logs | Medium | `LogSanitizer` on every logged customer-supplied value (§11) |
| Signed download links leaking via forwarded email | Low–Medium | Short TTL, single artifact scope, redemption audited; link is never a bearer token for anything else |
| Job-id enumeration | Low | `404` (not `403`) for another owner's job; UUID ids |

---

## 19. Local development and testing

The feature is profile-agnostic. `artifact-directory` and `log-directory` are relative
paths, so running locally (`golocal` skill) writes into the project working directory and
running on EC2 (`goremote`) writes beside the app — no code change, no profile branch.

For a fast manual loop:

1. Start locally; sign in as an ENTERPRISE account.
2. `POST /api/v1/enterprise/data/jobs` with `{"feedId":"legislation","promptId":"legislation-by-state","parameters":{"state":"MT","year":"2026"},"format":"CSV"}`.
3. Watch `data-exports/logs/{jobId}.log` grow — or watch the Log tab do it for you.
4. Download the CSV and diff it against `health-ai-legislation.csv` to confirm the
   legislation feed's column contract hasn't drifted.
5. For ED-2, create a schedule with `cron_expression = "0 * * * * *"` and MailHog
   (`MailHog.exe` is already in the project root) as the SMTP sink; a mail with an
   attachment should land every minute.

Both slices carry unit tests at every layer of the existing pyramid, and both follow the
selective-test-execution rule in `CLAUDE.md` — run the changed classes' tests, not the
full 1,500-test suite.

---

## 20. Summary of decisions

| # | Decision | Chosen | Alternative kept in reserve |
|---|---|---|---|
| 1 | PULL transport | Async job + durable artifact | Sync fast-path for small canned queries |
| 2 | Execution | Dedicated bounded in-JVM executor + DB job table | SQS/Fargate at Phase 2 |
| 3 | Progress | HTMX polling | SSE (nginx config documented) |
| 4 | PUSH firing | Per-minute DB sweeper with atomic claim | EventBridge at Phase 2 |
| 5 | Prompts | Canned + constrained `DataQueryPlan` + RAG. **Never NL→SQL** | — |
| 6 | Customer-side | HTTPS/JSON only, hard SSRF controls | JDBC, S3 (ED-3) |
| 7 | PUSH delivery | Email attachment, auto-fallback to signed link | S3, SFTP, webhook (ED-4) |
| 8 | Logging | Per-job file + rolling appender + DB audit | CloudWatch shipping |
| 9 | Access | ENTERPRISE tier + ownership on every read + four independent limits | Team-scoped sharing |
| 10 | Structure | One `EnterpriseDataSourcePort` with many adapters | Per-dataset services |
