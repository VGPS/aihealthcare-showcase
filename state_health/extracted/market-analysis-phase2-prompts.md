# Market Analysis — Phase 2 Claude Code Prompts

Three ready-to-paste prompts, one per Phase 2 slice. Each restates the architectural context so you can paste it into a fresh Claude Code session (Plan Mode recommended) without re-explaining the project.

**Dependencies:** all three Phase 2 slices depend on Slice 1.6 (the complete, wired-up daily digest pipeline) — nothing else from Phase 1 is strictly required. None of the Phase 2 slices depend on each other, so all three can run in parallel once Slice 1.6 is merged.

**Suggested session order:** 2.2 has no DTO overlap and can run any time, in parallel with everything else. 2.1 and 2.3 both extend the same classifier response DTO from Slice 1.5 (`ImpactClassificationResponse`), and Phase 3's Slice 3.3 extends it too — the zero-risk path is to run these three **sequentially, in the order 2.1 → 2.3 → 3.3**, merging each into mainline before starting the next Claude Code session. This avoids any merge-conflict babysitting on the shared DTO. See the cross-slice coordination note at the end for the fallback (parallel + manual diff review) if you'd rather trade risk for speed.

**Step-by-step checklist for this sequence:** use the "Market Analysis — Sequential Merge Checklist" document for the concrete before/during/after steps for each of 2.1, 2.3, and 3.3, plus a final cross-check once all three are merged.

---

## Prompt 2.1 — Guidance history tracking and beat/raise quantification

```
Implement Slice 2.1 of the Market Analysis module: guidance history
tracking and beat/raise quantification, per the "Market Analysis" section
of CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 is complete —
domain/marketanalysis/ has the core domain model and ports, and
application/marketanalysis/MarketDigestService.java runs the full daily
research → enrich → classify → filter → save → notify pipeline (Slice
1.6). Slice 1.5's classifier
(infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapter.java) uses
Spring AI structured output to classify news entries.

IMPORTANT — check for schema conflicts before starting: this slice needs
to extend the classifier's structured-output schema so it can also
propose a guidance comparison for EARNINGS entries. Slice 2.3 (regulatory
metadata) and Phase 3's Slice 3.3 (deal terms) may also extend this same
schema. Read the current state of
infrastructure/marketanalysis/ai/ImpactClassificationResponse.java and
infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java FIRST.
Add your new field as an ADDITIONAL optional field alongside whatever's
already there — do not overwrite or remove fields you don't recognize.

Goal: persist each tracked company's earnings guidance over time in a
standing reference table (not scoped to a single digest), and use it to
precisely quantify a "beat/raise" as an exact numeric delta (e.g.
"guidance raised from $120-125M to $130-135M") instead of restating the
news qualitatively.

Create:
- domain/marketanalysis/GuidanceComparison.java
- domain/marketanalysis/port/GuidancePort.java
- db/migration/V<next>__create_guidance_history.sql — check the actual
  latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/GuidancePortAdapter.java
- application/marketanalysis/GuidancePortAdapterTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — add (or begin) an
  enrichWithGuidanceAndRatings step; this slice only implements the
  guidance half (ratings enrichment is a separate slice, do not implement
  it here — leave a clear TODO or stub if the method signature needs to
  exist for both halves).
- infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java —
  extend the schema so EARNINGS entries also extract a proposed new
  guidance range (metric name, low, high) from the source text, if stated.
- infrastructure/marketanalysis/ai/ImpactClassificationResponse.java — add
  an optional field for the classifier's proposed guidance range.

guidance_history table: standing reference table keyed by
(ticker_symbol, metric, given_at) with a unique constraint on that triple
to prevent duplicate rows from a re-run of the same day's digest. Columns:
ticker_symbol, metric (e.g. "FY2026_REVENUE"), range_low, range_high,
given_at, source_digest_entry_id (nullable FK, for traceability).

GuidanceComparison fields: tickerSymbol, metric, priorRangeLow (nullable),
priorRangeHigh (nullable), newRangeLow, newRangeHigh, isInitialGuidance
(boolean).

Implementation notes:
- getPriorGuidance(ticker, metric) looks up the most recent prior record
  for that ticker/metric pair, independent of which digest first recorded
  it — this is a standing lookup table, not digest-scoped.
- Handle the first-ever guidance for a ticker/metric pair gracefully:
  getPriorGuidance() returns Optional.empty(), so the comparison should
  render as "initial guidance" (isInitialGuidance = true, prior fields
  null) rather than throwing or computing a delta against nothing.
- After computing the delta, call recordGuidance() to persist the new
  guidance as the latest record for that ticker/metric.
- Only attempt guidance extraction for EARNINGS category entries — other
  categories should never populate this field.

Acceptance criteria:
1. The first-ever guidance recorded for a ticker/metric pair produces a
   GuidanceComparison with isInitialGuidance = true and no prior range
   (handled explicitly, not thrown as an error).
2. A second guidance for the same ticker/metric correctly computes
   low/high deltas against the immediately prior record, not the oldest
   historical record.
3. The unique constraint on (ticker_symbol, metric, given_at) prevents
   duplicate rows when the same day's digest generation is re-run.
4. An enriched EARNINGS entry includes the guidance delta end-to-end,
   verified against a fixture that runs through the actual pipeline (or
   a slice of it).
5. Existing Slice 1.5 classifier tests (and any other classifier-schema
   consumers already in the codebase) still pass after this change.

When done, run the full test suite (not just new tests) and confirm
everything passes before summarizing. Explicitly report whether you found
existing extensions to ImpactClassificationResponse from other slices and
how you avoided conflicting with them.
```

