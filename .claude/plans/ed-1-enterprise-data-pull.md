# Claude Code Prompt — AIHealthcare: Slice ED-1, Enterprise Data PULL (Spec-Driven Development)

You are working in the AIHealthcare project at `C:\workspaces\SpringAIClaude\AIHealthcare`.

**Before writing any code**, read `CLAUDE.md`, `.claude/CONVENTIONS.md`, `docs/CONVENTIONS.md`,
`docs/architecture.md`, and the `hexagonal-architecture` and `spring-ai` skills. The
design rationale behind this slice is in `docs/enterprise-data-access-design.md` — read
it too; it explains *why* several things here are shaped the way they are, and you should
not "improve" them without reading it first.

Follow the SDD workflow: small increments, each built, tested and reviewed before the
next begins.

---

## Context and intent

ENTERPRISE-tier customers need to request data from AIHealthcare — either by choosing a
pre-determined, parameterised prompt, or by typing a question in their own words — and
receive it back as a downloadable artifact with a full, readable log of what happened.

This slice builds the **PULL** half. Slice ED-2 adds the **PUSH** half (customer-owned
cron schedules that email the same artifacts) and reuses everything built here — so
design decisions in this slice are load-bearing for the next one.

Design goals, in priority order:

1. **One execution core, many triggers.** A pull and a scheduled push must be the same
   `DataJob` running the same code. ED-2 should add *when* and *where*, nothing else.
2. **One port, many sources.** Internal corpus queries, LLM synthesis and customer-side
   HTTPS endpoints all sit behind a single outbound port so a new data feed is one new
   adapter class and zero changes elsewhere.
3. **A prompt never becomes SQL.** Free text is resolved into a constrained, whitelisted
   query plan or answered by RAG synthesis — never translated into a query string.
4. **Every run is auditable and reproducible.** A per-job log file that a developer can
   read top to bottom and understand exactly what the system decided and why.
5. **Nothing a customer submits can hurt the box.** Bounded executor, bounded rows,
   bounded bytes, bounded time, bounded concurrency, and hard SSRF controls on the only
   outbound connector.

---

## Scope boundaries

- Do **not** modify existing pipeline, newsletter, wiki, research or harvesting code
  except where an increment explicitly says so.
- Do **not** rename or repurpose the existing `DataExport*` family
  (`ExportDataUseCase`, `DataExportService`, `DataExportRequest`, `DataExportResult`,
  `DataExportController`). This slice's types are all named `EnterpriseData*` /
  `Data*Job*` to avoid collision. You **do** reuse the existing `ExportFormat` enum.
- No PUSH, no scheduling, no email. That is ED-2. Do not add a `cron` field anywhere,
  do not create `enterprise_push_schedules`.
- No JDBC or S3 customer connectors. HTTPS/JSON only.
- No new Maven modules. Everything goes in the existing single module under
  `com.wgblackmon.aihealthcare.*`.
- Every new class follows `.claude/CONVENTIONS.md` without exception: class-level Javadoc
  with `@author Bill Blackmon`, `@version 1.0`, `@since 2026-09-08`, `@updated 2026-09-08`;
  Lombok `@Slf4j` on every concrete class outside `domain`; `log.debug()` naming the
  method and its arguments as the first statement and `log.debug("... | return={}", …)`
  before every return; **traditional `for` loops only, no Streams anywhere**;
  records for all immutable data; constructor injection only; `domain` stays JDK-only.

---

## Increment 0 — Reconnaissance (no production code)

Before writing anything, read these existing files and report back a short table of what
you found, so the rest of the slice is written against reality rather than assumption.
**Stop and ask if any of them contradicts this spec.**

| File | What to confirm |
|---|---|
| `domain/model/SubscriptionTier.java` | That `ENTERPRISE` exists, and the full enum ordering |
| `domain/model/TierLimits.java`, `infrastructure/config/TierLimitProperties.java` | The shape of a tier's limits, and how to add `monthlyDataJobs` / `monthlyPushRuns` |
| `domain/service/TierGatingService.java` | The exact method used to assert a minimum tier, and what it throws on denial |
| `domain/port/outbound/UsageTrackingPort.java`, `infrastructure/persistence/UsageTrackingAdapter.java`, `UsageRecordEntity.java`, `UsageRecordId.java` | How a usage counter is keyed and incremented; whether the key is an enum or a string |
| `domain/model/ExportFormat.java`, `domain/service/DataExportService.java` | Which formats exist, and where CSV/JSON serialisation already lives |
| `infrastructure/config/SecurityConfig.java`, `ApiKeyAuthenticationFilter.java`, `ApiRateLimitFilter.java` | Which paths are CSRF-exempt, how `X-API-Key` resolves a principal, and **whether the resolved principal carries the owner's subscription tier** |
| `infrastructure/scheduler/PipelineAsyncRunner.java`, `PipelineRunEventPort.java`, `domain/model/PipelineRunEvent.java`, `PipelineErrorType.java`, `PipelineStepStatus.java` | The existing async + run-event pattern, and the `PipelineErrorType` values you can reuse |
| `domain/port/outbound/ArticleSearchQueryPort.java`, `infrastructure/persistence/ArticleSpecificationBuilder.java`, `domain/model/ArticleSearchCriteria.java` | How a criteria object becomes a JPA `Specification` |
| `domain/port/outbound/StateLawPort.java`, `RegulatoryEventPort.java` | The query methods available for the legislation and regulatory feeds |
| `domain/port/inbound/ConductAiSearchUseCase.java`, `domain/service/AiSearchService.java`, `domain/port/outbound/AiSearchPort.java`, `domain/model/AiSearchResult.java`, `AiSearchSynthesis.java`, `SourceCitation.java` | The synthesis call signature and the citation shape |
| `domain/service/LogSanitizer.java` | The sanitising method signature |
| `web/controller/AdminPipelineController.java` + `templates/admin-pipelines.html` | The house pattern for an async-trigger page with polled status |
| `templates/fragments/tier-utils.html`, `fragments/nav.html`, `fragments/head.html` | How to gate and lay out a new page consistently |
| `static/js/htmx-csrf.js` | How CSRF is attached to HTMX requests |

