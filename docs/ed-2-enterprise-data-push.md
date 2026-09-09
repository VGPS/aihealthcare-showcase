# Claude Code Prompt — AIHealthcare: Slice ED-2, Enterprise Data PUSH (Spec-Driven Development)

You are working in the AIHealthcare project at `C:\workspaces\SpringAIClaude\AIHealthcare`.

**Prerequisite: Slice ED-1 (`docs/ed-1-enterprise-data-pull.md`) must be complete
and green before this slice begins.** ED-2 adds no new execution machinery — it adds a
*trigger* and a *sink* on top of ED-1's job core.

Before writing any code, read `CLAUDE.md`, `.claude/CONVENTIONS.md`,
`docs/CONVENTIONS.md`, `docs/enterprise-data-access-design.md` (especially §7 on why this
slice uses a database sweeper rather than dynamic scheduling, and §10 on size-aware
delivery), and the ED-1 spec so you know exactly what already exists.

---

## Context and intent

ENTERPRISE customers can now pull data on demand. This slice lets them say *"send me this
every weekday at 07:00"* and have it arrive by email without anyone touching the console.

Design goals, in priority order:

1. **A scheduled push is a PULL job with a different trigger.** Reuse
   `EnterpriseDataService.submit(...)` and `EnterpriseDataJobRunner` exactly as they are.
   If this slice needs to change either of them beyond adding a `scheduleId`, stop and
   ask — the ED-1 design was shaped specifically to make this unnecessary.
2. **Schedule state lives in the database, never in memory.** A JVM restart must not lose,
   duplicate, or silently stop a customer's schedule.
3. **Time zones are the customer's, not the server's.** "07:00 weekdays" means 07:00 where
   the customer is.
4. **Delivery degrades gracefully.** An extract too large to attach becomes a signed,
   expiring download link — the customer still gets their data, and SES never rejects the
   message.
5. **A broken schedule goes quiet, not loud.** Repeated failures deactivate the schedule
   and notify an admin; they do not email the customer an error every hour forever.

---

## Scope boundaries

- Do **not** change `EnterpriseDataService`'s public contract, `EnterpriseDataJobRunner`'s
  execution logic, the artifact store, the log writer, or any ED-1 data source adapter.
  The only permitted ED-1 touch-points are listed in Increment 3.
- Do **not** widen `TransactionalEmailPort` with an attachment method. A new
  `DataPushDeliveryPort` is introduced instead, for the reasons in design doc §10.
- No S3 push, no SFTP push, no webhook push. The `DataPushDeliveryPort` seam exists so
  those are later slices, not this one.
- No dynamic `TaskScheduler` registry. The sweeper is the design; see design doc §7.
- Same conventions as ED-1, without exception: Javadoc header with `@author Bill Blackmon`,
  `@version 1.0`, `@since 2026-09-08`, `@updated 2026-09-08`; `@Slf4j` outside `domain`
  with entry and exit `log.debug()`; **no Streams, `for` loops only**; records for
  immutable data; constructor injection only; `domain` stays JDK-only.

---

## Increment 0 — Reconnaissance

Read and report on:

| File | What to confirm |
|---|---|
| `domain/service/EnterpriseDataService.java`, `infrastructure/enterprise/EnterpriseDataJobRunner.java` | The exact `submit(DataRequest)` signature, and that `DataRequest` already carries `mode` and can carry a `scheduleId` |
| `domain/model/DataRequest.java`, `DataJob.java`, `DataJobMode.java` | That `PUSH` exists on `DataJobMode` and `scheduleId` exists on `DataJob` (both were added in ED-1) |
| `infrastructure/scheduler/DigestDeliveryScheduler.java` | The house pattern for a scheduled delivery job — cron externalisation, error isolation, logging |
| `infrastructure/delivery/TransactionalEmailAdapter.java`, `EmailDeliveryAdapter.java`, `domain/port/outbound/TransactionalEmailPort.java` | Which `JavaMailSender`/SES path is in use, whether `MimeMessageHelper` is already used anywhere, and the from-address convention |
| `domain/port/outbound/AdminNotificationPort.java`, `infrastructure/delivery/AdminNotificationAdapter.java` | How to raise an admin alert when a schedule auto-deactivates |
| `deploy/setup-ses-domain.sh` | The verified sending domain, so `push.from-address` defaults to something real |
| `infrastructure/config/EnterpriseDataProperties.java` | Where to add the nested `Push` block |
| `domain/model/SubscriptionTier.java`, `infrastructure/config/TierLimitProperties.java` | Where `monthlyPushRuns` goes |

