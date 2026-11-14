# AIHealthcare — Feature Module Specs

> Extracted from CLAUDE.md 2026-11-14 to keep the base file compact.
> These are the locked-in architectural decisions and domain details for
> Market Analysis, State Legislation, and Enterprise Data modules.

---

## Market Analysis

### Overview

Tracks daily market-moving news in AI-powered healthcare — regulatory actions, public-company
earnings/guidance, funding rounds, and M&A — producing a ranked, fact-vs-speculation-separated
digest. Reuses the existing Deep Research + scheduled-job pattern already established for the
Trends pipeline, adding a lightweight market-data lookup step for any publicly traded company
mentioned in the news.

**Architecture decisions (locked in — do not re-litigate):**
- **No Flyway** — use JPA `@Entity` classes with the existing `ddl-auto: create-drop` pattern.
- **No Bedrock** — use the existing Spring AI `ChatClient` wired to the direct Anthropic API.
- **No React** — all UI is Thymeleaf server-rendered.
- **Flat packages** — `domain.model`, `domain.port.outbound`, `domain.service`, `infrastructure.ai`,
  `infrastructure.persistence`, `web.controller`. Do NOT create feature-scoped sub-packages.
- **Provider locked:** market data = Alpaca (free); private funding = Perplexity Search API;
  analyst ratings = Benzinga ($99/mo, Slice 3.2).

### Domain Model

**Records & enums (all in `domain.model`):**

| Type | Fields | Notes |
|------|--------|-------|
| `MarketNewsItem` | headline, summary, sourceUrls, publishedAt, category | 5 fields |
| `NewsCategory` | EARNINGS, REGULATORY, FUNDING, M_AND_A, MAJOR_PARTNERSHIP, LEGAL_ACTION, WORKFORCE, PRODUCT_LAUNCH, OTHER | enum (9 values) |
| `FactClassification` | CONFIRMED, SPECULATIVE | enum |
| `ImpactDimension` | REVENUE, EARNINGS, VALUATION, INVESTOR_SENTIMENT, FUTURE_GROWTH | enum |
| `ImpactAssessment` | dimension, direction (POSITIVE/NEGATIVE/NEUTRAL), rationale | 3 fields |
| `MarketImpactRank` | rank 1 (highest) .. 5 (lowest) | record with int validation |
| `AffectedCompany` | name, tickerSymbol (nullable), role, peerGroup | 4 fields |
| `PeerGroup` | AI_SCRIBE_DOCUMENTATION, VALUE_BASED_CARE_PLATFORM, DIAGNOSTIC_IMAGING_AI, DRUG_DISCOVERY_AI, DIGITAL_THERAPEUTICS, OTHER | enum |
| `MarketDigestEntry` | newsItem + impacts + factClassification + rank + companies + optional DealTerms/PrivateFundingRound/RegulatoryTracker | aggregate |
| `MarketDigest` | date, entries list, generatedAt | aggregate |
| `PrivateFundingRound` | companyName, roundStage, amountUsd, leadInvestors, announcedAt | Phase 3 |
| `DealTerms` | upfrontCashUsd, milestonePaymentsUsd, equityStakePct, royaltyPct, disclosedPortion | Phase 3 |
| `AnalystRatingChange` | firm, tickerSymbol, previousRating, newRating, previousPriceTarget, newPriceTarget, changedAt | Phase 3 |
| `GuidanceComparison` | tickerSymbol, priorGuidanceLow/High, newGuidanceLow/High, metric | Phase 2 |
| `RulemakingStage` | DISCUSSION_PAPER, COMMENT_PERIOD, DRAFT_GUIDANCE, FINAL_GUIDANCE, ENFORCEMENT | enum, Phase 2 |
| `Jurisdiction` | US_FDA, EU_AI_ACT, UK_MHRA, US_STATE, OTHER | enum, Phase 2 |
| `RegulatoryTracker` | jurisdiction, stage, docketId, commentDeadline, lastUpdatedAt | Phase 2 |

