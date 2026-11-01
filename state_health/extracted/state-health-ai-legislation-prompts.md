# State Health-AI Legislation Registry — Claude Code Prompts (Slices 2.4 and 2.5)

Two ready-to-paste prompts. Each is self-contained — it restates the architectural context (hexagonal architecture, Spring Boot, Spring AI, PostgreSQL/pgvector) so you can paste it into a fresh Claude Code session (Plan Mode recommended) without re-explaining the project.

**Suggested session order:** 2.4 → merge → 2.5 → merge. Neither touches the shared classifier DTO, so both can run in parallel with any other Phase 2/3 slice.

**Before running Prompt 2.4:**
1. Paste the "State Health-AI Legislation Registry" sub-section from `state-health-ai-legislation-slices.md` into `CLAUDE.md` under the existing "Market Analysis" section.
2. Copy `state_health_ai_laws_seed.json` to `src/main/resources/data/state_health_ai_laws_seed.json`.
3. Copy `state_health_ai_laws_raw.json` and `state_health_ai_laws_table.md` to `docs/legislation/`.

---

## Prompt 2.4 — State law registry: domain, schema, seed import, REST

```
Implement Slice 2.4 of the Market Analysis module: the State Health-AI
Legislation Registry — domain model, Postgres schema, idempotent seed
importer, and read-only REST endpoints — per the "State Health-AI
Legislation Registry" sub-section of the Market Analysis section in
CLAUDE.md. Read that sub-section fully before planning.

Context: this project uses hexagonal architecture. Domain types live under
domain/legislation/ and must have zero framework dependencies (no Spring,
no Jackson annotations, no JPA). Ports are plain interfaces in
domain/legislation/port/. Adapters live under infrastructure/legislation/,
application services under application/legislation/, controllers under
web/legislation/. Persistence is PostgreSQL via Flyway migrations under
db/migration/. Match the conventions already used by the Market Analysis
module (see MarketDigestRepositoryAdapter, MarketDigestController, and
their tests) for package layout, entity mapping, Testcontainers setup,
pagination, and 404 error shape.

Goal: give the app an authoritative, source-linked registry of every
enacted U.S. state law regulating AI in healthcare, seeded from
src/main/resources/data/state_health_ai_laws_seed.json (43 records:
41 enacted laws across 24 states plus 2 tracker-listed bills that did
NOT pass), queryable by state, category, status, effective-date window,
and free text.

The seed JSON is the contract. Field names and enum codes in the domain
model must match it exactly:
  id, stateCode, stateName, billNumber, title, yearEnacted, dateSigned,
  dateSignedNote, effectiveDate, effectiveDateNote, status, statusDetail,
  categories[], regulatedParties, keyRequirements, enforcement,
  sources[] {type, url}, notes, datasetVersion
Enums:
  LawStatus: ENACTED, ENACTED_STAYED, NOT_ENACTED, PENDING
  LawCategory: PAYER_UTILIZATION_REVIEW, CLAIMS_DOWNCODING,
    PROVIDER_CLINICAL_USE, PROVIDER_DISCLOSURE_CONSENT,
    MENTAL_HEALTH_PSYCHOTHERAPY, CONSUMER_CHATBOTS, COMPREHENSIVE_AI_ACT, OTHER
  SourceType: OFFICIAL, SECONDARY
  StateCode: all 50 states + DC as two-letter codes

Create these files:
- domain/legislation/StateLaw.java (aggregate root; record or class with a
  validating static factory — an ENACTED law must have >=1 OFFICIAL source
  OR a non-null notes value)
- domain/legislation/StateCode.java, LawStatus.java, LawCategory.java,
  SourceType.java, LawSource.java, LawChangeEvent.java, NewBillCandidate.java
- domain/legislation/port/StateLawRepository.java (upsert, findById,
  findAll, findByState, findByCategory, findEffectiveBetween, search,
  recordChangeEvent, findUnreviewedChangeEvents, saveCandidate,
  findUnreviewedCandidates)
- domain/legislation/port/LawSourceMonitorPort.java,
  LegislationDiscoveryPort.java, LawChangeNotifier.java — INTERFACES ONLY,
  each with the comment "// Implemented in Slice 2.5". Do not implement
  them in this session.
- db/migration/V<next>__create_state_law_registry.sql — use the exact
  schema from CLAUDE.md (tables: state_law, state_law_category,
  state_law_source, state_law_change_event, state_law_candidate with a
  VECTOR(1536) embedding column). Check the repo's current highest
  migration number and use the next one; <next> is a placeholder.
- infrastructure/legislation/persistence/ — JPA entities for the five
  tables, StateLawJpaRepository, StateLawRepositoryAdapter (implements
  the domain port), StateLawRepositoryAdapterIT (Testcontainers, real
  Postgres with pgvector, same setup as MarketDigestRepositoryAdapterIT).
- infrastructure/legislation/seed/StateLawSeedRecord.java — Jackson DTO
  mirroring the seed JSON. Configure FAIL_ON_UNKNOWN_PROPERTIES=true for
  this DTO so a future seed schema change fails loudly at startup.
- infrastructure/legislation/seed/StateLawSeedImporter.java —
  ApplicationRunner. Upsert by id, but ONLY overwrite a row whose
  dataset_version is lexicographically older than the seed record's
  datasetVersion (versions are ISO dates). Never touch rows with an
  equal or newer version. Log a one-line summary: inserted / updated /
  skipped counts.
- infrastructure/legislation/seed/StateLawSeedImporterTest.java
- application/legislation/StateLawQueryService.java (thin; filters +
  upcoming-effective-date view using an injectable java.time.Clock)
- web/legislation/StateLawController.java with:
    GET /api/legislation/state-laws
        (query params: state, category, status, effectiveFrom,
         effectiveTo, q; paginated like /api/market-digest)
    GET /api/legislation/state-laws/{id}
    GET /api/legislation/state-laws/upcoming?days=N
    GET /api/legislation/state-laws/by-state  (every StateCode as a key,
        including those with an empty list)
- web/legislation/StateLawSummary.java, StateLawResponse.java (full
  record incl. sources and change events)
- web/legislation/StateLawControllerIT.java

Implementation notes:
- search(String) is ILIKE '%q%' over title || key_requirements || notes.
  Do NOT build vector search in this slice.
- effectiveDate is the FIRST operative date; staged laws keep the staging
  in effectiveDateNote. Never derive or invent a date the seed leaves null.
- Keep the two NOT_ENACTED rows (Florida, Mississippi) — the UI filters
  them out, and Slice 2.5's dedupe relies on their presence.
- Do not add the monitor/discovery/notifier adapters, scheduler, or the
  /changes, /candidates, /refresh endpoints — those are Slice 2.5.

Acceptance criteria (write tests to verify each):
1. domain/legislation/** has zero imports from Spring, Jackson, JPA, or
   any infrastructure/web package — verify by inspecting imports.
2. Migration applies cleanly; UNIQUE (state_code, bill_number) is enforced.
3. Fresh DB + startup imports exactly 43 state_law rows: 40 ENACTED,
   1 ENACTED_STAYED (co-sb-24-205), 2 NOT_ENACTED (fl-hb-1555-cs-sb-794,
   ms-hb-1535). Every ENACTED/ENACTED_STAYED row has >=1 category and
   >=1 source.
4. Restart with the same seed changes zero rows (updated_at unchanged).
5. Set one row's dataset_version to '2099-01-01', restart: that row is
   untouched; the other 42 remain eligible.
6. findEffectiveBetween(2026-09-01, 2026-12-31) includes al-sb-63 and
   excludes ga-sb-444.
7. GET /api/legislation/state-laws?state=TX returns exactly tx-sb-815 and
   tx-hb-149; ?category=CLAIMS_DOWNCODING returns exactly il-sb-3114 and
   in-hb-1271-hea-1271; ?status=NOT_ENACTED returns exactly the Florida
   and Mississippi rows.
8. GET /api/legislation/state-laws/tx-sb-815 returns one OFFICIAL source
   whose URL starts with https://capitol.texas.gov/.
9. GET /api/legislation/state-laws/does-not-exist returns 404 in the same
   error shape as a missing market digest date.
10. With Clock fixed at 2026-09-06, GET /upcoming?days=120 includes
    al-sb-63 and excludes ga-sb-444.

When done, summarize which files were created, report the importer's
inserted/updated/skipped counts from a fresh run, and confirm all ten
acceptance criteria pass.
```

