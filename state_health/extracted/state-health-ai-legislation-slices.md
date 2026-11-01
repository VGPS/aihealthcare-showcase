# Market Analysis — State Health-AI Legislation Registry (Slices 2.4 and 2.5)

Companion to the `Market Analysis` CLAUDE.md section and to `market-analysis-slices.md`. This document adds two new Phase 2 slices that give the AIHealthcare application a durable, source-linked registry of every enacted U.S. state law regulating AI in healthcare, plus an automated freshness loop that re-checks the official sources and discovers new bills without a human re-doing the research.

Same conventions as every other slice: hexagonal architecture, Spring Boot / Spring AI, PostgreSQL with pgvector, Flyway migrations, `domain/` has zero framework dependencies. Each slice lists **Goal**, **Depends on**, **Files touched**, **Implementation notes**, and **Acceptance criteria**. Ready-to-paste Claude Code prompts for both slices are in `state-health-ai-legislation-prompts.md`.

**Files that ship with this document** (place them exactly where noted before running Prompt 2.4):

| File | Where it goes in the repo | Purpose |
|---|---|---|
| `state_health_ai_laws_seed.json` | `src/main/resources/data/state_health_ai_laws_seed.json` | Normalized seed dataset — 43 records (41 enacted laws in 24 states + 2 tracker-listed bills that did NOT pass). ISO dates, enum codes, typed source URLs. This is what the seed importer reads. |
| `state_health_ai_laws_raw.json` | `docs/legislation/state_health_ai_laws_raw.json` | The research-grade original (prose dates, "n.a." markers, evidentiary notes). Reference only — never loaded by the app. |
| `state_health_ai_laws_table.md` | `docs/legislation/state_health_ai_laws_table.md` | Human-readable table, pending-bills list, and tracker corrections. Reference only. |

---

## Why this is its own slice (and not part of 2.3)

Slice 2.3's `RegulatoryTracker` tracks a single regulatory *item* moving through a *federal-style rulemaking lifecycle* (discussion paper → comment period → guidance → enforcement) and is fed by the daily news pipeline. State legislation behaves differently: dozens of statutes, each with a bill number, signing date, staggered effective dates, a category mix, and a stable official URL that can be re-fetched. It is a **reference registry** with a slow refresh cadence, not a news-driven tracker. Keeping it in its own bounded context (`domain/legislation/`) avoids overloading `RegulatoryTracker`, while Slice 2.5 links the two where it adds value (a newly discovered bill can seed a `RegulatoryTracker` row with `Jurisdiction.US_STATE`).

---

## CLAUDE.md addition — paste under the "Market Analysis" section as a new sub-section

### State Health-AI Legislation Registry

**Purpose.** Maintain an authoritative, source-linked registry of U.S. state laws regulating AI in healthcare (payer utilization review, claims downcoding, provider clinical use, provider disclosure/consent, mental-health/psychotherapy, consumer chatbots, comprehensive AI acts). Seeded from a researched dataset (version `2026-09-06`, 41 enacted laws / 24 states), then kept current by (a) re-fetching each law's official source URL on a schedule and flagging content changes, and (b) a weekly Perplexity Search sweep for newly enacted or amended bills. Every value in the registry carries the URL it came from; nothing is written from LLM memory.

**Domain model (`domain/legislation/`, zero framework deps):**

```
domain/
  legislation/
    StateLaw.java                 // aggregate root — see fields below
    StateCode.java                // enum of the 50 US states + DC (2-letter codes)
    LawStatus.java                // enum: ENACTED, ENACTED_STAYED, NOT_ENACTED, PENDING
    LawCategory.java              // enum: PAYER_UTILIZATION_REVIEW, CLAIMS_DOWNCODING, PROVIDER_CLINICAL_USE,
                                  //       PROVIDER_DISCLOSURE_CONSENT, MENTAL_HEALTH_PSYCHOTHERAPY,
                                  //       CONSUMER_CHATBOTS, COMPREHENSIVE_AI_ACT, OTHER
    LawSource.java                // record: sourceType (OFFICIAL/SECONDARY), url, lastFetchedAt, lastContentHash, lastHttpStatus, changedSinceLastReview
    SourceType.java               // enum: OFFICIAL, SECONDARY
    LawChangeEvent.java           // record: lawId, detectedAt, changeType (SOURCE_CONTENT_CHANGED, SOURCE_UNREACHABLE, NEW_BILL_CANDIDATE, STATUS_CHANGE_CANDIDATE), detail, reviewed
    NewBillCandidate.java         // record: stateCode, billNumber, title, summary, sourceUrls, discoveredAt, confidence (0-100), reviewed, promotedLawId
    port/
      StateLawRepository.java
      LawSourceMonitorPort.java   // fetch URL -> (httpStatus, contentHash, fetchedAt); implemented over the app's existing HTTP client
      LegislationDiscoveryPort.java // findNewStateHealthAiBills(Instant since) -> List<NewBillCandidate>; Perplexity Search adapter
      LawChangeNotifier.java      // notify(List<LawChangeEvent>) -> SES email to the reviewer/admin list
```