**Outbound ports (all in `domain.port.outbound`):**

| Port | Methods |
|------|---------|
| `MarketNewsResearchPort` | `findRecentAiHealthcareNews(Instant since)` → `List<MarketNewsItem>` |
| `MarketDataPort` | `getQuote(ticker)` → `Optional<Quote>` |
| `ImpactClassifierPort` | `classify(List<MarketDigestEntry>)` → `List<MarketDigestEntry>` |
| `MarketDigestPort` | `save(digest)`, `findByDate(date)`, `findLatest()`, `findBetween(from, to)` |
| `MarketDigestNotifierPort` | `notify(digest)` — called only when digest has ≥1 qualifying entry |
| `PrivateFundingPort` | `findRecentRounds(since, peerGroupFilter)` → Phase 3 |
| `AnalystRatingPort` | `findRecentChanges(ticker, since)` → Phase 3 |
| `GuidancePort` | `getPriorGuidance(ticker, metric)` + `recordGuidance(comparison)` → Phase 2 |
| `TickerWatchlistPort` | `findWatchedTickers(subscriberEmail)` → Phase 3 |

**Inbound port:** `ProduceMarketDigestUseCase` in `domain.port.inbound` —
`runDaily()`, `getDigest(date)`, `getLatest()`, `getRange(from, to)`

**Domain service:** `MarketDigestService` in `domain.service` — orchestrates news research →
LLM classification → market data enrichment → qualifying-bar filter → ranking → persistence → notification

### Qualifying Bar (`isMarketMoving`)

An entry qualifies when:
- `category == EARNINGS`, OR
- `category == REGULATORY`, OR
- `category == FUNDING AND dealSizeUsd > 50_000_000` (strict — exactly $50M does NOT qualify), OR
- `category == M_AND_A`, OR
- `category == MAJOR_PARTNERSHIP`

### Scheduler (YAML)

```yaml
aihealthcare:
  market-analysis:
    schedule: "0 0 12 * * *"   # daily noon UTC (7:00 AM Chicago)
    enabled: true
```

`MarketAnalysisScheduler` in `infrastructure.scheduler`. Dedicated async executor bean
`"marketAnalysisExecutor"` (corePoolSize=2, maxPoolSize=4).

### Persistence (JPA — no Flyway)

Four core Phase-1 entities:
- `MarketDigestEntity` — id BIGSERIAL PK, digestDate DATE UNIQUE, generatedAt TIMESTAMPTZ
- `MarketDigestEntryEntity` — id BIGSERIAL PK, digest FK, headline, summary, sourceUrls TEXT,
  category, factClassification, marketImpactRank SMALLINT (1-5), publishedAt, embedding VECTOR(1536) nullable
- `MarketDigestImpactEntity` — id BIGSERIAL PK, entry FK, dimension, direction, rationale
- `MarketDigestCompanyEntity` — id BIGSERIAL PK, entry FK, companyName, tickerSymbol nullable,
  role, peerGroup nullable, quotePrice, quoteChangePct, marketCap

pgvector cosine similarity (<=>) on `embedding` for 7-day rolling dedup
(threshold: `aihealthcare.market-analysis.dedup-similarity-threshold`, default 0.93).
Dedup suppresses notification only — every entry is always persisted.

### Infrastructure Adapters

| Adapter | Port | Package |
|---------|------|---------|
| `PerplexityMarketNewsAdapter` | `MarketNewsResearchPort` | `infrastructure.research` |
| `AlpacaMarketDataAdapter` | `MarketDataPort` | `infrastructure.ai` |
| `MarketImpactClassifierAdapter` | `ImpactClassifierPort` | `infrastructure.ai` |
| `MarketDigestNotifierAdapter` | `MarketDigestNotifierPort` | `infrastructure.delivery` |
| `MarketDigestAdapter` | `MarketDigestPort` | `infrastructure.persistence` |

### Testing Notes

- Unit test `isMarketMoving()` against each `NewsCategory` boundary, especially the $50M
  funding threshold and exact-equals edge case.
