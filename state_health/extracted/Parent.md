# Parent — Market Analysis Module Build Guide

This is the entry-point document for building the Market Analysis module. If you are Claude Code operating in Agent Mode: read this document fully before touching any code, then work through the "Execution Order" section below one step at a time, following the referenced companion document at each step. Do not skip ahead or reorder steps — several later steps depend on earlier ones being merged first.

## What this module does

Market Analysis is a daily automated pipeline that researches, classifies, ranks, and delivers a digest of AI-healthcare market news. Each day it:

1. Searches for fresh news across earnings, regulatory actions, funding rounds, M&A, and major partnerships in the AI-healthcare sector.
2. Enriches that news with market data (price/volume), earnings guidance deltas, peer-group context, analyst rating changes, and structured deal terms where applicable.
3. Classifies each item's factual status (confirmed vs. speculative) and market impact using an LLM.
4. Filters to only the items that clear a materiality bar, ranks them, and de-duplicates near-identical stories using vector similarity.
5. Persists the digest, exposes it over REST (including personal watchlists and a weekly rollup view), and notifies subscribers.
6. Maintains a source-linked registry of every enacted U.S. state law regulating AI in healthcare (Slices 2.4–2.5), seeded from a researched dataset and kept current by a weekly source-freshness check and a weekly new-bill discovery sweep that a human reviewer closes out.

The module is built in **three phases**, each broken into independently implementable **slices** (21 total: the original 19 plus Slices 2.4–2.5, the State Health-AI Legislation Registry). Phase 1 builds the core loop end-to-end with the minimum viable version of every step. Phase 2 and Phase 3 layer richer enrichment on top, in parallelizable slices — including two zero-incremental-cost slices (3.6, 3.7) that reuse the Slice 1.7 Alpaca account for a secondary news cross-check and structured corporate-actions confirmation.

## Architecture conventions (apply to every slice, every phase)

- **Hexagonal architecture.** Domain types and port interfaces live under `domain/marketanalysis/`, with zero framework dependencies. Adapters implementing those ports live under `infrastructure/marketanalysis/<concern>/`. Application services orchestrating the pipeline live under `application/marketanalysis/`. REST controllers live under `web/marketanalysis/`. The one exception is the State Health-AI Legislation Registry (Slices 2.4–2.5), which is its own bounded context under `domain/legislation/`, `infrastructure/legislation/`, `application/legislation/`, and `web/legislation/` — same layering rules, separate package tree.
- **Spring Boot / Spring AI / PostgreSQL with pgvector.** LLM calls go through Spring AI's structured-output support against Claude via Bedrock. Migrations are Flyway files under `db/migration/`.
- **CLAUDE.md dependency.** Every slice prompt assumes the "Market Analysis" section already exists in the target repo's `CLAUDE.md` (domain model, ports, schema, provider decisions). Confirm this section is present before running Slice 1.1 — if it is not, stop and add it first rather than improvising the spec inline.
- **Migration numbering.** Every slice with a `db/migration/V<next>__...sql` file requires checking the actual latest migration version number in the repo at the time you implement that slice — `<next>` is a placeholder, never a literal filename. Re-check this before every single slice, since it shifts each time a prior slice merges a new migration.
- **Backward compatibility.** Every Phase 2/Phase 3 column or field added to a Phase-1-created entity must be nullable (or have a sensible default) so existing digests continue to deserialize correctly after the new slice lands.
- **Provider decisions are final — do not re-litigate them during implementation:**
  - Slice 1.7 (market data): **Alpaca** (`https://data.alpaca.markets/v2`) — free, real-time IEX quotes, no daily cap. Market cap is not exposed; model it as `Optional<BigDecimal>`, not a blocking requirement.
  - Slice 3.1 (private funding): **Perplexity Search API**, reusing the Slice 1.4 news adapter's client — not Crunchbase or PitchBook (both are cost-prohibitive at this scale).
  - Slice 3.2 (analyst ratings): **Benzinga Analyst Ratings API**, $99/month via the Massive/Polygon partner catalog — not Finnhub (Finnhub is consensus-only; Benzinga gives per-analyst action history). Confirm the "individual use" licensing language covers this commercial product before shipping.
  - Slice 3.6 (news cross-check): **Alpaca News API** (Benzinga-sourced under the hood) — free with the existing Slice 1.7 Alpaca account, no new vendor, no incremental cost. A secondary cross-check over the tracked-ticker list only; it does not replace the Slice 1.4 Perplexity Search adapter.
  - Slice 3.7 (corporate actions): **Alpaca Corporate Actions API** — also free with the existing Slice 1.7 Alpaca account, no new vendor, no incremental cost. Structured confirmation data for mergers/spinoffs/splits/dividends, layered alongside Slice 3.3's LLM-extracted deal terms.
  - Slice 2.5 (legislation source monitoring): the **app's existing HTTP client** — hash the normalized text body of each official source URL; no new vendor. Six hosts are pre-listed as blocked (they refuse automated fetches) and must be skipped, never retried in a loop.
  - Slice 2.5 (legislation discovery): the **Perplexity Search API**, reusing the Slice 1.4 client — not LegiScan or any paid bill-tracking API. Discovery produces *candidates only*; nothing is written to the registry without human promotion.