`StateLaw` fields (mirror the seed JSON exactly — same names, same enum codes):

```
id                 String   // stable slug, e.g. "tx-sb-815" — primary key, also the seed file's "id"
stateCode          StateCode
stateName          String
billNumber         String   // as filed, e.g. "SB 815", "S 2195 / H 7350", "HB 1271 (HEA 1271)"
title              String   // official title or act name
yearEnacted        Integer  // nullable for NOT_ENACTED
dateSigned         LocalDate // nullable
dateSignedNote     String   // nullable — retained prose when the exact date is unconfirmed or came from a secondary source
effectiveDate      LocalDate // nullable — the FIRST operative date; staged dates are described in effectiveDateNote
effectiveDateNote  String   // nullable
status             LawStatus
statusDetail       String   // free text, e.g. "Enacted — enforcement stayed by federal court; repealed/replaced by SB 26-189"
categories         List<LawCategory>  // 1..n
regulatedParties   String
keyRequirements    String   // 2-4 sentences, specific (human review, disclosure, crisis detection, reporting, consent)
enforcement        String   // nullable — agency, penalties, private right of action
sources            List<LawSource>    // at least one OFFICIAL where available
notes              String   // nullable — litigation, preemption, amendments, evidentiary caveats
datasetVersion     String   // "2026-09-06" for seed rows; refresh job stamps its own run date on updates
createdAt / updatedAt  Instant
```

**Ports:**

```java
public interface StateLawRepository {
    void upsert(StateLaw law);                       // keyed on id
    Optional<StateLaw> findById(String id);
    List<StateLaw> findAll();
    List<StateLaw> findByState(StateCode state);
    List<StateLaw> findByCategory(LawCategory category);
    List<StateLaw> findEffectiveBetween(LocalDate from, LocalDate to);   // upcoming-effective-date view
    List<StateLaw> search(String freeText);          // ILIKE over title/keyRequirements/notes; upgrade to pgvector later if useful
    void recordChangeEvent(LawChangeEvent event);
    List<LawChangeEvent> findUnreviewedChangeEvents();
    void saveCandidate(NewBillCandidate candidate);
    List<NewBillCandidate> findUnreviewedCandidates();
}

public interface LawSourceMonitorPort {
    // Fetch the URL, return status + SHA-256 of the normalized text body. Never throws — unreachable sources
    // come back as a result with httpStatus = 0 or the error code and a null hash.
    SourceFetchResult fetch(String url);
}

public interface LegislationDiscoveryPort {
    // Perplexity Search (recency-filtered) for newly enacted/amended state laws on AI in healthcare since `since`.
    // Returns candidates only — a human (or a later LLM-review step) promotes a candidate to a StateLaw.
    List<NewBillCandidate> findNewStateHealthAiBills(Instant since);
}

public interface LawChangeNotifier {
    void notify(List<LawChangeEvent> events, List<NewBillCandidate> candidates);
}
```

**Persistence (Flyway, `V<next>__create_state_law_registry.sql`):**