- Contract-test `MarketImpactClassifierAdapter` with a fixed prompt fixture and golden JSON
  response (catch schema drift early).
- Integration test the full pipeline against a stubbed `MarketNewsResearchPort` (canned news
  items: one earnings beat + one sub-threshold funding round) to verify end-to-end filtering.

---

## State Health-AI Legislation Registry

### Overview

A durable, source-linked registry of every enacted U.S. state law regulating AI in healthcare.
Seeded from a 43-record JSON dataset (41 enacted laws across 24 states + 2 NOT_ENACTED for
dedup suppression). Kept current by a weekly source-freshness monitor and a weekly Perplexity
Search discovery sweep. New candidates require human promotion — nothing is auto-written.

**Architecture decisions:**
- **No Flyway** — JPA entities with `ddl-auto: create-drop`.
- **Flat packages** — same as Market Analysis above.
- **Seed data via `ApplicationRunner`** — idempotent upsert from `state_health_ai_laws_seed.json`.
- **Human review required** — `NewBillCandidate` records discovered by Perplexity are never
  auto-promoted. Admin promotes/dismisses via REST.
- **Thymeleaf UI** — no React.

### Domain Model

**Records & enums (all in `domain.model`):**

| Type | Fields | Notes |
|------|--------|-------|
| `StateLaw` | id (slug), stateCode, stateName, billNumber, title, yearEnacted, dateSigned, dateSignedNote, effectiveDate, effectiveDateNote, status, statusDetail, categories, regulatedParties, keyRequirements, enforcement, sources, notes, datasetVersion, createdAt, updatedAt | 21-field aggregate root |
| `StateCode` | AL, AK, AZ, ... DC (51 values) | enum |
| `LawStatus` | ENACTED, ENACTED_STAYED, NOT_ENACTED, PENDING | enum |
| `LawCategory` | PAYER_UTILIZATION_REVIEW, CLAIMS_DOWNCODING, PROVIDER_CLINICAL_USE, PROVIDER_DISCLOSURE_CONSENT, MENTAL_HEALTH_PSYCHOTHERAPY, CONSUMER_CHATBOTS, COMPREHENSIVE_AI_ACT, OTHER | enum (8 values) |
| `LawSource` | sourceType, url, lastFetchedAt, lastContentHash, lastHttpStatus, changedSinceLastReview | 6 fields |
| `SourceType` | OFFICIAL, SECONDARY | enum |
| `LawChangeEvent` | lawId, detectedAt, changeType, detail, reviewed | 5 fields |
| `NewBillCandidate` | stateCode, billNumber, title, summary, sourceUrls, discoveredAt, confidence, reviewed, promotedLawId | 9 fields |

**Outbound ports (all in `domain.port.outbound`):**

| Port | Methods |
|------|---------|
| `StateLawPort` | `upsert(StateLaw)`, `findById`, `findAll`, `findByState`, `findByCategory`, `findEffectiveBetween`, `search(query)` |
| `LawChangeEventPort` | `recordChangeEvent`, `findUnreviewed`, `markReviewed(eventId)` |
| `NewBillCandidatePort` | `saveCandidate`, `findUnreviewed`, `markReviewed`, `promote`, `dismiss` |
| `LawSourceMonitorPort` | `checkUrl(url)` → `SourceCheckResult` |
| `LegislationDiscoveryPort` | `discoverNewBills()` → `List<NewBillCandidate>` |
| `LawChangeNotifierPort` | `notifyChanges(List<LawChangeEvent>, List<NewBillCandidate>)` |

**Inbound port:** `ManageStateLawsUseCase` in `domain.port.inbound`
**Domain service:** `StateLawService` in `domain.service`

### Seed Data

File: `application/src/main/resources/data/state_health_ai_laws_seed.json`

Loaded by `StateLawSeedRunner` (`ApplicationRunner`, `@Order(1)`) — idempotent upsert by `id`.
Runs on every startup; existing records are updated if `datasetVersion` in JSON is newer.