- **Legislation registry seed is data, not code.** `state_health_ai_laws_seed.json` (version `2026-09-06`) is the contract for Slice 2.4's domain model — field names and enum codes must match it exactly. Values the seed leaves `null` (a handful of signature dates, staged effective dates) stay `null`; never backfill them from memory.

## Documents in this directory

Place all of the following in the same directory as this Parent document. Each is referenced by name below — Claude Code should open the named document at the point it's needed rather than relying on this Parent document's summary of it.

| Document | Contents |
|---|---|
| `Parent.md` | This document — overall guide and execution order. |
| `market-analysis-section.md` | The core CLAUDE.md-droppable spec: domain model, ports, schema, provider decisions. Source of truth for the architecture. |
| `market-analysis-slices.md` | All 19 slices across 3 phases, each with Goal / Depends on / Files touched / Implementation notes / Acceptance criteria. |
| `market-analysis-phase1-prompts.md` | 9 ready-to-paste Claude Code prompts for Phase 1 (Slices 1.1–1.9). |
| `market-analysis-phase2-prompts.md` | 3 ready-to-paste Claude Code prompts for Phase 2 (Slices 2.1–2.3). |
| `market-analysis-phase3-prompts.md` | 7 ready-to-paste Claude Code prompts for Phase 3 (Slices 3.1–3.7). |
| `market-analysis-merge-checklist.md` | Step-by-step before/during/after checklist for the one sequential dependency in this whole build: Slices 2.1 → 2.3 → 3.3, which share one DTO. |
| `state-health-ai-legislation-slices.md` | Slices 2.4–2.5: State Health-AI Legislation Registry — the CLAUDE.md sub-section (domain model, ports, schema, REST, scheduled jobs, provider decisions), both slices in the standard Goal / Depends on / Files touched / Implementation notes / Acceptance criteria format, cross-slice notes, and the seed data dictionary. |
| `state-health-ai-legislation-prompts.md` | 2 ready-to-paste Claude Code prompts (Slices 2.4, 2.5). |
| `data/state_health_ai_laws_seed.json` | Normalized seed dataset — 43 records (41 enacted laws / 24 states + 2 tracker-listed bills that did NOT pass), version `2026-09-06`. Copy to `src/main/resources/data/` before running Prompt 2.4. |
| `docs/legislation/state_health_ai_laws_raw.json`, `docs/legislation/state_health_ai_laws_table.md` | Research-grade originals: prose dates, evidentiary notes, pending-bill list, and corrections to public trackers. Copy to `docs/legislation/`. Reference only — never loaded by the app. |

## Execution order

Work through these steps in order. Each step names the exact document to open and what to do with it. "Merge" means: commit and merge the slice's changes into your mainline/main working branch before starting the next step — do not stack uncommitted slices on top of each other.

### Step 0 — Confirm the spec is in place

Open `market-analysis-section.md`. Confirm its content is already present in the target repo's `CLAUDE.md` under a "Market Analysis" section. If it is not there yet, add it now — every subsequent prompt assumes Claude Code can read this section as shared context.

Then open `state-health-ai-legislation-slices.md` and paste its "State Health-AI Legislation Registry" sub-section into `CLAUDE.md` directly beneath the Market Analysis section. Prompts 2.4 and 2.5 read that sub-section as their shared context. Also copy this bundle's `data/state_health_ai_laws_seed.json` to the repo's `src/main/resources/data/` and this bundle's `docs/legislation/` folder (the raw JSON and the table) to the repo's `docs/legislation/` now, so the files are in place when Step 2 reaches Prompt 2.4.

### Step 1 — Phase 1, in this internal order