```sql
CREATE TABLE state_law (
    id                  VARCHAR(96) PRIMARY KEY,
    state_code          CHAR(2) NOT NULL,
    state_name          VARCHAR(32) NOT NULL,
    bill_number         VARCHAR(128) NOT NULL,
    title               TEXT NOT NULL,
    year_enacted        SMALLINT,
    date_signed         DATE,
    date_signed_note    TEXT,
    effective_date      DATE,
    effective_date_note TEXT,
    status              VARCHAR(24) NOT NULL,     -- ENACTED, ENACTED_STAYED, NOT_ENACTED, PENDING
    status_detail       TEXT,
    regulated_parties   TEXT,
    key_requirements    TEXT NOT NULL,
    enforcement         TEXT,
    notes               TEXT,
    dataset_version     VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (state_code, bill_number)
);
CREATE INDEX idx_state_law_state ON state_law(state_code);
CREATE INDEX idx_state_law_effective ON state_law(effective_date);

CREATE TABLE state_law_category (
    law_id      VARCHAR(96) NOT NULL REFERENCES state_law(id) ON DELETE CASCADE,
    category    VARCHAR(40) NOT NULL,
    PRIMARY KEY (law_id, category)
);

CREATE TABLE state_law_source (
    id                       BIGSERIAL PRIMARY KEY,
    law_id                   VARCHAR(96) NOT NULL REFERENCES state_law(id) ON DELETE CASCADE,
    source_type              VARCHAR(16) NOT NULL,   -- OFFICIAL, SECONDARY
    url                      TEXT NOT NULL,
    last_fetched_at          TIMESTAMPTZ,
    last_http_status         SMALLINT,
    last_content_hash        CHAR(64),
    changed_since_last_review BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (law_id, url)
);

CREATE TABLE state_law_change_event (
    id           BIGSERIAL PRIMARY KEY,
    law_id       VARCHAR(96) REFERENCES state_law(id) ON DELETE CASCADE,   -- nullable for NEW_BILL_CANDIDATE
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    change_type  VARCHAR(40) NOT NULL,   -- SOURCE_CONTENT_CHANGED, SOURCE_UNREACHABLE, NEW_BILL_CANDIDATE, STATUS_CHANGE_CANDIDATE
    detail       TEXT NOT NULL,
    reviewed     BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE state_law_candidate (
    id               BIGSERIAL PRIMARY KEY,
    state_code       CHAR(2) NOT NULL,
    bill_number      VARCHAR(128),
    title            TEXT,
    summary          TEXT NOT NULL,
    source_urls      TEXT[] NOT NULL,
    discovered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    confidence       SMALLINT CHECK (confidence BETWEEN 0 AND 100),
    reviewed         BOOLEAN NOT NULL DEFAULT false,
    promoted_law_id  VARCHAR(96) REFERENCES state_law(id),
    embedding        VECTOR(1536)      -- pgvector: dedupe candidates against each other and against existing laws
);
```

**REST API (read-only registry + reviewer actions):**

```
GET  /api/legislation/state-laws                          -> List<StateLawSummary>  (filters: state=, category=, status=, effectiveFrom=, effectiveTo=, q=)
GET  /api/legislation/state-laws/{id}                     -> StateLawResponse (full record incl. sources + change events)
GET  /api/legislation/state-laws/upcoming?days=90         -> laws whose effective_date falls within the next N days
GET  /api/legislation/state-laws/by-state                 -> Map<StateCode, List<StateLawSummary>>  (for a US-map UI)
GET  /api/legislation/changes?unreviewedOnly=true         -> List<LawChangeEvent>
POST /api/legislation/changes/{id}/review                 -> mark reviewed
GET  /api/legislation/candidates?unreviewedOnly=true      -> List<NewBillCandidate>
POST /api/legislation/candidates/{id}/promote             -> body: full StateLaw; creates the law, links candidate.promotedLawId
POST /api/legislation/candidates/{id}/dismiss             -> mark reviewed without promotion
POST /api/legislation/refresh                             -> admin: trigger the source-monitor run now (async, returns 202)
```

**Scheduled jobs (same `@Scheduled` + `marketAnalysisExecutor` pattern):**

```java
// Weekly source freshness check — Monday 05:30 America/Chicago, before the 07:00 daily digest
@Scheduled(cron = "0 30 5 * * MON", zone = "America/Chicago")
public void refreshSources() { legislationService.refreshAllSources(); }

// Weekly discovery sweep — Monday 06:00 America/Chicago
@Scheduled(cron = "0 0 6 * * MON", zone = "America/Chicago")
public void discoverNewBills() { legislationService.discoverNewBills(Instant.now().minus(8, ChronoUnit.DAYS)); }
```

Both cadences are config properties (`legislation.refresh.cron`, `legislation.discovery.cron`, `legislation.discovery.lookback-days`) — weekly is the default because state legislatures move slowly out of session; drop to daily Jan–Jun when most sessions are live.