### Scheduler (YAML)

```yaml
aihealthcare:
  legislation:
    source-check-schedule: "0 30 10 * * MON"    # Monday 10:30 UTC
    discovery-schedule: "0 0 11 * * MON"          # Monday 11:00 UTC
    enabled: true
```

`LegislationMonitorScheduler` in `infrastructure.scheduler`. Reuses `marketAnalysisExecutor`.

### Infrastructure Adapters

| Adapter | Port | Package |
|---------|------|---------|
| `StateLawAdapter` | `StateLawPort`, `LawChangeEventPort`, `NewBillCandidatePort` | `infrastructure.persistence` |
| `HttpSourceMonitorAdapter` | `LawSourceMonitorPort` | `infrastructure.ingestion.web` |
| `PerplexityLegislationDiscoveryAdapter` | `LegislationDiscoveryPort` | `infrastructure.research` |
| `LegislationChangeNotifierAdapter` | `LawChangeNotifierPort` | `infrastructure.delivery` |

---

## Enterprise Data Access — ED-1 (PULL)

### Overview

Async, job-based data export for ENTERPRISE-tier customers. Customers submit a data job
specifying a feed, optional prompt, format, and row limit. The job runs asynchronously on a
dedicated `ThreadPoolTaskExecutor`, produces an artifact (CSV/JSON), and makes it available
for download. Every action is audit-logged. Artifacts expire after 14 days (configurable).

### Security Invariants (must never be violated)

1. **No natural-language → SQL.** `DataQueryPlan`'s field set is closed — changing it requires security review.
2. **No filesystem call outside `ConfinedFileStore`.** No customer-supplied string reaches a `Path`.
3. **No outbound HTTP outside `RemoteEndpointGuard`.** Validated-IP pinning, no redirects, HTTPS only.
4. **No tool calling, no MCP client, no agent loop** on any customer-triggered path. One-shot LLM calls only.
5. **Ownership resolved IN the query** — `findByXAndOwnerEmail`, never `findById` + check. Other owner → 404 not 403.
6. **Secrets are references, never values.** `secretRef` holds env var name, resolved at request time, never logged.

### Domain Types

| Type | Kind | Fields |
|------|------|--------|
| `DataJob` | record | 21 fields — jobId, ownerEmail, teamId, mode, feedId, promptId, format, status, rowCount, byteSize, contentSha256, artifactPath, logPath, errorType, errorMessage, submittedAt, startedAt, completedAt, heartbeatAt, expiresAt, scheduleId |
| `DataJobStatus` | enum | QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, EXPIRED + `isTerminal()` |
| `DataJobMode` | enum | PULL, PUSH |
| `DataFeed` | record | 10 fields |
| `DataSourceKind` | enum | DB_QUERY, LLM_SYNTHESIS, CUSTOMER_REMOTE |
| `CannedPrompt` | record | 8 fields |
| `DataQueryPlan` | record | 8 fields — CLOSED security boundary |
| `DataRequest` | record | 14 fields with validation |
| `DataAccessAction` | enum | 12 values (SUBMIT through DOWNLOAD_LOG) |
| `RemoteConnection` | record | 9 fields — baseUrl must start with "https://" |

### Ports

| Port | Package | Kind |
|------|---------|------|
| `RequestEnterpriseDataUseCase` | `domain.port.inbound` | inbound — 7 methods |
| `ManageRemoteConnectionsUseCase` | `domain.port.inbound` | inbound — 5 methods |
| `DataJobPort` | `domain.port.outbound` | 9 methods incl. `findStaleRunning`, `findExpired` |
| `DataArtifactPort` | `domain.port.outbound` | write/read/delete/exists |
| `DataJobLogPort` | `domain.port.outbound` | open/read/delete |
| `DataAccessAuditPort` | `domain.port.outbound` | append/findByOwnerEmail |
| `CannedPromptPort` | `domain.port.outbound` | CRUD for canned prompts |
| `PromptToQueryPort` | `domain.port.outbound` | LLM query planning |
| `EnterpriseDataSourcePort` | `domain.port.outbound` | per-feed data execution |
| `RemoteConnectionPort` | `domain.port.outbound` | CRUD for remote connections |