Open `market-analysis-phase1-prompts.md`. Run its prompts as separate Claude Code Plan Mode sessions in this order, merging after each (or after each parallel group):

1. Prompt 1.1 (domain model and ports) — merge.
2. Prompt 1.2 (filter/ranking logic) — merge.
3. Prompt 1.3 (Postgres schema) — merge.
4. Prompts 1.4 (Perplexity news adapter), 1.5 (Claude/Bedrock classifier), and 1.7 (Alpaca market data adapter) — these three are mutually independent and may be run in parallel sessions. Merge each as it completes; it is fine if they merge in a different order than they were started, since they touch disjoint files.
5. Prompt 1.6 (scheduler/wiring — the full pipeline) — depends on Slices 1.2, 1.3, 1.4, and 1.5 being merged (per `market-analysis-slices.md`, 1.7 is not a hard dependency of 1.6, but running it in the same parallel batch as 1.4/1.5 above is still the simplest sequencing since 1.7 has no dependency on 1.6 either). Merge.
6. Prompt 1.8 (SES/REST layer) — depends on 1.6. Merge.
7. Prompt 1.9 (pgvector dedup) — depends only on Slice 1.3, not on 1.6 — it can technically be run as early as step 3 above (right after 1.3 merges) if you want to parallelize further. This guide lists it last in Phase 1 for simplicity, but don't treat step 7's position here as a hard dependency on 1.6/1.8. Merge.

At the end of Step 1 you have a complete, working, end-to-end daily digest pipeline. This is a natural checkpoint to pause and validate the whole loop manually (trigger one digest run, confirm it researches, classifies, filters, persists, and notifies) before layering Phase 2/3 on top.

### Step 2 — Phase 2 and Phase 3 slices with NO shared-DTO conflict

These may be run in any order, in parallel with each other and with Step 3 below, as soon as their individual dependencies (noted in each prompt) are merged:

- Open `market-analysis-phase2-prompts.md` → run **Prompt 2.2** (peer group tagging) — depends only on Slice 1.6. Merge.
- Open `state-health-ai-legislation-prompts.md` → run **Prompt 2.4** (state law registry: domain, schema, seed import, REST) — depends on Slices 1.3 and 1.8. Confirm the seed file is at `src/main/resources/data/state_health_ai_laws_seed.json` (Step 0) before starting. Expected result on a fresh database: exactly 43 `state_law` rows imported. Merge.
- Open `state-health-ai-legislation-prompts.md` → run **Prompt 2.5** (source freshness monitor, discovery sweep, reviewer workflow) — depends on Slices 2.4, 1.4, and 1.8. If Slice 2.3 has already merged, 2.5 will also link promoted candidates to a `RegulatoryTracker` (`Jurisdiction.US_STATE`); if 2.3 has not merged yet, that wiring is conditional and simply stays inactive — do not wait on 2.3 to run 2.5. Merge.
- Open `market-analysis-phase3-prompts.md` → run **Prompt 3.1** (private funding, Perplexity Search) — depends only on Slice 1.6. Merge.
- Open `market-analysis-phase3-prompts.md` → run **Prompt 3.2** (analyst ratings, Benzinga) — depends on Slices 1.6 and 1.7 (needs the tracked-ticker list from 1.7 plus the working pipeline from 1.6). Merge.
- Open `market-analysis-phase3-prompts.md` → run **Prompt 3.4** (ticker watchlists) — depends only on Slice 1.8. Merge.
- Open `market-analysis-phase3-prompts.md` → run **Prompt 3.6** (Alpaca news cross-check) — depends on Slices 1.6 and 1.7. Merge.

### Step 3 — The one sequential dependency: 2.1 → 2.3 → 3.3

Slices 2.1, 2.3, and 3.3 all extend the same classifier response DTO (`ImpactClassificationResponse` / `ImpactClassificationPrompts`, first created in Slice 1.5). Do not run these in parallel with each other. Open `market-analysis-merge-checklist.md` and follow it exactly — it has a dedicated before/during/after section for each of the three slices plus a final cross-check. In summary:

1. Open `market-analysis-phase2-prompts.md` → run **Prompt 2.1** (guidance history) → work through the checklist's Slice 2.1 sections → merge.
2. Open `market-analysis-phase2-prompts.md` → run **Prompt 2.3** (regulatory lifecycle tracker) → work through the checklist's Slice 2.3 sections → merge.
3. Open `market-analysis-phase3-prompts.md` → run **Prompt 3.3** (deal terms structuring) → work through the checklist's Slice 3.3 sections → merge.
4. Run the checklist's "Final verification" section.