Report one line per file. **Stop and ask if anything contradicts this spec.**

---

## Increment 1 — OpenAPI contract first

Add to `openapi.yaml` under the `enterprise-data` tag:

| Method | Path | Request | Response |
|---|---|---|---|
| `GET` | `/api/v1/enterprise/data/schedules` | — | `200` `[PushScheduleResponse]` |
| `POST` | `/api/v1/enterprise/data/schedules` | `PushScheduleRequest` | `201` `PushScheduleResponse` |
| `PUT` | `/api/v1/enterprise/data/schedules/{scheduleId}` | `PushScheduleRequest` | `200` |
| `DELETE` | `/api/v1/enterprise/data/schedules/{scheduleId}` | — | `204` |
| `POST` | `/api/v1/enterprise/data/schedules/{scheduleId}/run` | — | `202` `DataJobResponse` |
| `GET` | `/d/{token}` | — | `200` binary, `403` bad signature, `410` expired |

`PushScheduleRequest`: `label`, `feedId`, `promptId?`, `promptText?`, `parameters?`,
`format`, `cronExpression`, `zoneId`, `recipients` (array of email), `active`.

`PushScheduleResponse` adds: `scheduleId`, `nextRunAt`, `lastRunAt`, `lastStatus`,
`lastJobId`, `consecutiveFailures`, and **`nextRuns`** — an array of the next three fire
times, computed server-side. That field is small and it prevents most cron mistakes
before they ever run.

`400` responses must distinguish an invalid cron expression from an invalid zone id from
an invalid recipient — the customer needs to know which of the three they got wrong.

Regenerate DTOs and confirm the build is green.

---

## Increment 2 — Domain records, ports and validation

### `domain.model`

**`DataPushSchedule`** — record:

```
String        scheduleId
String        ownerEmail
String        label
String        feedId
String        promptId          // nullable
String        promptText        // nullable
Map<String,String> parameters
ExportFormat  format
String        cronExpression
String        zoneId
List<String>  recipients
boolean       active
Instant       nextRunAt         // nullable until first computed
Instant       lastRunAt         // nullable
DataJobStatus lastStatus        // nullable
String        lastJobId         // nullable
int           consecutiveFailures
Instant       createdAt
Instant       updatedAt
```

Compact constructor: non-blank `scheduleId`, `ownerEmail`, `label`, `feedId`,
`cronExpression`, `zoneId`; non-null `format`; at least one recipient; every recipient
matching a basic email shape; defensive copies of the map and list;
`consecutiveFailures >= 0`.

**It does not validate the cron expression itself** — `CronExpression` is Spring, and
`domain` is JDK-only. Validation happens in the application/infrastructure layer
(Increment 4). Say so in the record's Javadoc so the omission reads as deliberate.

**`PushDeliveryMode`** — enum: `ATTACHMENT`, `SIGNED_LINK`. Which one was used is recorded
on the audit entry, because "did they get the file or the link?" is the first support
question.

**`PushDeliveryResult`** — record: `String scheduleId`, `String jobId`,
`PushDeliveryMode mode`, `List<String> recipients`, `long byteSize`,
`Instant deliveredAt`, `boolean success`, `String errorMessage`.

Extend **`DataAccessAction`** with `SCHEDULE_CREATE`, `SCHEDULE_UPDATE`,
`SCHEDULE_DELETE`, `SCHEDULE_FIRE`, `PUSH_SEND`, `PUSH_FAIL`, `SCHEDULE_DEACTIVATE`,
`LINK_REDEEM`. (Adding enum values is the only change to an ED-1 domain type.)

### `domain.port.outbound`

**`DataPushSchedulePort`**:

```java
DataPushSchedule       save(DataPushSchedule schedule);
Optional<DataPushSchedule> findByScheduleIdAndOwnerEmail(String scheduleId, String ownerEmail);
List<DataPushSchedule> findByOwnerEmail(String ownerEmail);
List<DataPushSchedule> findDue(Instant now, int limit);
boolean                claim(String scheduleId, Instant observedNextRunAt, Instant newNextRunAt, Instant now);
void                   recordOutcome(String scheduleId, String jobId, DataJobStatus status, Instant completedAt);
void                   deactivate(String scheduleId, String reason);
void                   delete(String scheduleId, String ownerEmail);
```