---

## Prompt 2.2 — Peer group tagging and company database cross-reference

```
Implement Slice 2.2 of the Market Analysis module: peer group tagging and
company database cross-reference, per the "Market Analysis" section of
CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 is complete.
This project already has an existing AI-healthcare company database
(populated via the Agent API structured extraction + wide-research
pipeline from the Trends feature) — find that existing table/repository
before writing any new schema. Do not create a second company registry.

Goal: tag each AffectedCompany in a digest entry with a PeerGroup (e.g.
AI_SCRIBE_DOCUMENTATION, DRUG_DISCOVERY_AI, CLINICAL_DECISION_SUPPORT —
define a reasonable enum covering AI-healthcare sub-sectors), and
cross-reference against the existing company database so private/
unlisted peers can be named in a digest entry even when they have no
market data of their own.

Create:
- domain/marketanalysis/PeerGroup.java
- infrastructure/marketanalysis/companydb/PeerGroupResolver.java (queries
  the existing company DB/table)
- infrastructure/marketanalysis/companydb/PeerGroupResolverTest.java

Modify:
- domain/marketanalysis/AffectedCompany.java — add a peerGroup field
  (nullable).
- infrastructure/marketanalysis/persistence/MarketDigestEntity.java — add
  a peer_group column to the affected-company persistence mapping.
- db/migration/V<next>__add_peer_group_to_affected_company.sql — check the
  actual latest migration version number in this repo first.

Implementation notes:
- Before writing the migration, check the existing company table's
  current schema. If it already has a sector/category field that maps
  cleanly onto PeerGroup, reuse that rather than adding a redundant
  column to two different tables.
- PeerGroupResolver does a simple lookup-by-name-or-ticker against the
  existing company table — this is a read path, not a new ingestion
  pipeline.
- The Slice 1.5 classifier is a reasonable place to propose a PeerGroup
  for a company it hasn't seen before (one not found in the existing
  company DB) — but PeerGroupResolver's result for KNOWN companies always
  overrides any classifier guess. Only extend the classifier schema for
  this if you're confident it won't collide with other slices' schema
  extensions (check ImpactClassificationResponse.java's current state
  first, same caution as other slices touching that file).
- Add a needsReview boolean (or similar) to AffectedCompany so an
  unrecognized company's classifier-proposed peer group is flagged for
  manual review rather than silently trusted as ground truth.

Acceptance criteria:
1. A known public company already in the company DB with an assigned
   peer group resolves to the correct PeerGroup without an LLM call.
2. An unrecognized company falls back to the classifier's proposed peer
   group and is flagged via needsReview = true rather than silently
   trusted.
3. Given an earnings entry for a company tagged with a specific peer
   group, a query for "other companies in this peer group" returns known
   private peers from the existing company DB.
4. Existing digest entries created before this slice landed do not break
   when read back — peer_group is nullable and backward compatible.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 2.3 — Regulatory rulemaking lifecycle tracker and deadline sweep

```
Implement Slice 2.3 of the Market Analysis module: a regulatory rulemaking
lifecycle tracker with a deadline sweep, per the "Market Analysis" section
of CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 is complete —
application/marketanalysis/MarketDigestService.java runs the daily
research → enrich → classify → filter → save → notify pipeline (Slice
1.6), and infrastructure/marketanalysis/notification/MarketDigestFormatter.java
(Slice 1.8) renders the email/API digest body.

IMPORTANT — check for schema conflicts before starting: this slice
extends the classifier's structured-output schema to extract jurisdiction,
docket ID, lifecycle stage, and comment deadline for REGULATORY entries.
Slice 2.1 (guidance) and Phase 3's Slice 3.3 (deal terms) may also extend
this same schema. Read the current state of
infrastructure/marketanalysis/ai/ImpactClassificationResponse.java and
infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java FIRST.
Add your new fields as ADDITIONAL optional fields alongside whatever's
already there — do not overwrite or remove fields you don't recognize.

Goal: track a regulatory item (FDA discussion paper, EU AI Act deferral,
MHRA guidance, etc.) across its lifecycle stage over time, and proactively
surface it again when a comment deadline is within 7 days — even on a day
with no fresh news about it.