---

## Prompt 2.5 — Source freshness monitor, new-bill discovery, reviewer workflow

```
Implement Slice 2.5 of the Market Analysis module: automated freshness
for the State Health-AI Legislation Registry — a weekly source monitor,
a weekly Perplexity Search discovery sweep for new bills, an SES change
digest to reviewers, and promote/dismiss/review endpoints — per the
"State Health-AI Legislation Registry" sub-section of the Market Analysis
section in CLAUDE.md. Read that sub-section fully before planning.

Context: hexagonal architecture, Spring Boot, Spring AI (structured
output via ChatClient.entity()), PostgreSQL with pgvector, Flyway.
Slice 2.4 already created domain/legislation/** (StateLaw, LawSource,
LawChangeEvent, NewBillCandidate, enums), the five state_law* tables,
StateLawRepositoryAdapter, the seed importer, StateLawQueryService, and
the read-only endpoints on web/legislation/StateLawController. Slice 2.4
also DECLARED three ports you will now implement: LawSourceMonitorPort,
LegislationDiscoveryPort, LawChangeNotifier.

Reuse, do not duplicate:
- The Perplexity Search client wrapper from infrastructure/marketanalysis/
  perplexity/ (Slice 1.4) including its recency filter and retry policy.
- The embedding provider and threshold-as-config pattern from
  infrastructure/marketanalysis/embedding/ (Slice 1.9).
- SesEmailSender and the formatter snapshot-test pattern from
  infrastructure/marketanalysis/notification/ (Slice 1.8).
- The marketAnalysisExecutor bean (Slice 1.6) for async work.
- The app's existing HTTP client for the source monitor — no new client.
If Slice 2.3's RegulatoryTrackerRepository bean exists, a promoted
candidate should also upsert a RegulatoryTracker with
Jurisdiction.US_STATE; make that wiring @ConditionalOnBean so 2.5 does
not hard-require 2.3.

Create these files:
- infrastructure/legislation/monitor/HttpLawSourceMonitorAdapter.java
  (implements LawSourceMonitorPort; never throws — returns a result with
  httpStatus and nullable contentHash)
- infrastructure/legislation/monitor/ContentNormalizer.java (strip HTML
  tags, collapse whitespace, SHA-256 hex)
- infrastructure/legislation/monitor/HttpLawSourceMonitorAdapterTest.java
  (MockWebServer/WireMock)
- infrastructure/legislation/discovery/PerplexityLegislationDiscoveryAdapter.java
  (implements LegislationDiscoveryPort)
- infrastructure/legislation/discovery/LegislationDiscoveryPrompts.java
- infrastructure/legislation/discovery/NewBillCandidateResponse.java
  (structured-output DTO: stateCode, billNumber, title, summary,
  dateSigned?, effectiveDate?, sourceUrls[], confidence 0-100)
- infrastructure/legislation/discovery/CandidateEmbeddingService.java
- infrastructure/legislation/discovery/PerplexityLegislationDiscoveryAdapterTest.java
  (golden fixture contract test)
- infrastructure/legislation/notification/SesLawChangeNotifier.java
- infrastructure/legislation/notification/LawChangeDigestFormatter.java
- infrastructure/legislation/notification/LawChangeDigestFormatterTest.java (snapshot)
- application/legislation/LegislationRefreshService.java
- application/legislation/LegislationDiscoveryService.java
- application/legislation/LegislationReviewService.java
- application/legislation/LegislationScheduler.java
- application/legislation/LegislationRefreshServiceTest.java
- application/legislation/LegislationDiscoveryServiceTest.java
- web/legislation/StateLawController.java — ADD:
    GET  /api/legislation/changes?unreviewedOnly=true
    POST /api/legislation/changes/{id}/review
    GET  /api/legislation/candidates?unreviewedOnly=true
    POST /api/legislation/candidates/{id}/promote   (body: PromoteCandidateRequest)
    POST /api/legislation/candidates/{id}/dismiss
    POST /api/legislation/refresh                    (admin; 202 Accepted)
- web/legislation/PromoteCandidateRequest.java
- web/legislation/StateLawControllerIT.java (extend)
- config/LegislationConfig.java — @ConfigurationProperties("legislation"):
    refresh.cron          default "0 30 5 * * MON"  (America/Chicago)
    discovery.cron        default "0 0 6 * * MON"
    discovery.lookback-days      default 8
    discovery.dedupe-threshold   default 0.93
    monitor.politeness-delay-ms  default 2000
    monitor.blocked-hosts  default [leginfo.legislature.ca.gov, cga.ct.gov,
      iga.in.gov, legiscan.com, nysenate.gov, billstatus.ls.state.ms.us]
    review.recipients      (list of emails)

Behavior:
- Monitor: for every state_law_source row, fetch -> normalize -> hash.
  First fetch stores the baseline, emits nothing. Changed hash -> set
  changed_since_last_review=true and record SOURCE_CONTENT_CHANGED with
  URL + old/new hash prefixes. 4xx/5xx/timeout -> record
  SOURCE_UNREACHABLE once per URL until it is next reachable (no weekly
  repeats). Blocked hosts are never requested; emit SOURCE_UNREACHABLE
  once on first run only. Space requests by politeness-delay-ms.
- Discovery: Perplexity Search with recency = lookback window using the
  prompt text in CLAUDE.md (newly enacted/signed/amended state laws on AI
  in health care: payer UR/prior auth, downcoding, provider disclosure/
  consent, psychotherapy/mental health, chatbots/companions with health
  provisions, comprehensive AI acts). One Spring AI structured-output call
  maps hits -> List<NewBillCandidateResponse>. Instruct the model to
  return an empty list rather than guess. Then dedupe:
    (1) exact (stateCode, billNumber) match against state_law -> do NOT
        save a candidate; record STATUS_CHANGE_CANDIDATE on that law;
    (2) cosine similarity >= dedupe-threshold against existing unreviewed
        candidates -> skip.
- Notifier: one email per run, only if >=1 event or candidate. Sections:
  "Sources that changed", "Sources unreachable", "New bill candidates".
  Every line includes its URL.
- Review: promote requires >=1 OFFICIAL source URL and all non-null
  StateLaw fields (400 otherwise); stamps datasetVersion with today's ISO
  date; sets candidate.reviewed=true and promotedLawId. dismiss sets
  reviewed=true. changes/{id}/review sets reviewed=true and clears
  changed_since_last_review on the linked source.
- POST /refresh runs monitor then discovery on marketAnalysisExecutor and
  returns 202 immediately. Protect it with the same admin mechanism used
  by the app's other admin endpoints.
- Scheduler: two @Scheduled methods reading cron strings from config,
  zone America/Chicago.

Acceptance criteria (write tests to verify each):
1. Monitor: first fetch -> hash stored, no event; identical body -> no
   event; whitespace-only change -> no event; substantive text change ->
   exactly one SOURCE_CONTENT_CHANGED and changed_since_last_review=true.
2. Monitor: 403 -> one SOURCE_UNREACHABLE; second consecutive 403 ->
   none; later 200 clears state so a subsequent 403 fires again.
3. A blocked-host URL is never requested (zero calls on the mock server).
4. Discovery contract test: newly-signed-payer-bill fixture -> one
   candidate with correct stateCode, billNumber, >=1 sourceUrl,
   confidence >= 70; empty-search fixture -> empty list, no exception;
   malformed LLM output -> typed adapter exception.
5. Dedupe: candidate matching (TX, "SB 815") is not saved; a
   STATUS_CHANGE_CANDIDATE event is recorded on tx-sb-815.
6. Dedupe: two near-identical candidates in one run persist as one.
7. Notifier: zero events + zero candidates -> no email (mock verify);
   with events -> snapshot HTML contains every event URL.
8. promote without an OFFICIAL source -> 400; valid body -> new state_law
   row readable via GET /api/legislation/state-laws/{id}, candidate
   marked reviewed, and (if the 2.3 bean is present) a RegulatoryTracker
   with Jurisdiction.US_STATE exists.
9. POST /refresh -> 202; the work runs on a thread named market-digest-*.
10. Both cron expressions come from config and are inspectable the same
    way Slice 1.6's scheduler test inspects the daily digest cron.

When done, summarize which files were created/modified, run the monitor
once against the seeded sources in a local/dev profile and report how
many sources returned 200 vs. were unreachable/blocked, and confirm all
ten acceptance criteria pass.
```