Report: for each file, one line — the signature or fact you need, and whether it matches
what this spec assumes.

---

## Increment 1 — OpenAPI contract first

Edit the project's `openapi.yaml` (currently
`application/src/main/resources/openapi.yaml` in the single-module layout) and add the
following paths under a new `enterprise-data` tag. No controllers yet.

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/api/v1/enterprise/data/feeds` | — | `200` `[DataFeedResponse]` |
| `GET` | `/api/v1/enterprise/data/prompts` | `?feedId=` optional | `200` `[CannedPromptResponse]` |
| `POST` | `/api/v1/enterprise/data/jobs` | `DataJobRequest` | `202` `DataJobResponse` + `Location` header |
| `GET` | `/api/v1/enterprise/data/jobs` | `?page=&size=&status=` | `200` `DataJobPageResponse` |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}` | — | `200` `DataJobResponse`, `404` if not owned |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/artifact` | — | `200` binary + `Content-Disposition`, `409` if not `SUCCEEDED`, `410` if expired |
| `GET` | `/api/v1/enterprise/data/jobs/{jobId}/log` | `?from=` byte offset | `200` `text/plain` |
| `DELETE` | `/api/v1/enterprise/data/jobs/{jobId}` | — | `204`, `409` if already terminal |
| `GET` | `/api/v1/enterprise/connections` | — | `200` `[RemoteConnectionResponse]` — **never includes secret values** |
| `POST` | `/api/v1/enterprise/connections` | `RemoteConnectionRequest` | `201` |
| `DELETE` | `/api/v1/enterprise/connections/{connectionId}` | — | `204` |

Schema notes:

- `DataJobRequest`: `feedId` (required), `promptId` (optional), `promptText` (optional),
  `parameters` (`map[string]string`, optional), `format` (enum from `ExportFormat`),
  `rowLimit` (integer, optional — server clamps to the tier ceiling),
  `connectionId` (optional, only meaningful for `CUSTOMER_REMOTE` feeds).
  Exactly one of `promptId` / `promptText` may be present; both absent is valid for feeds
  that need no prompt (a plain parameterised extract).
- `DataJobResponse`: `jobId`, `status`, `feedId`, `format`, `rowCount`, `byteSize`,
  `submittedAt`, `startedAt`, `completedAt`, `errorType`, `errorMessage`,
  `artifactUrl`, `logUrl`.
- Document all error responses (`400`, `401`, `403`, `404`, `409`, `410`, `429`) using the
  project's existing error schema — check what `GlobalExceptionHandler` currently emits
  and match it.

Regenerate DTOs per the documented OpenAPI → code flow and confirm the build is green
before moving on.

---

## Increment 2 — Domain records, enums and ports

All in `com.wgblackmon.aihealthcare.domain` — **JDK only, no Spring, no Lombok**.

### `domain.model`

**`DataSourceKind`** — enum: `INTERNAL_CORPUS`, `LLM_SYNTHESIS`, `CUSTOMER_REMOTE`.

**`DataJobMode`** — enum: `PULL`, `PUSH`. (`PUSH` is declared now and used in ED-2, so the
persisted column never needs migrating. This is the one forward-looking value in the
slice and it is deliberate.)

**`DataJobStatus`** — enum: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`,
`EXPIRED`. Add `boolean isTerminal()`.

**`DataFeed`** — record describing one registered feed:
`String feedId`, `String label`, `String description`, `DataSourceKind kind`,
`List<ExportFormat> supportedFormats`, `List<DataParameter> parameters`,
`int defaultRowLimit`, `int maxRowLimit`, `String chartHint`, `boolean active`.
`chartHint` is one of `NONE`, `TIME_SERIES`, `CATEGORY_BAR` and drives the console's
summary chart — a display concern the feed itself is best placed to declare.