Create:
- domain/marketanalysis/RulemakingStage.java (enum, e.g.
  DISCUSSION_PAPER, PROPOSED_RULE, COMMENT_PERIOD, FINAL_RULE, WITHDRAWN)
- domain/marketanalysis/Jurisdiction.java (enum, e.g. FDA, EU_AI_ACT,
  MHRA, OTHER)
- domain/marketanalysis/RegulatoryTracker.java
- domain/marketanalysis/port/RegulatoryTrackerRepository.java
- db/migration/V<next>__create_regulatory_tracker.sql — check the actual
  latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/RegulatoryTrackerRepositoryAdapter.java
- application/marketanalysis/RegulatoryDeadlineSweepTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — add a step
  (alongside the existing pipeline, run once per digest generation) that
  sweeps for regulatory items with an approaching deadline and includes
  them in the digest even if there's no fresh news that day.
- infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java —
  extend the schema so REGULATORY entries additionally extract
  jurisdiction, docket ID (nullable), lifecycle stage, and comment
  deadline (nullable date), if stated in the source text.
- infrastructure/marketanalysis/ai/ImpactClassificationResponse.java — add
  the corresponding optional fields.
- infrastructure/marketanalysis/notification/MarketDigestFormatter.java —
  render a distinct callout for deadline-driven entries (visually
  distinguishable from same-day news entries in the email/API output).

regulatory_tracker table: upserted keyed on (jurisdiction, docket_id).
Handle entries with no discernible docket ID (a vague policy statement
with nothing to key on) by allowing docket_id to be null — but a plain
composite unique constraint on (jurisdiction, docket_id) would treat
multiple null-docket-id rows as colliding under most Postgres unique
constraint semantics only if NULLs aren't treated as distinct, so use a
PARTIAL unique index (`docket_id IS NOT NULL`) to allow arbitrarily many
null-docket-id rows while still enforcing uniqueness for real docket IDs.

Implementation notes:
- A new REGULATORY entry referencing an existing docket ID should update
  that tracker row's stage/last_updated_at rather than creating a
  duplicate — this means the repository adapter needs an upsert path
  (INSERT ... ON CONFLICT or find-then-update) keyed on
  (jurisdiction, docket_id).
- findApproachingDeadlines(Duration.ofDays(7)) must be idempotent-safe
  against being called every single day for the full 7-day approach
  window — don't re-surface the same deadline in the digest every day for
  a week. Recommend surfacing it once at T-7 days and once at T-1 day
  (make both thresholds configurable properties rather than hardcoded).
  Track a lastSurfacedAt or similar field on RegulatoryTracker to
  implement this.
- Only attempt this structured extraction for REGULATORY category
  entries.

Acceptance criteria:
1. A new REGULATORY entry for a previously-unseen docket creates a
   regulatory_tracker row at DISCUSSION_PAPER stage (or whatever the
   correctly-extracted initial stage is).
2. A later entry referencing the same docket ID updates the existing
   row's stage (e.g. to COMMENT_PERIOD) rather than duplicating it.
3. findApproachingDeadlines(Duration.ofDays(7)) returns a tracker whose
   comment_deadline is 6 days away and excludes one that's 10 days away.
4. A day with zero fresh regulatory news but one approaching deadline
   still triggers a notification containing the deadline callout.
5. Entries with a null docket ID never collide with each other under the
   partial-unique-index constraint (verify by persisting two null-docket
   rows for the same jurisdiction and confirming no constraint violation).
6. Existing Slice 1.5 classifier tests (and any other classifier-schema
   consumers) still pass after this change.

When done, run the full test suite (not just new tests) and confirm
everything passes before summarizing. Explicitly report whether you found
existing extensions to ImpactClassificationResponse from other slices and
how you avoided conflicting with them.
```

---

## Notes on running these sessions

- **Decision: run 2.1 → 2.3 → 3.3 sequentially, merging after each one, rather than in parallel.** All three extend the same `ImpactClassificationResponse.java` / `ImpactClassificationPrompts.java` DTO from Slice 1.5. Both 2.1's and 2.3's prompts instruct Claude Code to read the current file state first and add fields alongside whatever's already there, which makes parallel execution survivable but still requires a careful manual diff review if you go that route. Running them one at a time with a merge in between removes that risk entirely — this is now the adopted plan, not just a fallback.
- **2.2 has no DTO collision risk** — it's file-disjoint from 2.1/2.3/3.3 and can run any time, independent of this sequence, unless you choose to let the classifier propose peer groups for unknown companies (the prompt flags this as optional and tells Claude Code to check for collisions first if it goes that route).
- **Before each session:** paste the corresponding prompt as-is into a fresh Claude Code Plan Mode session. Review the plan it proposes against the acceptance criteria before letting it execute.