### Infrastructure

| Class | Package | Purpose |
|-------|---------|---------|
| `ConfinedFileStore` | `infrastructure.enterprise` | Jail-breaking-safe file I/O |
| `RemoteEndpointGuard` | `infrastructure.enterprise.source` | HTTPS-only, IP-pinned HTTP client |
| `EnterpriseDataJobRunner` | `infrastructure.enterprise` | Async job executor with heartbeat + timeout |
| `EnterpriseDataJobReaper` | `infrastructure.scheduler` | Marks stale RUNNING jobs FAILED/ORPHANED (every 5 min) |
| `EnterpriseDataRetentionScheduler` | `infrastructure.scheduler` | Deletes expired artifacts/logs (daily 03:15 UTC) |

**289 tests across 18 test classes.**

---

## Enterprise Data PUSH — ED-2

### Overview

Scheduled push delivery layered on top of ED-1's job execution core. Per-customer cron
schedules with DB-sweeper scheduler, email delivery with size-aware attachment/signed-link
fallback, cron preview, and auto-deactivation after consecutive failures.

### Additional Invariants (must survive future edits)

7. **Schedule state lives in the database and firing is claim-guarded** — never move it into
   an in-memory `TaskScheduler`. The `claim()` call is a conditional UPDATE guarded on
   `next_run_at` still equalling the observed value. Guarantees at-most-once delivery.
8. **`next_run_at` advances before the push job runs.** Updated atomically in the claim step —
   a slow or failed job can never cause a double-fire.

### Domain Types

| Type | Kind | Fields |
|------|------|--------|
| `DataPushSchedule` | record | 19 fields — scheduleId, ownerEmail, label, feedId, promptId, promptText, parameters, format, cronExpression, zoneId, recipients, active, nextRunAt, lastRunAt, lastStatus, lastJobId, consecutiveFailures, createdAt, updatedAt |
| `PushDeliveryResult` | record | 4 fields |
| `PushDeliveryMode` | enum | ATTACHMENT, SIGNED_LINK |
| `CronScheduleCalculator` | class | DST-correct cron-to-Instant via `ZonedDateTime.now(ZoneId.of(zoneId))` |

### Ports

| Port | Package | Kind |
|------|---------|------|
| `ManageDataPushSchedulesUseCase` | `domain.port.inbound` | create/update/delete/list/runNow/previewNextRuns |
| `DataPushSchedulePort` | `domain.port.outbound` | save/findByOwnerEmail/findById/delete/findDue/claim/findByIdAndOwnerEmail |
| `DataPushDeliveryPort` | `domain.port.outbound` | `deliver(DataJob, List<String> recipients)` → `PushDeliveryResult` |
| `SignedLinkPort` | `domain.port.outbound` | `generateLink(jobId, ttl)` → URI |

### Infrastructure

| Class | Package | Purpose |
|-------|---------|---------|
| `DataPushScheduleService` | `domain.service` | Schedule CRUD + cron preview + runNow |
| `EnterpriseDataPushScheduler` | `infrastructure.scheduler` | Per-minute DB sweeper with atomic claim; auto-deactivation after 3 consecutive failures |
| `EmailDataPushAdapter` | `infrastructure.enterprise.delivery` | ≤8MB → email attachment, >8MB → signed download link |
| `HmacSignedLinkAdapter` | `infrastructure.enterprise` | HMAC-SHA256 token generation + verification, constant-time comparison |
| `SignedDownloadController` | `web.controller` | `GET /d/{token}` — token-verified artifact download, no login required |
| `DataPushScheduleRepository` | `infrastructure.persistence` | `findDue(now)` + `claim(scheduleId, observedNextRunAt, newNextRunAt, now)` |

**304 tests across 25 test classes (289 ED-1 + 15 ED-2).**