`claim(...)` is the concurrency primitive and deserves a paragraph of Javadoc: it performs
a **conditional** update guarded on `next_run_at` still equalling the value the sweeper
observed, returning `true` only when exactly one row changed. That single row is what
guarantees a schedule fires once even if two sweeps overlap or a second instance is ever
introduced.

**`DataPushDeliveryPort`**:

```java
PushDeliveryResult deliver(DataPushSchedule schedule, DataJob job, DataArtifact artifact);
```

Javadoc: *the implementation decides attachment vs signed link based on artifact size; it
must never throw for a delivery failure — it returns a failed `PushDeliveryResult` so the
scheduler can record the outcome and apply back-off.*

**`SignedLinkPort`**:

```java
String  createToken(String jobId, Instant expiresAt);
Optional<String> verifyToken(String token, Instant now);   // returns jobId when valid
```

### `domain.port.inbound`

**`ManageDataPushSchedulesUseCase`**:

```java
DataPushSchedule       create(DataPushSchedule schedule);
DataPushSchedule       update(String scheduleId, String ownerEmail, DataPushSchedule schedule);
void                   delete(String scheduleId, String ownerEmail);
List<DataPushSchedule> list(String ownerEmail);
DataJob                runNow(String scheduleId, String ownerEmail);
List<Instant>          previewNextRuns(String cronExpression, String zoneId, int count);
```

### Tests

`DataPushScheduleTest` — validation of each rule, defensive copying, zero recipients
rejected, a malformed recipient rejected.
`PushDeliveryResultTest` — construction and the failure shape.

Stop for review.

---

## Increment 3 — Persistence and the claim semantics

`EnterprisePushScheduleEntity` + `EnterprisePushScheduleRepository` +
`DataPushScheduleAdapter` in `infrastructure.persistence`. DDL is in design doc §15;
follow it exactly, including `idx_eps_due` on `(active, next_run_at)` — the sweeper's
query runs every minute and must hit an index.

`recipients` is a **pipe-delimited `TEXT` column**, matching the existing convention used
by `aiHealthcareKeywords`, `tags` and `relatedSlugs`.

`findDue(now, limit)` → `WHERE active = TRUE AND next_run_at IS NOT NULL AND next_run_at <= :now ORDER BY next_run_at ASC`,
bounded by `limit` so one sweep can never pick up an unbounded backlog.

`claim(...)` is a `@Modifying @Query` returning `int`:

```sql
UPDATE enterprise_push_schedules
   SET next_run_at = :newNextRunAt,
       last_run_at = :now,
       last_status = 'RUNNING',
       updated_at  = :now
 WHERE schedule_id = :scheduleId
   AND active      = TRUE
   AND next_run_at = :observedNextRunAt
```

Return `rowsUpdated == 1`. Note in the Javadoc that `next_run_at` advances **before** the
job runs, so a crashing job cannot re-fire in a tight loop.

### The only permitted ED-1 touch-points in this slice

1. `EnterpriseDataJobEntity` / `DataJobAdapter` — start writing the `schedule_id` column
   that ED-1 already declared, and add `findByScheduleId(String, int limit)` for the
   schedule's run history.
2. `DataAccessAction` — new enum values (Increment 2).
3. `EnterpriseDataProperties` — the nested `Push` block (Increment 5).
4. `TierLimitProperties` / tier YAML — `monthlyPushRuns`.
5. `enterprise-data-console.html` — replace the ED-1 placeholder card with the real
   Schedules tab (Increment 7).

Anything beyond this list means stopping and asking.

### Tests

`@DataJpaTest DataPushScheduleAdapterTest`:

- round-trip including pipe-delimited recipients (empty, one, many)
- `findByScheduleIdAndOwnerEmail` returns empty for a different owner
- `findDue` excludes inactive, excludes future, respects the limit and the ordering
- **`claim` returns `true` once and `false` on an immediate second call with the same
  `observedNextRunAt`** — this is the single most important test in the slice
- `claim` returns `false` for an inactive schedule
- `recordOutcome` sets `last_status`, `last_job_id`, and increments or resets
  `consecutive_failures` correctly

Stop for review.

---

## Increment 4 — Cron validation and next-run computation

`infrastructure.enterprise.push.CronScheduleCalculator` — a `@Component` wrapping Spring's
`CronExpression` and `ZoneId`. Kept as its own class so it can be unit-tested with a fixed
`Clock` without a scheduler anywhere near it.

