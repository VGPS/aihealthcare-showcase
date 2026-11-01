# Market Analysis — Complete Document Bundle (v2, 2026-09-06)

This bundle contains the full spec, implementation plan, Claude Code prompts, and seed data for the Market Analysis module of the AIHealthcare application: a daily automated pipeline that researches, classifies, ranks, and delivers a digest of AI-healthcare market news — plus, new in this version, a source-linked registry of every enacted U.S. state law regulating AI in healthcare (Slices 2.4–2.5).

## Start here

Open **`Parent.md`** first. It is the entry-point document — it explains the module's overall functionality, the shared architecture conventions, the locked-in provider decisions, and the exact order to execute every step in. If you're directing Claude Code, tell it to read `Parent.md` and follow it start to finish; it will open each of the other files below by name at the point it's needed.

## Files in this bundle

| File | Purpose |
|---|---|
| `Parent.md` | Entry point. Overall guide, architecture conventions, provider decisions, and the full execution order across all three phases (21 slices). Read this first. |
| `market-analysis-section.md` | The core spec, meant to be dropped into the target repo's `CLAUDE.md` under a "Market Analysis" section. Domain model, ports, schema, and provider decisions — the source of truth every other document assumes is already in place. |
| `market-analysis-slices.md` | The original 19 implementation slices across the 3 build phases. Each slice lists its Goal, dependencies, files touched, implementation notes, and acceptance criteria. |
| `market-analysis-phase1-prompts.md` | 9 ready-to-paste Claude Code prompts for Phase 1 (Slices 1.1–1.9) — the core end-to-end daily digest loop. |
| `market-analysis-phase2-prompts.md` | 3 ready-to-paste Claude Code prompts for Phase 2 (Slices 2.1–2.3) — guidance history, peer-group tagging, regulatory lifecycle tracking. |
| `market-analysis-phase3-prompts.md` | 7 ready-to-paste Claude Code prompts for Phase 3 (Slices 3.1–3.7) — private funding, analyst ratings, deal terms, watchlists, weekly rollup, Alpaca news cross-check, corporate actions confirmation. |
| `market-analysis-merge-checklist.md` | Step-by-step before/during/after checklist for the one sequential dependency in the whole build: Slices 2.1 → 2.3 → 3.3, which all extend the same classifier response DTO and must not be run in parallel. |
| `state-health-ai-legislation-slices.md` | **New.** Slices 2.4–2.5: State Health-AI Legislation Registry. Contains the CLAUDE.md sub-section to paste beneath the Market Analysis section (domain model, ports, schema, REST, scheduled jobs, provider decisions), both slices in the standard format, cross-slice notes, and the seed data dictionary. |
| `state-health-ai-legislation-prompts.md` | **New.** 2 ready-to-paste Claude Code prompts (Slices 2.4, 2.5). |
| `data/state_health_ai_laws_seed.json` | **New.** Normalized seed dataset, version `2026-09-06` — 43 records (41 enacted laws across 24 states, plus 2 tracker-listed bills that did not pass, retained so dedupe logic suppresses them). Copy to the repo's `src/main/resources/data/` before running Prompt 2.4. |
| `docs/legislation/state_health_ai_laws_raw.json` | **New.** Research-grade original dataset with prose dates and evidentiary notes. Copy to the repo's `docs/legislation/`. Reference only — never loaded by the app. |
| `docs/legislation/state_health_ai_laws_table.md` | **New.** Human-readable state-by-state table, not-enacted list, pending bills, and corrections to public trackers. Copy to the repo's `docs/legislation/`. Reference only. |

## Build order at a glance

```
Step 0  Confirm market-analysis-section.md is in the target repo's CLAUDE.md;
        paste the Legislation Registry sub-section beneath it;
        place data/ and docs/legislation/ files in the repo
Step 1  Phase 1: 1.1 → 1.2 → 1.3 → {1.4, 1.5, 1.7 in parallel} → 1.6 → {1.8, 1.9}
Step 2  Phase 2/3 slices with no shared-DTO conflict, any order:
        2.2, 2.4, 2.5 (after 2.4), 3.1, 3.2, 3.4, 3.6
Step 3  Sequential-only (shared DTO — do not parallelize): 2.1 → 2.3 → 3.3
Step 4  3.7 (corporate actions confirmation — needs Step 3 merged first)
Step 5  Last: 3.5 (weekly rollup)
Step 6  Final full-suite test + manual end-to-end digest check
        + legislation refresh smoke test
```

See `Parent.md` for the full explanation of each step, including which document to open and what to do with it.

## Locked-in provider decisions (do not re-litigate)

- **Market data (Slice 1.7):** Alpaca — free, real-time IEX quotes, no daily cap.
- **Private funding (Slice 3.1):** Perplexity Search API, reusing the Slice 1.4 news adapter's client — not Crunchbase or PitchBook.
- **Analyst ratings (Slice 3.2):** Benzinga Analyst Ratings API, $99/month — not Finnhub.
- **News cross-check (Slice 3.6):** Alpaca News API (Benzinga-sourced), free with the existing Slice 1.7 Alpaca account — no new vendor, no incremental cost.
- **Corporate actions (Slice 3.7):** Alpaca Corporate Actions API, also free with the existing Slice 1.7 Alpaca account — no new vendor, no incremental cost.
- **Legislation source monitoring (Slice 2.5):** the app's existing HTTP client, hashing each official source URL weekly — no new vendor. Six known-blocked hosts are skipped, never retried in a loop.
- **Legislation discovery (Slice 2.5):** Perplexity Search API, reusing the Slice 1.4 client — not LegiScan or any paid bill-tracking API. Produces candidates only; nothing reaches the registry without human promotion.

Full rationale for each is in `Parent.md` and in the relevant slice's entry in `market-analysis-slices.md` or `state-health-ai-legislation-slices.md`.

## What changed from the previous bundle

- `Parent.md` updated: module overview item 6, slice count 19 → 21, legislation package-tree note, two new provider decisions, seed-is-data rule, four new document rows, Step 0 / Step 2 / Step 6 additions, two new dependency-chain lines, two new ambiguity rules.
- Added `state-health-ai-legislation-slices.md`, `state-health-ai-legislation-prompts.md`, `data/`, and `docs/legislation/`.
- All previously existing files (`market-analysis-section.md`, `market-analysis-slices.md`, the three phase prompt files, the merge checklist) are unchanged.