**`DataParameter`** — record: `String name`, `String label`, `String type`
(`STRING`/`INTEGER`/`DATE`/`ENUM`), `boolean required`, `String defaultValue`,
`List<String> allowedValues`. Compact constructor rejects a blank name and requires
`allowedValues` to be non-empty when `type` is `ENUM`.

**`CannedPrompt`** — record: `String promptId`, `String label`, `String description`,
`String feedId`, `String templateText`, `List<DataParameter> parameters`,
`SubscriptionTier minTier`, `boolean active`.

**`DataQueryPlan`** — record. **This is the security boundary; its field set is closed and
must not grow without a review.** `String feedId`, `List<String> keywords`,
`LocalDate dateFrom`, `LocalDate dateTo`, `List<String> states`,
`List<String> categories`, `String sortBy`, `int limit`.

**`DataRequest`** — record: `String jobId`, `String ownerEmail`, `String teamId`,
`DataJobMode mode`, `String feedId`, `String promptId`, `String promptText`,
`Map<String,String> parameters`, `ExportFormat format`, `int rowLimit`,
`String connectionId`, `DataQueryPlan plan`, `Instant requestedAt`.
Compact constructor: non-blank `jobId`, `ownerEmail`, `feedId`; non-null `format`;
`rowLimit > 0`; defensive copies of the map and the plan's lists.

**`DataColumn`** — record: `String name`, `String label`, `String type`.

