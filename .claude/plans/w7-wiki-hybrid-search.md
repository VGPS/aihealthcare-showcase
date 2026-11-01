# Slice W7 — Wiki Hybrid Search (Keyword + Semantic Merge)

> **Status: NOT STARTED — saved for possible future implementation.**
> Continues the Wiki slice lineage (W1 domain, W2 persistence, W3 reversal watch,
> W5 provenance UI, W6 linter). Discussed 2026-10-23 alongside a review of a
> hybrid-search (BM25 + vector + RRF) article; this is the one piece of that
> discussion judged worth doing, scoped narrowly to Wiki only.

---

## Slice Summary

**What it is:** Add a semantic (vector) retrieval leg alongside the existing keyword
leg in Wiki search, so paraphrased or synonym queries can find relevant wiki pages —
not just literal substring matches.

**Why it's worth doing (unlike the Article/AI Search side of the same discussion):**
`WikiQueryAdapter.findRelevantPages()` runs a plain SQL `LIKE` query only. Its own
class-level javadoc already says: *"Phase 1 uses simple LIKE queries for relevance;
future phases may upgrade to pgvector semantic similarity."* This gap is not
hypothetical — a substring match structurally cannot find a paraphrase (e.g. a user
searching "AI chatbot regulation" will never find a page titled "Illinois WOPR Act"
via `LIKE`, no matter how relevant that page is). Unlike Article/AI Search, which
already has a separate exact-match escape hatch (`/dashboard/search`), Wiki has only
one search path today — so this gap has no existing mitigation.

**Why it's scoped to Wiki only:** Article/AI Search has the mirror-image problem
(vector-only, misses exact identifiers) but is mitigated by the existing
`/dashboard/search` page — see the 2026-10-23 UI hint added to `ai-search.html`
pointing users there. Building a shared "hybrid search" abstraction across both
`ArticleSearchPort` and `WikiQueryPort` would be a premature abstraction (two
different ports, two different consumers, two different failure modes) — avoid it.

---

## Problem

`WikiQueryAdapter.findRelevantPages(query, maxResults)` (in
`infrastructure/persistence/WikiQueryAdapter.java`) delegates entirely to
`WikiPageRepository.searchByKeyword(query)` — a `LIKE`-based SQL query with no
ranking and no semantic understanding. This is the sole retrieval path for:
- `/wiki` search
- `/wiki/{slug}` related-pages lookups
- The "Ask the Wiki" feature (if it calls this port — verify during implementation)

It is also called with `findRelevantPages("", 10000)` by three callers that want
**every** page, not a relevance-ranked subset:
- `WikiLintScheduler`
- `WikiLintController`
- `FeedHarvestScheduler`

Any change must preserve that "empty query → everything" contract exactly, or the
wiki linter silently breaks.

## Goal

Merge a vector-similarity leg into `findRelevantPages()` for non-blank queries only,
without changing the `WikiQueryPort` interface signature and without touching the
empty-query fast path.

## Constraints (must not break)

- `WikiQueryPort` interface signature stays identical — drop-in replacement.
- Empty-query behavior (`findRelevantPages("", N)`) returns exactly what it returns
  today — unchanged code path, not just "equivalent output."
- No shared `HybridSearchService` or similar cross-port abstraction.
- Domain purity preserved — all new logic (embedding, vector query, merge) lives in
  `infrastructure.persistence` / `infrastructure.ai`, never in `domain`.
- No change to `ArticleSearchPort`, `VectorStoreArticleSearchAdapter`, or the article
  embedding pipeline — fully independent of this slice.

## Design

1. **Embed wiki pages.** New scheduler (or extension of the existing pattern used by
   `EmbeddingScheduler`) embeds `WikiPageEntity.contentMarkdown` (+ title) into a
   vector store. Two options, decide at implementation time:
   - **(a) Shared store, metadata-tagged** — reuse the existing `PgVectorStore` bean
     used for articles, tag each wiki embedding with metadata
     `{"type": "wiki_page", "slug": ...}`, filter by that tag when querying. Less
     code, no new table/bean.
   - **(b) Separate store** — a dedicated `wiki_page_embeddings` table/bean. Cleaner
     isolation, more boilerplate.
   - **Recommendation: start with (a).** Revisit only if metadata filtering proves to
     be a real bottleneck (unlikely at current wiki page counts).
   - Idempotency: track embedding state per page (e.g. `lastEmbeddedRevision` on
     `WikiPageEntity`), so a re-compiled page (new revision) gets re-embedded, mirroring
     how `EmbeddingScheduler` tracks `embedded` on `NewsArticleEntity`.