### Step 4 — Corporate actions confirmation (depends on Step 3)

Open `market-analysis-phase3-prompts.md` → run **Prompt 3.7** (corporate actions confirmation). Its digest-entry attachment logic targets entries carrying Slice 3.3's `DealTerms`, so wait until Step 3 is fully merged before running this. (If you want to get ahead, the prompt notes that the standalone corporate-actions persistence half can start earlier — but hold the attachment wiring until 3.3 lands.) Merge.

### Step 5 — Last slice: weekly rollup

Open `market-analysis-phase3-prompts.md` → run **Prompt 3.5** (weekly rollup view). This depends on BOTH Slice 1.9 (embedding dedup) and Slice 1.8 (REST layer) being merged — confirm both before starting, not just 1.9. It is best run last of all regardless, since it benefits from Slices 3.1–3.3, 3.6, and 3.7 already being in the pipeline (more entry types available to roll up), though it will function correctly even if run as early as its two hard dependencies allow. Merge.

### Step 6 — Done

All 21 slices across 3 phases are now merged. Do a final full-suite test run and a manual end-to-end digest generation check (one EARNINGS entry, one REGULATORY entry, one M_AND_A entry, one FUNDING entry) to confirm every slice's enrichment appears correctly and nothing from one category leaks into another. Then trigger `POST /api/legislation/refresh` once in a dev profile and confirm: (a) the monitor fetched every non-blocked source and recorded baselines with no change events on the first run, (b) blocked hosts were skipped, (c) the discovery sweep completed without writing anything to `state_law`, and (d) `GET /api/legislation/state-laws/by-state` returns all state codes with Texas showing exactly `tx-sb-815` and `tx-hb-149`.

## Quick-reference dependency chain

```
1.1 → 1.2 → 1.3 → {1.4, 1.5, 1.7} → 1.6 → 1.8
              └→ 1.9 (only needs 1.3 — can start earlier than shown if parallelizing)

                  1.6 merged unlocks: 2.1, 2.2, 3.1
                  1.6 AND 1.7 (both) unlock: 3.2, 3.6
                  1.8 merged unlocks: 3.4
                  1.8 AND 1.9 (both) unlock: 3.5
                  1.3 AND 1.8 (both) unlock: 2.4
                  2.4 AND 1.4 (both) unlock: 2.5  (soft-links to 2.3 if it
                    has landed; 2.3 is NOT a prerequisite)

Sequential-only (shared DTO, do not parallelize):
  2.1 → 2.3 → 3.3

3.3 merged unlocks: 3.7 (digest-entry attachment step; standalone
  persistence half of 3.7 can start earlier on just 1.7)

3.5 runs last (benefits from 3.1–3.3, 3.6, and 3.7 in the pipeline, but
  its only hard dependencies are 1.8 + 1.9 — note this needs BOTH, not
  just 1.9)
```

## What to do if something feels ambiguous mid-build

- If a slice prompt references a file or table you can't find (e.g. "the existing company database"), stop and search the repo before improvising a new one — several Phase 2/3 slices explicitly assume reuse of pre-existing infrastructure from other parts of the codebase (the Trends/wide-research company DB, the Slice 1.4 Perplexity client).
- If two slices seem to want to touch the same file outside of the known 2.1/2.3/3.3 DTO overlap, stop and re-read `market-analysis-slices.md`'s "Cross-slice notes" section — this is the authoritative list of known coordination points. Something may have changed since these documents were written. Slices 2.4 and 2.5 are confined to `domain/legislation/**`, `infrastructure/legislation/**`, `application/legislation/**`, `web/legislation/**`, `config/LegislationConfig.java`, and one migration — if either appears to need a file outside that set (other than reusing the Slice 1.4 client, Slice 1.9 embedding provider, Slice 1.8 `SesEmailSender`, or Slice 1.6 executor), stop and re-check `state-health-ai-legislation-slices.md`'s "Cross-slice notes".
- If the seed importer reports anything other than 43 inserted / 0 updated / 0 skipped on a fresh database, or a Jackson unknown-property failure at startup, the seed file and `StateLawSeedRecord` have drifted apart — fix the DTO to match the JSON, never the other way round.
- Never guess at a provider choice, pricing tier, or licensing term that isn't already locked in above — those decisions were made deliberately after cost/API-access comparison and should not be revisited mid-implementation.