**Provider decisions (final — do not re-litigate):**
- **Source monitoring:** the app's existing HTTP client (whatever `PerplexityMarketNewsAdapter` / the Trends pipeline already wrap). No new vendor. Hash the *text* body after stripping HTML tags and collapsing whitespace so cosmetic page changes don't fire false alarms. Respect `robots.txt`-style politeness: one request per URL per run, 2-second spacing, honest `User-Agent`.
- **Discovery:** the **Perplexity Search API**, reusing the Slice 1.4 client and its recency filter. Not LegiScan, not a paid bill-tracking API (LegiScan's free tier is rate-capped and its bulk API is a paid tier; the Search API is already in the stack and the volume — a handful of new enactments per month — is trivial).
- **Known blocked hosts (as of 2026-09-06):** `leginfo.legislature.ca.gov` for 2025–26 bills, `cga.ct.gov`, `iga.in.gov`, `legiscan.com`, `nysenate.gov`, `billstatus.ls.state.ms.us` refused automated fetches during research. The monitor must treat 403/429/timeouts as `SOURCE_UNREACHABLE` (flag once, do not retry in a loop) and never as "content changed". For those hosts, the seed already points to an accessible official alternate (governor press release, chaptered-law PDF, agency advisory) or marks the SECONDARY source.
- **Quarterly anchor:** the Manatt Health AI Policy Tracker (`https://www.manatt.com/insights/newsletters/health-highlights/manatt-health-health-ai-policy-tracker`) publishes quarterly. Register it as a SECONDARY source on a synthetic `_tracker-manatt` row (status `OTHER`) so its content hash is monitored too — a hash change is the cue to run a manual reconciliation pass against the registry.

**Data-quality rules baked into the domain:**
- A `StateLaw` with `status = ENACTED` must have at least one `OFFICIAL` source **or** a `notes` value explaining why only a secondary source exists. Enforce in a static factory / validator, not in the DB.
- `effectiveDate` is the first operative date; multi-stage laws (Connecticut SB 5, Nebraska LB 525, Hawaii SB 3001) keep `effectiveDate` for the first stage or `null` with the staging described in `effectiveDateNote`. Never invent a single date for a staged law.
- Laws that trackers list but that did NOT pass (Florida HB 1555 / SB 794; Mississippi HB 1535) are kept with `status = NOT_ENACTED` so the UI and the discovery de-duplicator can suppress them and so a future tracker "re-discovery" doesn't re-add them as new.
- `NEW_BILL_CANDIDATE` records are never auto-promoted. Promotion requires the reviewer to supply an official URL.

---

## Slice 2.4 — State law registry: domain, schema, seed import, REST — parallelizable

**Goal:** Stand up the `domain/legislation/` bounded context, its Postgres schema, a seed importer that loads `state_health_ai_laws_seed.json` idempotently at startup, and the read-only REST endpoints — so the React frontend can render a state-by-state view of enacted health-AI laws from day one.

**Depends on:** Slice 1.3 (repository adapter / Testcontainers conventions), Slice 1.8 (controller and response-DTO conventions). No dependency on 2.1–2.3; no shared DTO with any other slice.

**Files touched:**
```
domain/legislation/StateLaw.java
domain/legislation/StateCode.java
domain/legislation/LawStatus.java
domain/legislation/LawCategory.java
domain/legislation/LawSource.java
domain/legislation/SourceType.java
domain/legislation/LawChangeEvent.java
domain/legislation/NewBillCandidate.java
domain/legislation/port/StateLawRepository.java
domain/legislation/port/LawSourceMonitorPort.java        (interface only — implemented in 2.5)
domain/legislation/port/LegislationDiscoveryPort.java    (interface only — implemented in 2.5)
domain/legislation/port/LawChangeNotifier.java           (interface only — implemented in 2.5)
db/migration/V<next>__create_state_law_registry.sql
infrastructure/legislation/persistence/StateLawEntity.java
infrastructure/legislation/persistence/StateLawCategoryEntity.java
infrastructure/legislation/persistence/StateLawSourceEntity.java
infrastructure/legislation/persistence/StateLawChangeEventEntity.java
infrastructure/legislation/persistence/StateLawCandidateEntity.java
infrastructure/legislation/persistence/StateLawJpaRepository.java
infrastructure/legislation/persistence/StateLawRepositoryAdapter.java
infrastructure/legislation/persistence/StateLawRepositoryAdapterIT.java
infrastructure/legislation/seed/StateLawSeedRecord.java           (Jackson DTO matching the seed JSON exactly)
infrastructure/legislation/seed/StateLawSeedImporter.java         (ApplicationRunner; idempotent upsert keyed on id)
infrastructure/legislation/seed/StateLawSeedImporterTest.java
application/legislation/StateLawQueryService.java
web/legislation/StateLawController.java
web/legislation/StateLawSummary.java
web/legislation/StateLawResponse.java
web/legislation/StateLawControllerIT.java
src/main/resources/data/state_health_ai_laws_seed.json            (copy the shipped file here, unchanged)
docs/legislation/state_health_ai_laws_raw.json                    (reference copy)
docs/legislation/state_health_ai_laws_table.md                    (reference copy)
```

**Implementation notes:**
- The seed JSON is the contract. `StateLawSeedRecord` must deserialize every record in the shipped file with no field ignored and no field defaulted silently — configure Jackson `FAIL_ON_UNKNOWN_PROPERTIES = true` for this DTO so schema drift in a future seed version fails loudly at startup rather than dropping data.
- Importer semantics: **upsert by `id`, but only overwrite a row whose `dataset_version` is older than the seed's `datasetVersion`.** Rows the refresh job (2.5) has stamped with a newer version must not be clobbered on restart. Rows a reviewer has edited via the promote/review endpoints carry the run-date version and are therefore preserved too.
- `StateCode` is a full 51-value enum even though only 24 states have laws today — the discovery sweep in 2.5 will surface other states.
- `LawStatus.PENDING` exists in the enum from day one (for promoted candidates that are passed-but-unsigned, e.g. New York's Kids Chatbot Safety Act) but no seed row uses it.
- `search(String)` can start as `ILIKE '%q%'` over `title || key_requirements || notes`; do not build pgvector search in this slice — the `embedding` column exists only on `state_law_candidate` for 2.5's dedupe.
- `GET /by-state` should return every state code (including those with an empty list) so a US-map component can render uniformly.
- Follow the existing controller/DTO/error-handling conventions from `MarketDigestController` — same base path style, same pagination, same 404 shape.
- Do not implement the three monitor/discovery/notifier ports here — just declare them. Leave a one-line comment on each: `// Implemented in Slice 2.5`.

**Acceptance criteria:**
- [ ] `domain/legislation/**` compiles with zero imports from Spring, Jackson, JPA, or any `infrastructure`/`web` package (verify by import inspection, not just compilation).
- [ ] Migration applies cleanly on top of the current schema; `UNIQUE (state_code, bill_number)` is enforced.
- [ ] On a fresh database, application startup imports **exactly 43** `state_law` rows: 40 `ENACTED`, 1 `ENACTED_STAYED` (`co-sb-24-205`), 2 `NOT_ENACTED` (`fl-hb-1555-cs-sb-794`, `ms-hb-1535`); every ENACTED/ENACTED_STAYED row has ≥1 category and ≥1 source.
- [ ] Restarting the application with the same seed file changes **zero** rows (`updated_at` unchanged) — importer is idempotent.
- [ ] Manually bumping one row's `dataset_version` to `2099-01-01` and restarting leaves that row untouched while the other 42 are still eligible for overwrite.
- [ ] `findEffectiveBetween(2026-09-01, 2026-12-31)` returns at least `al-sb-63` (2026-10-01) and does not return `ga-sb-444` (2027-01-01).
- [ ] `GET /api/legislation/state-laws?state=TX` returns exactly `tx-sb-815` and `tx-hb-149`; `?category=CLAIMS_DOWNCODING` returns exactly `il-sb-3114` and `in-hb-1271-hea-1271`; `?status=NOT_ENACTED` returns exactly the two Florida/Mississippi rows.
- [ ] `GET /api/legislation/state-laws/tx-sb-815` returns the full record including one `OFFICIAL` source whose URL starts with `https://capitol.texas.gov/`.
- [ ] `GET /api/legislation/state-laws/does-not-exist` returns 404 in the same error shape as `GET /api/market-digest/{date}` for a missing date.
- [ ] `GET /api/legislation/state-laws/upcoming?days=120` (evaluated as of 2026-09-06 in the test via an injectable `Clock`) includes `al-sb-63` and excludes `ga-sb-444`.

---

## Slice 2.5 — Source freshness monitor, new-bill discovery, reviewer workflow — depends on 2.4

**Goal:** Keep the registry current automatically: re-fetch every source URL weekly and flag content changes or dead links; run a weekly Perplexity Search sweep for newly enacted or amended state health-AI laws and queue them as candidates; email the reviewer a digest of what changed; expose promote/dismiss/review endpoints so a human closes the loop.

**Depends on:** Slice 2.4 (registry), Slice 1.4 (Perplexity Search client to reuse), Slice 1.8 (SES notifier pattern). Soft dependency on Slice 2.3: if `RegulatoryTrackerRepository` exists, a promoted candidate also upserts a `RegulatoryTracker` with `Jurisdiction.US_STATE` — guard with a conditional bean, do not hard-require 2.3.

**Files touched:**
```
infrastructure/legislation/monitor/HttpLawSourceMonitorAdapter.java        (implements LawSourceMonitorPort)
infrastructure/legislation/monitor/ContentNormalizer.java                  (strip tags, collapse whitespace, SHA-256)
infrastructure/legislation/monitor/HttpLawSourceMonitorAdapterTest.java    (WireMock or MockWebServer)
infrastructure/legislation/discovery/PerplexityLegislationDiscoveryAdapter.java   (implements LegislationDiscoveryPort)
infrastructure/legislation/discovery/LegislationDiscoveryPrompts.java
infrastructure/legislation/discovery/NewBillCandidateResponse.java         (Spring AI structured-output DTO)
infrastructure/legislation/discovery/CandidateEmbeddingService.java        (reuse the Slice 1.9 embedding provider)
infrastructure/legislation/discovery/PerplexityLegislationDiscoveryAdapterTest.java
infrastructure/legislation/notification/SesLawChangeNotifier.java          (implements LawChangeNotifier)
infrastructure/legislation/notification/LawChangeDigestFormatter.java
infrastructure/legislation/notification/LawChangeDigestFormatterTest.java  (snapshot)
application/legislation/LegislationRefreshService.java
application/legislation/LegislationDiscoveryService.java
application/legislation/LegislationReviewService.java
application/legislation/LegislationScheduler.java
application/legislation/LegislationRefreshServiceTest.java
application/legislation/LegislationDiscoveryServiceTest.java
web/legislation/StateLawController.java            (add /changes, /candidates, /refresh endpoints)
web/legislation/PromoteCandidateRequest.java
web/legislation/StateLawControllerIT.java          (extend)
config/LegislationConfig.java                      (properties: cron strings, lookback days, reviewer email list, politeness delay, blocked-host list)
```

**Implementation notes:**
- **Monitor:** iterate every `state_law_source` row; fetch; normalize; compare hash. First-ever fetch stores the baseline and emits no event. A changed hash sets `changed_since_last_review = true` and records `SOURCE_CONTENT_CHANGED` with the old/new hash prefixes and URL in `detail`. HTTP 4xx/5xx/timeout records `SOURCE_UNREACHABLE` **once** per URL until it is next reachable (don't spam weekly). Hosts in `legislation.monitor.blocked-hosts` (seed the list with the six known-blocked hosts above) are skipped with a `SOURCE_UNREACHABLE` event only on the first run.
- **Discovery prompt** (Search API, recency = lookback window): "Newly enacted, signed, or amended U.S. state laws or regulations governing artificial intelligence in health care since {since}: insurer/payer use of AI in utilization review or prior authorization, AI downcoding of claims, provider disclosure or patient consent for AI, AI in psychotherapy or mental health, AI chatbots or companions with health/mental-health provisions, or comprehensive state AI acts with health-care provisions. For each, give state, bill number, title, date signed, effective date, and the official legislature or governor URL." Then a single Spring AI structured-output call maps the hits into `List<NewBillCandidateResponse>` with a 0–100 `confidence`. Instruct the model to return an empty list rather than guess when the search yields nothing concrete.
- **Candidate dedupe:** before saving, (1) exact-match on `(stateCode, billNumber)` against `state_law` — if it matches an existing law, record a `STATUS_CHANGE_CANDIDATE` event on that law instead of a new candidate (this is how "Governor signed the bill we had as PENDING" gets caught); (2) cosine similarity ≥ `legislation.discovery.dedupe-threshold` (default 0.93) against existing unreviewed candidates → skip. Reuse the Slice 1.9 embedding provider and threshold-as-config convention.
- **Notifier:** one email per weekly run, only if there is ≥1 new event or candidate. Sections: "Sources that changed", "Sources unreachable", "New bill candidates (N)". Each line carries the URL. Reuse `SesEmailSender`. Reviewer recipients come from `legislation.review.recipients`.
- **Review endpoints:** `promote` requires the request body to include at least one `OFFICIAL` source URL and all non-nullable `StateLaw` fields; stamp `datasetVersion` with today's ISO date; set `candidate.reviewed = true` and `promotedLawId`. `dismiss` sets `reviewed = true` only. `changes/{id}/review` sets `reviewed = true` and clears `changed_since_last_review` on the related source.
- **Manual trigger:** `POST /api/legislation/refresh` runs the monitor then the discovery sweep on `marketAnalysisExecutor` and returns 202 immediately. Protect with whatever admin-role mechanism the app already uses for `POST /api/market-digest/subscribe`-class endpoints.
- **Scheduler:** two `@Scheduled` methods with cron strings from config; distinct from and earlier than the 07:00 daily digest so the Monday digest can (later) mention legislation changes if you choose to wire that in.

**Acceptance criteria:**
- [ ] Monitor unit test (mock server): first fetch of a URL stores a hash and emits **no** event; second fetch with byte-identical body emits no event; second fetch with a whitespace-only change emits no event; second fetch with a substantive text change emits exactly one `SOURCE_CONTENT_CHANGED` and sets `changed_since_last_review = true`.
- [ ] Monitor unit test: a 403 response emits one `SOURCE_UNREACHABLE`; a second consecutive 403 emits none; a subsequent 200 clears the unreachable state (a later 403 fires again).
- [ ] A URL whose host is in `legislation.monitor.blocked-hosts` is never requested (verify zero calls on the mock server).
- [ ] Discovery adapter contract test (golden fixture): a fixture describing a newly signed payer-AI bill yields one `NewBillCandidate` with correct `stateCode`, `billNumber`, ≥1 `sourceUrl`, and `confidence ≥ 70`; an empty search fixture yields an empty list and no exception; malformed LLM output raises a typed adapter exception.
- [ ] Dedupe test: a candidate whose `(stateCode, billNumber)` matches `tx-sb-815` is not saved as a candidate and instead produces a `STATUS_CHANGE_CANDIDATE` event on `tx-sb-815`.
- [ ] Dedupe test: two near-identical candidates (same bill, reworded summary) in one run persist as one.
- [ ] Notifier: no email is sent when a run produces zero events and zero candidates (verify via mock); when there are events, the snapshot-tested HTML contains each event's URL.
- [ ] `POST /api/legislation/candidates/{id}/promote` without an `OFFICIAL` source returns 400; with a valid body it creates a `state_law` row readable via `GET /api/legislation/state-laws/{newId}`, marks the candidate reviewed, and (if the Slice 2.3 bean is present) creates a `RegulatoryTracker` with `Jurisdiction.US_STATE`.
- [ ] `POST /api/legislation/refresh` returns 202 and the work runs on a thread whose name starts with `market-digest-` (same executor as the daily pipeline).
- [ ] Both cron expressions are read from config and are inspectable via the same mechanism used in Slice 1.6's scheduler test.

---

## Cross-slice notes

- **No shared DTO with 2.1 / 2.3 / 3.3.** Slices 2.4 and 2.5 touch only `domain/legislation/**`, `infrastructure/legislation/**`, `application/legislation/**`, `web/legislation/**`, `config/LegislationConfig.java`, and one new migration. They can be run in parallel with anything in Phase 2/3 — including the sequential 2.1 → 2.3 → 3.3 chain — without merge-conflict risk.
- **Migration numbering:** same rule as every other slice — check the real latest `V*` number at implementation time; `<next>` is a placeholder.
- **Reused infrastructure, do not duplicate:** the Perplexity client wrapper from Slice 1.4, the embedding provider and threshold-as-config pattern from Slice 1.9, `SesEmailSender` and the formatter snapshot-test pattern from Slice 1.8, `marketAnalysisExecutor` from Slice 1.6, and the controller/404/pagination conventions from `MarketDigestController`.
- **Refreshing the seed itself:** when a new researched dataset is produced (e.g. after the Manatt Q3 2026 tracker lands), replace `state_health_ai_laws_seed.json`, bump every record's `datasetVersion`, and restart — the importer's version-gated upsert applies the new research while preserving reviewer edits made since.
- **What NOT to do:** do not let the discovery sweep write directly to `state_law`; do not treat a `SOURCE_CONTENT_CHANGED` event as evidence the law changed (legislature sites re-template constantly — it is a cue to look, nothing more); do not backfill `dateSigned` or `effectiveDate` from memory when the seed says `null` — leave it null with the note.

---

## Parent.md addendum — ALREADY APPLIED to the updated `Parent.md` shipped alongside this document (kept here for reference)

Add to **Step 2 — Phase 2 and Phase 3 slices with NO shared-DTO conflict**, after the Prompt 2.2 line:

- Open `state-health-ai-legislation-prompts.md` → run **Prompt 2.4** (state law registry, seed import, REST) — depends on Slices 1.3 and 1.8. **Before running:** copy `state_health_ai_laws_seed.json` to `src/main/resources/data/` and the two reference files to `docs/legislation/`. Merge.
- Open `state-health-ai-legislation-prompts.md` → run **Prompt 2.5** (source monitor, discovery sweep, reviewer workflow) — depends on Slices 2.4, 1.4, and 1.8; soft-links to 2.3 if present. Merge.

Add to the **Quick-reference dependency chain**:

```
1.3 AND 1.8 (both) unlock: 2.4
2.4 AND 1.4 (both) unlock: 2.5   (2.5 also soft-links to 2.3 if it has landed; not required)
```

Add to the **Documents in this directory** table:

| `state-health-ai-legislation-slices.md` | Slices 2.4–2.5: State Health-AI Legislation Registry — CLAUDE.md sub-section, domain model, schema, REST, jobs, two slices. |
| `state-health-ai-legislation-prompts.md` | 2 ready-to-paste Claude Code prompts (2.4, 2.5). |
| `state_health_ai_laws_seed.json` | Normalized seed dataset (43 records, version 2026-09-06). Goes to `src/main/resources/data/`. |
| `state_health_ai_laws_raw.json`, `state_health_ai_laws_table.md` | Research-grade originals with evidentiary notes and tracker corrections. Go to `docs/legislation/`. |

---

## Appendix — Seed data dictionary (`state_health_ai_laws_seed.json`)

| Field | Type | Notes |
|---|---|---|
| `id` | string | Stable slug `<state>-<bill>`; primary key. |
| `stateCode` / `stateName` | string | Two-letter code and full name. |
| `billNumber` | string | As filed. Companion bills joined with ` / `; chapter/act designations in parentheses. |
| `title` | string | Official title or act name; act/chapter number appended in parentheses where confirmed. |
| `yearEnacted` | int or null | Null for NOT_ENACTED. |
| `dateSigned` | ISO date or null | Null where no fetched official document stated it (AZ HB 2175, NY GBL Art. 47, RI S 2197). |
| `dateSignedNote` | string or null | Retained prose whenever the date came from a secondary source or is unconfirmed. |
| `effectiveDate` | ISO date or null | First operative date. Null for staged laws where no single date applies (CT SB 5) and for NOT_ENACTED. |
| `effectiveDateNote` | string or null | Staging detail, applicability dates, "per Manatt" provenance. |
| `status` | enum | `ENACTED`, `ENACTED_STAYED`, `NOT_ENACTED`. (`PENDING` reserved for promoted candidates.) |
| `statusDetail` | string | Free text from research. |
| `categories` | enum[] | See `LawCategory`. |
| `regulatedParties`, `keyRequirements`, `enforcement`, `notes` | string / null | Verbatim from research; `enforcement` null when the source did not state it. |
| `sources[]` | `{type, url}` | `OFFICIAL` = legislature/governor/agency document; `SECONDARY` = Manatt tracker, law-firm alert, or coalition update that stated a value the official page lacked. |
| `datasetVersion` | string | `2026-09-06` for all seed rows. |

Known evidentiary gaps in version 2026-09-06 (kept as null, do not fill from memory): governor signature dates for AZ HB 2175 and RI S 2197; Rhode Island public-law numbers for S 2197 and S 2570; Minnesota session-law chapter number for HF 4188. Six state websites blocked automated fetches during research (see "Known blocked hosts" above); their rows carry an accessible official alternate or a SECONDARY source.