2. **Merge on query.** In `WikiQueryAdapter.findRelevantPages()`:
   - If `query` is blank → **unchanged**: `pageRepository.searchByKeyword(query)`,
     capped at `maxResults`, exactly as today.
   - If `query` is non-blank:
     a. Run the existing keyword query → exact-match hits (high confidence).
     b. Run a new vector similarity query (topK = `maxResults`) → semantic hits.
     c. Merge: keyword hits first (guaranteed inclusion — do **not** subject them to
        rank fusion, per the lesson from the hybrid-search research that naive RRF
        can bury an exact match behind consensus), then fill remaining slots up to
        `maxResults` with semantic hits not already present, deduped by slug.

3. **New dependency.** `WikiQueryAdapter` gains a `VectorStore` (or a thin port
   wrapping it) constructor dependency. This is a new cross-infrastructure dependency
   but stays hexagonal-compliant — only `domain` must remain framework-free; adapters
   depending on other infrastructure beans is normal.

## Files

### Create
| File | Notes |
|------|-------|
| `infrastructure/scheduler/WikiEmbeddingScheduler.java` | Embeds wiki pages needing (re-)embedding; mirrors `EmbeddingScheduler` structure |
| `WikiEmbeddingSchedulerTest.java` | Mockito tests mirroring `EmbeddingSchedulerTest` |

### Modify
| File | Change |
|------|--------|
| `infrastructure/persistence/WikiPageEntity.java` | Add `lastEmbeddedRevision` (or `embedded` boolean) column |
| `infrastructure/persistence/WikiPageRepository.java` | Add query method to find pages needing embedding |
| `infrastructure/persistence/WikiQueryAdapter.java` | Inject `VectorStore`; add semantic leg + merge/dedupe in `findRelevantPages()`; preserve empty-query fast path verbatim |
| `WikiQueryAdapterTest.java` | Add: empty query unchanged; non-blank query merges + dedupes by slug; keyword hits ranked before pure-semantic hits |
| `infrastructure/config/AppConfig.java` | Wire `VectorStore` into `WikiQueryAdapter` construction |

### Verify (no changes expected, but must re-run)
| Test | Why |
|------|-----|
| `WikiLintSchedulerTest` | Confirms empty-query "return everything" contract still holds |
| `WikiLintControllerTest` | Same |
| `FeedHarvestSchedulerTest` (wiki-lint-related assertions) | Same |

## Testing notes

- Unit test the merge/dedupe logic directly against mocked keyword + vector results —
  no real embedding calls in unit tests.
- The embedding step itself needs only a Mockito-based scheduler test (mock
  `EmbeddingModel`/`VectorStore`), same pattern as `EmbeddingSchedulerTest` — no live
  AI calls, no `ai-integration` profile needed for this logic.
- Manual smoke check after implementation: search the live `/wiki` page for a
  paraphrased query (e.g. "AI chatbot regulation") and confirm a semantically relevant
  page now surfaces that wouldn't have matched via `LIKE` alone.

## Estimated effort

**Roughly 5–8 hours — call it one focused working day.**

| Piece | Estimate |
|-------|----------|
| New embedding scheduler + tests (mirrors existing pattern closely) | 1–1.5 hrs |
| `WikiPageEntity`/`WikiPageRepository` changes (track embedded revision) | 30–45 min |
| `WikiQueryAdapter` merge/dedupe logic + `VectorStore` wiring + preserving empty-query path | 1.5–2 hrs |
| `AppConfig` wiring | 15–30 min |
| Regression check on the 3 empty-query callers | 30 min |
| Manual verification against live wiki data | 30–45 min |
| Buffer for Spring AI `VectorStore`/pgvector API friction | 30–60 min |

This is bigger than the Article/AI Search option discussed alongside it (which was a
true half-day job reusing an existing query path) because this slice needs a new
embedding step and an entity change, not just a merge of two already-existing
queries. The buffer line exists because this project has hit real Spring AI API
breaking changes before (see `project_spring_ai_quirks` memory and the PC-1 slice) —
budget for at least one surprise in the `VectorStore` API surface.