**`DataSet`** — record: `List<DataColumn> columns`, `List<List<String>> rows`,
`String narrative` (nullable — the LLM's prose answer for `LLM_SYNTHESIS` feeds),
`List<SourceCitation> citations`, `List<String> warnings`, `int truncatedAtRows`
(`0` when nothing was truncated). Compact constructor takes defensive copies and asserts
every row's width equals `columns.size()`.

**`DataJob`** — record: `String jobId`, `String ownerEmail`, `String teamId`,
`DataJobMode mode`, `String feedId`, `String promptId`, `ExportFormat format`,
`DataJobStatus status`, `Integer rowCount`, `Long byteSize`, `String contentSha256`,
`String artifactPath`, `String logPath`, `String errorType`, `String errorMessage`,
`Instant submittedAt`, `Instant startedAt`, `Instant completedAt`, `Instant heartbeatAt`,
`Instant expiresAt`, `String scheduleId`.

**`DataArtifact`** — record: `String jobId`, `ExportFormat format`, `String fileName`,
`long byteSize`, `String sha256`, `Instant createdAt`, `Instant expiresAt`.

**`RemoteConnection`** — record: `String connectionId`, `String ownerEmail`,
`String label`, `RemoteConnectionKind kind`, `String baseUrl`, `RemoteAuthType authType`,
`String headerName`, `String secretRef`, `boolean active`.
Compact constructor rejects any `baseUrl` that is not `https://`.
**`secretRef` is a name, never a value.** Add that sentence to the Javadoc verbatim.

**`RemoteConnectionKind`** — enum: `HTTPS_JSON`.
**`RemoteAuthType`** — enum: `NONE`, `API_KEY_HEADER`, `BEARER`.

**`DataAccessAction`** — enum: `SUBMIT`, `TIER_DENY`, `QUOTA_DENY`, `CONCURRENCY_DENY`,
`PLAN`, `FETCH`, `RENDER`, `COMPLETE`, `FAIL`, `CANCEL`, `DOWNLOAD_ARTIFACT`,
`DOWNLOAD_LOG`.

**`DataAccessAuditEntry`** — record: `Instant occurredAt`, `String ownerEmail`,
`String jobId`, `String scheduleId`, `DataAccessAction action`, `String outcome`,
`String detail`, `Integer rowCount`, `Long byteSize`.

### `domain.port.outbound`

**`EnterpriseDataSourcePort`** — the central abstraction:

```java
String         feedId();
DataSourceKind kind();
DataFeed       describe();
boolean        supports(DataRequest request);
DataSet        fetch(DataRequest request, DataJobLog log);
```

Javadoc must state the ownership contract explicitly: *an adapter reads; it never
mutates the corpus; it must honour `request.rowLimit()`; it must write a `FETCH_START`
and `FETCH_END` line to the supplied log; it must never receive or construct raw SQL.*

**`DataJobPort`** — `save`, `findByJobIdAndOwnerEmail`, `findByOwnerEmail` (paged),
`countActiveByOwnerEmail`, `updateStatus`, `touchHeartbeat`,
`findStaleRunning(Instant heartbeatBefore)`, `findExpired(Instant now)`.

**`DataArtifactPort`** — `write(String jobId, ExportFormat format, byte[] content)` →
`DataArtifact`; `read(String jobId)` → `InputStream`; `delete(String jobId)`;
`exists(String jobId)`.

**`DataJobLogPort`** — `open(String jobId)` → `DataJobLog`;
`read(String jobId, long fromByteOffset)` → `String`; `delete(String jobId)`.

**`CannedPromptPort`** — `findAllActive()`, `findByFeedId(String)`,
`findById(String)`, `save(CannedPrompt)`, `delete(String)`.

**`PromptToQueryPort`** — `DataQueryPlan resolve(String promptText, DataFeed feed)`.
Javadoc must state: *implemented by an LLM adapter; the returned plan is validated by the
caller before execution; the implementation must never emit SQL, a table name, or a field
outside `DataQueryPlan`.*

**`RemoteConnectionPort`** — `findByConnectionIdAndOwnerEmail`, `findByOwnerEmail`,
`save`, `delete`.

**`DataAccessAuditPort`** — `append(DataAccessAuditEntry entry)`,
`findByOwnerEmail(String ownerEmail, Instant since, int limit)`.

### `domain.port.inbound`

**`RequestEnterpriseDataUseCase`**:

```java
DataJob            submit(DataRequest request);
DataJob            getJob(String jobId, String ownerEmail);
List<DataJob>      listJobs(String ownerEmail, int page, int size);
void               cancel(String jobId, String ownerEmail);
List<DataFeed>     listFeeds(String ownerEmail);
List<CannedPrompt> listPrompts(String ownerEmail, String feedId);
String             readLog(String jobId, String ownerEmail, long fromByteOffset);
```

### `domain` support type

**`DataJobLog`** — a small interface in `domain.port.outbound` (an appender handed to
adapters so they can write into the per-job file without knowing where it lives):

```java
void phase(String phase, String detailKeyValuePairs);
void warn(String phase, String detail);
void error(String phase, String detail, Throwable cause);
long byteOffset();
```

### Unit tests for this increment

- `DataRequestTest` — compact-constructor validation, defensive copying (mutate the map
  you passed in, assert the record didn't change).
- `DataSetTest` — row-width assertion, truncation flag.
- `DataQueryPlanTest` — construction with each field, and rejection of a negative limit.
- `RemoteConnectionTest` — `http://` rejected, `https://` accepted.
- `DataJobStatusTest` — `isTerminal()` for every value.

Build and run only these tests. Stop for review.

---

## Increment 3 — Persistence

Package `com.wgblackmon.aihealthcare.infrastructure.persistence` (matching the existing
flat layout — entities, repositories and adapters side by side).

Create `EnterpriseDataJobEntity` + `EnterpriseDataJobRepository` + `DataJobAdapter`,
`EnterpriseDataPromptEntity` + repository + `CannedPromptAdapter`,
`EnterpriseDataAuditEntity` + repository + `DataAccessAuditAdapter`,
`EnterpriseRemoteConnectionEntity` + repository + `RemoteConnectionAdapter`.

DDL is specified in `docs/enterprise-data-access-design.md` §15 — follow it exactly,
including index names. Add the tables to `data.sql` only for seed rows (see below);
schema itself comes from JPA DDL generation as the rest of the project does — **confirm
that assumption in Increment 0** and match whatever the project actually does.

Conventions to carry over from the existing entities:

- List columns are **pipe-delimited `TEXT`**, matching `aiHealthcareKeywords`,
  `tags`, `relatedSlugs`. Do this for `recipients` in ED-2 and for any list here.
- `parameters_json` is JSON in a `TEXT` column, serialised in the adapter, never in
  the domain record.
- Adapters map entity ⇄ domain record with plain `for` loops; **a JPA `@Entity` never
  escapes the persistence package**.

`countActiveByOwnerEmail` is a derived query over `status IN ('QUEUED','RUNNING')`.

### Seed data

Seed four canned prompts into `enterprise_data_prompts` via `data.sql`, so the console is
useful the moment it loads:

| promptId | feedId | Parameters |
|---|---|---|
| `legislation-by-state` | `legislation` | `state` (ENUM over `StateCode`), `year` (INTEGER) |
| `legislation-recent-changes` | `legislation` | `days` (INTEGER, default 30) |
| `regulatory-ai-clearances` | `regulatory` | `days` (INTEGER, default 30), `body` (ENUM: FDA, CMS, ONC) |
| `articles-by-topic` | `articles` | `topic` (STRING), `days` (INTEGER, default 7) |

### Tests

`@DataJpaTest` for each adapter: round-trip, `findByJobIdAndOwnerEmail` returns empty for
a different owner (this is the tenancy guarantee — test it explicitly),
`countActiveByOwnerEmail` counts only `QUEUED`/`RUNNING`, `findStaleRunning` respects the
heartbeat cutoff, pipe-delimited round-tripping including the empty-list and
single-element cases.

Stop for review.

---

## Increment 4 — Configuration, executor, artifact store and the log writer

Package `infrastructure.config` for properties and beans; `infrastructure.enterprise` (new
sub-package) for the store and log adapters.

**`EnterpriseDataProperties`** — `@ConfigurationProperties(prefix = "aihealthcare.enterprise.data")`,
registered on `AppConfig`'s `@EnableConfigurationProperties`. Fields mirror the YAML block
in the design document §14 (nested `Executor` and `Remote` types; the `Push` block is
ED-2 — do not add it now). Add the YAML block to `application.yml` with the same
explanatory comment style the file already uses.

**`EnterpriseDataConfig`** — defines a dedicated
`ThreadPoolTaskExecutor enterpriseDataExecutor` bean from the properties, with
`CallerRunsPolicy`, a named thread prefix, and `setWaitForTasksToCompleteOnShutdown(true)`
plus a shutdown timeout. Also creates the artifact and log directories on startup if
absent, and logs at `INFO` where they are.

**`FileDataArtifactAdapter implements DataArtifactPort`** — writes
`{artifact-directory}/{jobId}.{ext}`, computes SHA-256 while writing, refuses to write
past `max-artifact-bytes` (throws, so the job fails cleanly rather than filling the disk).
**Filenames derive only from the UUID job id and the format's extension — never from any
user-supplied string.** Say so in the Javadoc.

**`FileDataJobLogAdapter implements DataJobLogPort`** — writes
`{log-directory}/{jobId}.log`. Line format, exactly:

```
{ISO-8601 UTC millis}  {LEVEL}  job={jobId}  phase={PHASE}  {key=value pairs}
```

Every `detail` string passes through `LogSanitizer` before it is written. The returned
`DataJobLog` tracks the byte offset so `read(jobId, from)` can tail. Reads clamp `from` to
the file length and return an empty string rather than throwing when the file does not
yet exist.

**Logback** — add an appender in `logback-spring.xml` (create it if the project uses
Boot's default) binding logger `com.wgblackmon.aihealthcare.infrastructure.enterprise` to
`logs/enterprise-data.log` with size-and-time rolling, `additivity=false` so this traffic
does not also flood `app.log`.

### Tests

- `FileDataArtifactAdapterTest` — `@TempDir`; write/read round-trip, SHA-256 correctness,
  oversize rejection, extension per format.
- `FileDataJobLogAdapterTest` — `@TempDir`; line format matches the documented shape,
  tailing from an offset returns only the new bytes, reading a missing file returns
  empty, a detail containing an email address comes back sanitised.

Stop for review.

---

## Increment 5 — Data source adapters

Package `infrastructure.enterprise.source`. Four adapters, each a `@Component`
implementing `EnterpriseDataSourcePort`.

### `ArticleCorpusDataSourceAdapter` — `feedId = "articles"`, `INTERNAL_CORPUS`

Delegates to `ArticleSearchQueryPort` / `ArticleSpecificationBuilder`. Maps
`DataQueryPlan` → `ArticleSearchCriteria`. Columns: `articleId`, `title`, `url`, `topic`,
`sourceName`, `sourceTier`, `publishedAt`, `author`. `chartHint = TIME_SERIES`.

### `LegislationCorpusDataSourceAdapter` — `feedId = "legislation"`, `INTERNAL_CORPUS`

Delegates to `StateLawPort`. Columns must match the shape of the existing
`health-ai-legislation.csv` export so downstream consumers are not surprised — **open
that file during Increment 0 and record its exact header row in your report**, then make
this adapter's columns match it. `chartHint = CATEGORY_BAR` (by state).

### `RegulatoryCorpusDataSourceAdapter` — `feedId = "regulatory"`, `INTERNAL_CORPUS`

Delegates to `RegulatoryEventPort`. Columns: `eventId`, `eventType`, `regulatoryBody`,
`referenceNumber`, `applicantName`, `deviceName`, `decisionDate`, `aiKeywords`.
`chartHint = TIME_SERIES`.

### `AiSynthesisDataSourceAdapter` — `feedId = "ai-synthesis"`, `LLM_SYNTHESIS`

Delegates to `ConductAiSearchUseCase`. Returns a `DataSet` whose `narrative` holds the
synthesis, whose `citations` hold the `SourceCitation` list, and whose `rows` hold the
retrieved source records (so a CSV of this feed is still useful). Honours the tier's
monthly query limit through the same `UsageTrackingPort` counter the existing AI search
uses — **do not introduce a second counter for the same underlying cost**.

### Shared rules for all four

- Honour `request.rowLimit()`; when the underlying result exceeds it, truncate and set
  `DataSet.truncatedAtRows` — never silently drop rows.
- Write `FETCH_START` (naming the adapter) and `FETCH_END` (`rows=`, `durationMs=`) to
  the supplied `DataJobLog`.
- `for` loops for every mapping. No Streams.
- Null-safe cell rendering: a null value becomes an empty string, never the literal
  `"null"`.

### Tests

One `*AdapterTest` each, with the delegated port mocked: happy path column/row mapping,
row-limit truncation sets the flag, empty result returns an empty `DataSet` (not null),
and the two log phases are written. For `AiSynthesisDataSourceAdapterTest`, assert the
narrative and citations survive and that no live AI call is made (mock the use case, per
the `spring-ai` skill).

Stop for review.

---

## Increment 6 — The customer-side HTTPS connector (security-critical)

Package `infrastructure.enterprise.source`.

**`HttpJsonRemoteDataSourceAdapter implements EnterpriseDataSourcePort`** —
`feedId = "customer-remote"`, `CUSTOMER_REMOTE`. Resolves the `RemoteConnection` named by
`request.connectionId()` (scoped to the owner), calls it with `RestClient`, and flattens
the JSON array in the response body into `DataSet` columns and rows.

**`RemoteEndpointGuard`** — a separate, independently testable component in the same
package holding every safety rule, because these must be provable in isolation:

1. **Scheme** — reject anything that is not `https`.
2. **Host allow-list** — when `remote.allowed-hosts` is non-empty, the host must be in it.
3. **Resolved-IP blocklist** — resolve the host with `InetAddress.getAllByName` and reject
   if *any* resolved address is loopback, link-local, site-local, any-local, multicast,
   or in `169.254.0.0/16`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`,
   `::1`, or `fc00::/7`. Check the **address**, not the hostname string.
4. **No redirects** — build the `RestClient` on a request factory with redirect following
   disabled; treat any `3xx` as an error. (A redirect to `169.254.169.254` is the classic
   bypass of check 3.)
5. **Timeouts** — connect and read timeouts from properties, both applied.
6. **Response cap** — stop reading at `remote.max-response-bytes` and fail the job rather
   than buffering an unbounded body.
7. **Secret resolution** — `RemoteConnection.secretRef` is looked up from the environment
   (or AWS Secrets Manager if the project already has a client — check in Increment 0);
   the resolved value is attached as a header and **never** logged, returned, or rendered.

> **Why this matters, in one sentence for the Javadoc:** the application runs on EC2, so
> an unguarded outbound fetcher pointed at `169.254.169.254` would hand an attacker the
> instance role's credentials.

Also add a note to `docs/architecture.md` (Deployment shape section) that the EC2 instance
must have IMDSv2 required (`HttpTokens=required`) as defence in depth.

### Tests — `RemoteEndpointGuardTest` is the most important test class in this slice

Explicitly assert rejection of: `http://example.com`, `https://127.0.0.1/x`,
`https://169.254.169.254/latest/meta-data/`, `https://10.1.2.3/x`,
`https://192.168.0.5/x`, `https://[::1]/x`, a host absent from a non-empty allow-list, and
a host whose DNS resolves to a private address (mock the resolver). Assert acceptance of a
public HTTPS host that passes every check.

`HttpJsonRemoteDataSourceAdapterTest` uses `MockRestServiceServer`: JSON array → rows,
nested-object flattening, a `302` response fails the job, an oversize body fails the job,
and the auth header is present but the secret never appears in any log line captured
during the test.

Stop for review.

---

## Increment 7 — The domain service and prompt resolution

### `domain.service.EnterpriseDataService implements RequestEnterpriseDataUseCase`

Constructor-injected with: `List<EnterpriseDataSourcePort>` (built into a
`Map<String, EnterpriseDataSourcePort>` in the constructor with a `for` loop),
`DataJobPort`, `DataArtifactPort`, `DataJobLogPort`, `CannedPromptPort`,
`PromptToQueryPort`, `DataAccessAuditPort`, `UsageTrackingPort`, `TierGatingService`,
`Clock`.

> Note: this class lives in `domain.service` like its siblings, and therefore may not use
> Lombok's `@Slf4j`. Follow whatever the existing `domain.service` classes do for logging
> (`AiSearchService`, `DataExportService`) — confirm in Increment 0 and match it exactly.
> If existing domain services do use `@Slf4j`, follow the codebase, not the written rule,
> and note the discrepancy in your report.

`submit(DataRequest)` executes this order, writing a log line at each step:

1. Resolve the feed by `feedId`; unknown → `IllegalArgumentException` → `400`.
2. `TierGatingService` — assert ENTERPRISE. Denied → audit `TIER_DENY`, throw → `403`.
3. `UsageTrackingPort` — monthly job quota. Over → audit `QUOTA_DENY` → `429`.
4. `DataJobPort.countActiveByOwnerEmail` vs `max-concurrent-jobs-per-account`. Over →
   audit `CONCURRENCY_DENY` → `429` with `Retry-After`.
5. Clamp `rowLimit` to `min(requested, feed.maxRowLimit, max-rows-per-job)`.
6. Resolve the plan:
   - `promptId` present → load the `CannedPrompt`, check its `minTier`, validate every
     supplied parameter against the prompt's `DataParameter` list (required present, enum
     values in range, integers parseable, dates ISO), then build the `DataQueryPlan`
     directly. **No LLM call.**
   - `promptText` present → `PromptToQueryPort.resolve(...)`, then run the returned plan
     through the *same* validator. A plan referencing a different `feedId`, an unknown
     enum value, or a limit above the clamp is rejected with
     `errorType = UNRESOLVABLE_PROMPT`, and the rejected plan is written to the log.
   - Neither present → build a plan from `parameters` alone.
7. Persist the job as `QUEUED`, audit `SUBMIT`, and hand it to the executor.
8. Return the job immediately.

Execution (in `EnterpriseDataJobRunner`, next section) does: `RUNNING` → `fetch` →
render → `DataArtifactPort.write` → `SUCCEEDED`, with `try/catch` mapping any exception to
`FAILED` plus an `errorType`, and a `finally` that always closes the log with a
`COMPLETE` line. Heartbeat every 10 s while running.

Every read method (`getJob`, `readLog`, and the controller's artifact stream) resolves by
`(id, ownerEmail)` **in the query**, never by id followed by a check.

### `infrastructure.enterprise.EnterpriseDataJobRunner`

`@Component` holding the `@Async("enterpriseDataExecutor")` execution method, the
heartbeat, the timeout enforcement, and the rendering step. Kept out of the domain
service so the domain stays framework-free and so ED-2 can submit to it directly.

Rendering reuses whatever `DataExportService` already does for CSV/JSON; if that logic is
not reusable as-is, extract it into a `DataSetRenderer` used by both — **extract, do not
duplicate**.

### `infrastructure.ai.PromptToQueryAdapter implements PromptToQueryPort`

`ChatClient` + `BeanOutputConverter<DataQueryPlan>`. New prompt template at
`src/main/resources/prompts/enterprise-query-plan.txt`, loaded through the existing
`PromptLoaderService`. The template describes the feed, its parameters and their allowed
values, and instructs the model to emit only the plan JSON. Temperature `0.0`.

The template must contain, in words the model will follow: *you are producing a filter
plan, not a query; you must not output SQL, table names, or any field not listed.* The
adapter treats a null or unparseable conversion as a rejection, never as an empty plan
(an empty plan would silently export the whole table).

### Tests

`EnterpriseDataServiceTest` with every port mocked and a fixed `Clock`:

- tier denial → audit written, nothing queued
- quota denial → audit written, nothing queued
- concurrency denial → audit written, nothing queued
- unknown feed → `400`-mapped exception
- canned prompt: missing required parameter → rejected; out-of-range enum → rejected;
  valid → plan built with no `PromptToQueryPort` interaction (`verifyNoInteractions`)
- free text: plan for a different `feedId` → rejected; limit above the clamp → clamped or
  rejected per spec; valid → queued
- `getJob` for another owner → empty/`404`
- happy path → job persisted `QUEUED`, `SUBMIT` audited, runner invoked once

`PromptToQueryAdapterTest` mocks the `ChatClient` per the `spring-ai` skill (never a live
call) and asserts that a response containing a `DROP TABLE` string still yields either a
valid constrained plan or a rejection — never anything executable.

Stop for review.

---

## Increment 8 — Web layer

### `web.controller.EnterpriseDataRestController`

Implements the generated OpenAPI delegate for `/api/v1/enterprise/data/**`. Thin —
resolves the principal's email, delegates to `RequestEnterpriseDataUseCase`, maps domain
records to generated DTOs. `202` with a `Location` header on submit. Artifact streaming
uses `StreamingResponseBody` with `Content-Disposition: attachment; filename="…"`.

### `web.controller.EnterpriseConnectionRestController`

CRUD for `RemoteConnection`. The response DTO **must not contain `secretRef`'s value**;
it may echo the reference name. Add an explicit test for this.

### `web.controller.EnterpriseDataConsoleController`

- `GET /enterprise/data` → `enterprise-data-console.html`, model: feeds, canned prompts,
  recent jobs, tier limits and current usage.
- `GET /enterprise/data/jobs/fragment` → `fragments/enterprise-job-rows.html`.
- `GET /enterprise/data/jobs/{jobId}/log/fragment?from=` →
  `fragments/enterprise-log-tail.html`.
- `GET /enterprise/data/jobs/{jobId}/preview` → `fragments/enterprise-job-preview.html`
  (KPI tiles, first 50 rows, chart payload as JSON, citations).

### `SecurityConfig` changes — the only edit to an existing security class

- `/enterprise/**` requires authentication **and** the ENTERPRISE authority/tier check the
  project already uses for `SUBSCRIBER`-gated pages.
- `/api/v1/enterprise/**` added to the CSRF ignore list beside the other `/api/v1/**`
  entries so `X-API-Key` callers work.
- Confirm `ApiRateLimitFilter` covers the new API paths; if it is path-scoped, extend the
  scope rather than adding a second filter.

### Tests

`@WebMvcTest` for each controller with the use case mocked:

- unauthenticated → `302`/`401`
- authenticated FREE/SUBSCRIBER → `403`
- ENTERPRISE happy path → `202` with a `Location` header
- another owner's job → `404`
- artifact for a `RUNNING` job → `409`
- artifact for an expired job → `410`
- connection response body contains no secret value

Stop for review.

---

## Increment 9 — The console page

`src/main/resources/templates/enterprise-data-console.html`, plus the three fragments
above. Reuse `fragments/head.html`, `fragments/nav.html` (add an `enterprise` nav entry
visible only to ENTERPRISE), `fragments/footer.html`, `fragments/tier-utils.html`, the
existing Tailwind classes, HTMX, Alpine and `htmx-csrf.js`. Match the visual language of
`admin-pipelines.html` and `webhooks.html` — do not invent a new style.

**Tab 1 · Run.** Feed `<select>`; a canned-prompt `<select>` filtered to the chosen feed
that renders its `DataParameter` list into a form via Alpine; a "or ask in your own words"
`<textarea>` as the alternative (choosing one disables the other); format and row-limit
controls; Submit posts JSON to the REST endpoint and switches to Tab 2.

**Tab 2 · Jobs.** Table polled with
`hx-get="/enterprise/data/jobs/fragment" hx-trigger="load, every 2s"`. The fragment
**omits the polling trigger when no job is `QUEUED` or `RUNNING`**, so an idle page stops
polling. Columns: submitted, feed, prompt, status pill, rows, size, duration, actions
(Preview / Download data / Download log). Preview swaps the preview fragment inline.

**Preview fragment.** Four KPI tiles (rows, bytes, duration, sources); the first 50 rows
in a real `<table>` inside an `overflow-x-auto` wrapper; one Chart.js chart selected by
the feed's `chartHint` (`TIME_SERIES` → line by date; `CATEGORY_BAR` → horizontal bar;
`NONE` → no chart); for `LLM_SYNTHESIS`, the narrative above the table and the citation
list below it. Follow the chart conventions already used on `dashboard.html`.

**Tab 3 · Schedules.** Render a disabled placeholder card reading "Scheduled delivery
arrives in the next slice." **Do not build it.**

**Tab 4 · Log.** A `<pre class="font-mono text-xs">` bound to the selected job, polled via
the log-tail fragment with a `from` offset held in Alpine state, appending as it grows.
A "Download full log" button beside it.

No test is required for templates per the selective-testing rule, but the controller
tests above must assert the correct view names and model attributes.

---

## Increment 10 — Housekeeping schedulers

Package `infrastructure.scheduler`, following the existing externalised-cron pattern.

**`EnterpriseDataJobReaper`** — `@Scheduled(cron = "${aihealthcare.enterprise.data.reaper-cron}")`.
Finds `RUNNING` jobs whose `heartbeatAt` is older than `stale-job-reap-minutes`, marks
them `FAILED` with `errorType = ORPHANED`, appends to their log, audits. This is what
stops a deploy leaving ghost jobs spinning in the UI forever.

**`EnterpriseDataRetentionScheduler`** — `@Scheduled(cron = "${aihealthcare.enterprise.data.retention-cron}")`,
default 03:15 UTC (deliberately before the 04:00 harvest wave). Deletes artifacts and log
files past `expires_at`, marks their jobs `EXPIRED`, leaves the audit rows alone.

Tests: both with a fixed `Clock` and mocked ports — due/not-due boundaries, and the
assertion that a `SUCCEEDED` job with a live artifact is untouched.

---

## Increment 11 — Documentation

- `docs/architecture.md`: add the slice to the Completed Slices table; add the five new
  tables to Persistence Schema; add the new pages to Thymeleaf UI Pages; add the new
  endpoints to REST API Surface; add the two new schedulers to Scheduler Summary; add the
  IMDSv2 note to Deployment Shape.
- `CLAUDE.md`: add an "Enterprise Data Access (Slice ED-1)" section listing the new domain
  types and ports in the same table style as the existing sections, and stating the two
  invariants that must survive future edits: **(a) no natural-language-to-SQL, ever;
  (b) `DataQueryPlan`'s field set is closed and changing it is a security review.**
- `docs/CONVENTIONS.md`: leave untouched unless a genuinely new convention emerged.
- `README.md`: one short paragraph under the feature list.

---

## Working agreement

- One increment at a time. After each: show the diff summary, run **only** the tests for
  the classes you changed (per the selective-test-execution rule in `CLAUDE.md`), report
  the pass/fail count, and stop for review.
- If anything in the existing codebase contradicts this spec — a different port
  signature, a different tier enum, a different logging convention in `domain.service` —
  **stop and ask**. Do not improvise a reconciliation.
- Do not write a class, method or field that no increment in this slice requires.
- Never log a secret, an API key, a resolved `secretRef` value, or an unsanitised
  customer prompt.

## Deferred to later slices — do not start

- Anything scheduled, any email, any `enterprise_push_schedules` table — that is ED-2.
- JDBC and S3 customer connectors — ED-3.
- S3/SFTP/webhook push transports — ED-4.
- SSE progress streaming, a synchronous fast path, SQS or Fargate execution.
- Team-scoped sharing of jobs and artifacts (the `teamId` column exists but is written
  as `null` in this slice).

## Definition of done

- `mvn verify` green.
- An ENTERPRISE user can submit a canned-prompt job and a free-text job from the console,
  watch both run, preview the results with a chart, download the CSV, and download the
  per-job log.
- A FREE and a SUBSCRIBER user both get `403` from every enterprise path.
- `RemoteEndpointGuardTest` passes every SSRF rejection case listed in Increment 6.
- Killing the JVM mid-job leaves a job that the reaper marks `FAILED / ORPHANED` within
  `stale-job-reap-minutes`.
- `data-exports/logs/{jobId}.log` reads top-to-bottom as a complete, sanitised account of
  the run.