```java
boolean        isValid(String cronExpression, String zoneId);
Instant        nextRunAfter(String cronExpression, String zoneId, Instant after);
List<Instant>  nextRuns(String cronExpression, String zoneId, Instant after, int count);
```

Rules:

- The project uses **Spring's 6-field cron format** (`second minute hour day month
  weekday`) everywhere in `application.yml`. Accept 6 fields. If a customer submits a
  5-field Unix expression, either reject it with a message naming the expected format, or
  normalise by prefixing `0 ` — **pick one, document it in the API description, and be
  consistent.** Silent normalisation of an ambiguous input is worse than a clear rejection.
- Compute against `ZonedDateTime.now(ZoneId.of(zoneId))` and convert the result to
  `Instant` for storage. Everything persisted is UTC; only the computation is zoned.
- Reject an unknown `zoneId` with a distinct error from an invalid cron.
- Enforce a **minimum interval** from `push.min-interval-minutes` (default 15). A customer
  submitting `* * * * * *` would otherwise queue a job every second. Reject with a message
  naming the minimum.

### Tests — `CronScheduleCalculatorTest`, all with a fixed `Clock`

- `"0 0 7 * * MON-FRI"` in `America/Chicago` at a known instant → the expected next three
  UTC instants, **including a run that crosses a DST boundary** (pick a date in March and
  one in November; the UTC offset must shift and the local time must stay 07:00)
- invalid cron → `isValid` false
- unknown zone → distinct failure
- an interval below the minimum → rejected

Stop for review.

---

## Increment 5 — Configuration, signed links, and email delivery

### Properties

Add the nested `Push` block to `EnterpriseDataProperties` and the YAML to
`application.yml`, in the file's existing commented style:

```yaml
      push:
        sweep-cron: "0 * * * * *"       # every minute, on the minute
        max-attachment-bytes: 8388608   # 8 MB — above this, send a signed link instead
        from-address: data@bigskylabs.ai
        max-recipients: 10
        max-schedules-per-account: 25
        min-interval-minutes: 15
        failure-threshold: 5            # consecutive failures before auto-deactivating
        due-batch-size: 50
```

Add `monthly-push-runs: 300` under `aihealthcare.tiers.enterprise`.

### `infrastructure.enterprise.push.HmacSignedLinkAdapter implements SignedLinkPort`

HMAC-SHA256 over `jobId + ":" + expiryEpochSeconds` using
`aihealthcare.enterprise.data.signing-secret`, encoded with URL-safe Base64 as
`{base64url(jobId:expiry)}.{base64url(mac)}`. Verification uses a
**constant-time comparison** (`MessageDigest.isEqual`) — a timing-safe compare is cheap
and its absence is a real finding in a security review.

If `signing-secret` is blank at startup: log a `WARN`, and make `createToken` throw. The
delivery adapter then falls back to attachment-only and records a warning on the job
rather than signing with an empty key.

### `infrastructure.delivery.EmailDataPushAdapter implements DataPushDeliveryPort`

- `artifact.byteSize() <= push.max-attachment-bytes` → `MimeMessageHelper(message, true)`
  with `addAttachment(fileName, new ByteArrayResource(bytes))`, plus the signed link in
  the body as a convenience. `mode = ATTACHMENT`.
- Otherwise → body carries the signed link only, and states the row count and size in
  plain words. `mode = SIGNED_LINK`.
- Recipients capped at `push.max-recipients`; the overflow is dropped and recorded as a
  warning, not silently ignored.
- Subject: `[AIHealthcare] {schedule.label} — {rowCount} rows — {date}`.
- Body: a small Thymeleaf template `templates/email/enterprise-push.html` with inline CSS,
  matching the newsletter email template's approach (Slice 44).
- **Never throws.** Any `MailException` is caught and returned as a failed
  `PushDeliveryResult` with the message. The scheduler owns the retry/back-off policy;
  the adapter owns delivery only.

### `web.controller.SignedDownloadController`

`GET /d/{token}` — verifies the token, loads the job, streams the artifact with
`Content-Disposition: attachment`. `403` on a bad signature, `410` on expiry or a deleted
artifact. Audits `LINK_REDEEM`. This path is **unauthenticated by design**, so it must be
added to `SecurityConfig`'s permitted paths and to the CSRF ignore list, and it must be
covered by `ApiRateLimitFilter` — an unauthenticated endpoint with no rate limit is a
brute-force target even with a 256-bit MAC. Serve it with `X-Robots-Tag: noindex,
nofollow` and `Cache-Control: no-store` so a forwarded link never lands in a search index
or a shared proxy cache. It reads the artifact through ED-1's `ConfinedFileStore` like
every other filesystem access in this feature — no new path handling.

### Tests

`HmacSignedLinkAdapterTest` — round-trip; a tampered payload fails; a tampered MAC fails;
an expired token fails; a token signed with a different secret fails; blank secret throws.

`EmailDataPushAdapterTest` — mock `JavaMailSender`, capture the `MimeMessage`: under the
threshold produces an attachment with the right filename; over the threshold produces no
attachment and a body containing the link; a `MailException` yields
`success = false` rather than propagating; recipient overflow is capped.

`SignedDownloadControllerTest` (`@WebMvcTest`) — `403` bad signature, `410` expired,
`200` happy path, no session required.

Stop for review.

---

## Increment 6 — The sweeper and the application service

### `domain.service.DataPushScheduleService implements ManageDataPushSchedulesUseCase`

CRUD with the same discipline as ED-1: ENTERPRISE tier asserted on every method, ownership
resolved **in the query** via `findByScheduleIdAndOwnerEmail`, `max-schedules-per-account`
enforced on create, cron and zone validated through `CronScheduleCalculator`,
`nextRunAt` computed on create and on any update that changes the cron or the zone, and
every mutation written to `DataAccessAuditPort`.

`runNow(...)` builds a `DataRequest` with `mode = PULL` (it is a manual, interactive run —
it should count against the pull quota, not the push quota) carrying the schedule's
`feedId`, prompt and parameters, and submits it through the existing
`RequestEnterpriseDataUseCase`. It does **not** touch `nextRunAt`.

### `infrastructure.scheduler.EnterpriseDataPushScheduler`

```java
@Scheduled(cron = "${aihealthcare.enterprise.data.push.sweep-cron}")
public void sweep() { ... }
```

Per sweep:

1. `findDue(now, due-batch-size)`.
2. For each schedule, in a `for` loop, each iteration wrapped in its own `try/catch` so
   one bad schedule cannot abort the sweep — the error-isolation pattern
   `StartupPipelineOrchestrator` and `DigestDeliveryScheduler` already use:
   a. compute `newNextRunAt` from the cron and zone;
   b. `claim(scheduleId, observedNextRunAt, newNextRunAt, now)` — if `false`, skip
      silently, someone else has it;
   c. check the owner's `monthlyPushRuns` quota; over → audit `QUOTA_DENY`, record the
      outcome as failed, continue;
   d. submit a `DataRequest` with `mode = PUSH` and `scheduleId` set, via
      `RequestEnterpriseDataUseCase.submit(...)`;
   e. register a completion callback (or poll the job to a terminal state on the push
      executor — **not** on the sweeper thread) that, on `SUCCEEDED`, loads the artifact
      and calls `DataPushDeliveryPort.deliver(...)`, then `recordOutcome(...)` and audits
      `PUSH_SEND`; on `FAILED`, audits `PUSH_FAIL` and increments
      `consecutiveFailures`;
   f. when `consecutiveFailures >= push.failure-threshold`, call
      `deactivate(scheduleId, reason)`, audit `SCHEDULE_DEACTIVATE`, and raise an
      `AdminNotificationPort` alert naming the schedule, the owner and the last error.

The sweeper thread must never block on job execution. Keep it to: query, claim, submit,
return. Delivery happens on the enterprise executor.

### Tests

`DataPushScheduleServiceTest` — mocked ports, fixed `Clock`: tier denial; another owner's
schedule → not found; `max-schedules-per-account` enforced; invalid cron rejected with the
cron-specific error; invalid zone rejected with the zone-specific error; `nextRunAt`
recomputed on a cron change but not on a label change; `runNow` submits with `mode = PULL`
and leaves `nextRunAt` untouched.

`EnterpriseDataPushSchedulerTest` — mocked ports, fixed `Clock`:

- a due schedule is claimed and submitted exactly once
- a failed claim results in **no** submission
- an exception on schedule A does not prevent schedule B being processed
- quota exceeded → no submission, outcome recorded
- reaching `failure-threshold` deactivates and notifies exactly once
- `nextRunAt` is advanced before submission

Stop for review.

---

## Increment 7 — Web layer and the Schedules tab

### `web.controller.EnterpriseScheduleRestController`

Implements the generated delegate for `/api/v1/enterprise/data/schedules/**`. `201` on
create with the computed `nextRuns[3]` in the body; `202` on `run`.

### Console — replace the ED-1 placeholder

In `enterprise-data-console.html`, swap Tab 3's placeholder card for the real UI, plus a
new `fragments/enterprise-schedule-rows.html`.

- **Create/edit form:** label; feed `<select>`; canned-prompt `<select>` (reusing the
  Tab 1 parameter-form component); format; cron expression with a **live "next three
  runs" preview** fetched from the API as the field changes (debounced) and rendered in
  the customer's chosen zone *and* in UTC side by side; zone `<select>` defaulting to the
  browser's `Intl.DateTimeFormat().resolvedOptions().timeZone`; recipients as a
  chip-style multi-entry input.
- **Table:** label, feed/prompt, cron (human-readable), zone, next run, last run, last
  status pill, recipients count, active toggle, and actions — Edit, *Run now*, Delete.
  *Run now* submits and switches to the Jobs tab so the customer sees it execute.
- A schedule that was auto-deactivated shows an amber banner with the last error and a
  Reactivate button (reactivating resets `consecutiveFailures` to `0` and recomputes
  `nextRunAt`).

Match the visual language of `webhooks.html` — same card, table and button classes. Do not
invent a new style.

### `SecurityConfig`

`/d/**` permitted without authentication; added to the CSRF ignore list; confirmed covered
by `ApiRateLimitFilter`.

### Tests

`@WebMvcTest EnterpriseScheduleRestControllerTest` — non-ENTERPRISE → `403`; another
owner's schedule → `404`; invalid cron → `400` with the cron-specific error body; create →
`201` with three `nextRuns`; `run` → `202`.

---

## Increment 8 — Documentation

- `docs/architecture.md`: add ED-2 to Completed Slices; add `enterprise_push_schedules` to
  Persistence Schema; add `EnterpriseDataPushScheduler` to Scheduler Summary (with its
  config key, as every other row does); add the schedule endpoints and `/d/{token}` to the
  REST API Surface.
- `CLAUDE.md`: extend the Enterprise Data Access section with the push types and ports, and
  record the two invariants that must survive future edits: **(a) schedule state lives in
  the database and firing is claim-guarded — never move it into an in-memory scheduler;
  (b) `next_run_at` advances before the job runs.**
- `docs/enterprise-data-access-design.md`: mark ED-2 as delivered; move S3/SFTP/webhook
  push from "deferred" to a named ED-4 entry.
- `README.md`: one sentence in the feature list.

---

## Working agreement

- One increment at a time. After each: diff summary, run only the tests for the classes
  you changed, report pass/fail, stop for review.
- If this slice appears to require a change to `EnterpriseDataService` or
  `EnterpriseDataJobRunner` beyond passing a `scheduleId` through, **stop and ask** — that
  is a signal the ED-1 design needs revisiting, not a signal to edit it quietly.
- Never log a recipient list, a signing secret, or a signed token.
- ED-1's three hard rules carry forward unchanged: no filesystem call outside
  `ConfinedFileStore`; no outbound HTTP outside `RemoteEndpointGuard`; no tool calling,
  MCP client or agent loop on any customer-triggerable path — the sweeper included. The
  sweeper reads its own database rows and nothing else; it never fetches a schedule, a
  prompt or a configuration from a remote endpoint.

## Deferred — do not start

- S3, SFTP and webhook push transports (ED-4 — the `DataPushDeliveryPort` seam is ready).
- Per-recipient delivery preferences, digest batching of multiple schedules into one mail.
- Retry of a failed *delivery* (as opposed to a failed *job*) — today a failed delivery
  waits for the next scheduled run.
- Team-scoped schedules; `ownerEmail` remains the sole owner.

## Definition of done

- `mvn verify` green.
- An ENTERPRISE user creates a schedule from the console, sees the next three runs
  previewed in their own time zone, and receives an email with the CSV attached at the
  scheduled minute (verified locally against MailHog with a 15-minute test interval).
- An artifact larger than `max-attachment-bytes` arrives as a signed link, and that link
  downloads the file without a session and returns `410` after its TTL.
- Restarting the JVM does not lose, duplicate or stall any schedule.
- Two consecutive sweeps overlapping in time fire a due schedule exactly once
  (`DataPushScheduleAdapterTest` proves the claim; the scheduler test proves the wiring).
- Five consecutive failures deactivate the schedule, notify an admin once, and stop
  emailing the customer.
