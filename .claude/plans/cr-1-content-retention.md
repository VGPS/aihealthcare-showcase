# CR-1 Plan: Copyright-Safe Content Retention

## Problem

The app stores scraped/harvested article content indefinitely with no length cap enforcement
at the persistence boundary and no expiry. This creates two concrete legal risks given the
app scrapes public web pages, re-summarizes content via LLM, and monetizes via Stripe:

1. **Near-full-text storage**: `WebPageHarvester` stores up to 10,000 chars of scraped HTML
   content as `bodyText`. RSS articles store whatever `entry.getDescription()` returns
   (usually short, but uncapped). No architectural boundary prevents a long scrape from
   landing in `news_articles.body_text` as effectively a full-text archive.

2. **No expiry**: `NewsArticleRepository` has zero delete methods. Every article ever
   harvested sits in the DB forever. There is no TTL, no sweep job, no age-out path.

3. **No robots.txt compliance**: `WebPageHarvester.fetchPageHtml()` fetches any configured
   URL with no `robots.txt` check. The user-agent string (`AIHealthcare-Monitor/1.0`) is
   set, but disallow directives are never consulted.

These match the fact patterns from NYT v. Perplexity and News Corp v. Perplexity.

## What This Is NOT

- Not a new `source_documents` table or parallel content store — extends existing entities.
- Not a change to LLM prompts or answer formatting (that's a separate eval concern).
- Not a full document-management system or licensing subsystem.
- Not touching the wiki layer's `SourceRef` provenance (already handles attribution well).

---

## Slice Scope — 3 Changes

### 1. Tiered excerpt cap at ingestion boundary

Flat 500-char cap is too aggressive — it would degrade AI search synthesis (5 adapters
pass full bodyText as LLM context), keyword matching (watchlist, trends, deals,
relationships all search bodyText), embedding quality, and NotebookLM export. But
COMPETITOR-tier scraped pages are the highest-risk content and aren't used by any of
those synthesis paths. Solution: tiered caps.

**Caps by source tier:**

| Tier | Cap | Rationale |
|------|-----|-----------|
| COMPETITOR | 500 chars | Highest risk — scraped full web pages; only used for change detection + monitoring, not LLM synthesis |
| INDUSTRY | 2,000 chars | News articles — enough for keyword matching, embeddings, and LLM context; well below full-article |
| ACADEMIC | 2,000 chars | Abstracts are usually <1,500 chars anyway; cap makes it structural |
| REGULATORY | 2,000 chars | Government publications, low risk but cap for consistency |
| LEGAL | 2,000 chars | Policy/compliance sources |
| RESEARCH | 2,000 chars | Research institutions |

**Impact analysis (66 bodyText read sites across 35 files):**
- LLM adapters (sentiment, framework, deal, scoring, digest) — already truncate to 300–500
  chars themselves; no impact from a 2,000-char cap
- AI search adapters (5) — currently pass full bodyText; 2,000 chars is a reduction but still
  provides meaningful synthesis context (most RSS descriptions are under 1,000 chars already)
- Keyword matching (watchlist, trends, deals, relationships) — 2,000 chars preserves the
  opening content where most keywords appear; minimal signal loss
- Embedding scheduler — 2,000 chars is sufficient for discriminating vector embeddings
- NotebookLM export — thinner but still usable; title + 2,000-char body is a substantive entry
- Templates (articles, company detail, social posts) — all display 200–250 char previews; no impact
- COMPETITOR pages — only used for monitoring/change detection, not synthesis; 500 is fine

**Files changed:**
- New: `ExcerptTruncator.java` in `infrastructure.ingestion` — shared utility, accepts a
  `maxChars` parameter, sentence-boundary truncation
- `WebPageHarvester.java` — `MAX_BODY_LENGTH`: 10,000 → 500 chars, use `ExcerptTruncator`
  with sentence-boundary trim instead of raw `substring()`
- `RomeFeedHarvester.java` — pass RSS `bodyText` through `ExcerptTruncator(2000)` before
  building `NewsArticle` (RSS descriptions are usually <1,000 chars already, but this makes
  the cap structural rather than accidental)
- `ArticleContentEnricher.java` — if enrichment produces longer body text, re-truncate
  through `ExcerptTruncator` at the tier-appropriate cap after enrichment

**Truncation logic:**
```
truncate(text, maxChars):
  if text.length <= maxChars: return text
  // Find last sentence-ending punctuation (.!?) at or before maxChars
  // If found: return text up to and including that punctuation
  // If not found: truncate at last space before maxChars, append "..."
```

The original memo's `min(maxChars, 15% of original)` rule is dropped — it would produce
absurdly short excerpts for long articles (15% of a 500-char abstract = 75 chars). The
hard cap per tier is sufficient.

**Test:** `ExcerptTruncatorTest` — sentence-boundary trim, cap enforcement at 500 and 2000,
short-input passthrough, no-sentence-boundary fallback, empty/null input.

### ~~2. Article expiry with sweep job~~ — REMOVED

**Decision (2026-09-05):** This app is the repository of record for all things AI in
Healthcare. No articles are ever deleted. The excerpt cap at ingestion (Change 1) is the
copyright boundary — we store excerpts, not full articles, but we store them permanently.
The sweep job, `expiresAt` column, TTL map, and backfill logic were all removed before
the first commit.

### 2. robots.txt gate for WebPageHarvester

**Files changed:**
- New: `RobotsTxtGate.java` in `infrastructure.ingestion.web` — fetches and caches
  `robots.txt` per domain (24h cache via `ConcurrentHashMap` + timestamp), checks path
  against disallow rules for `AIHealthcare-Monitor` and `*` user-agents
- `WebPageHarvester.java` — inject `RobotsTxtGate`, call `isAllowed(url)` before
  `fetchPageHtml()`; skip + log at WARN if disallowed
- New: `SourceDomainDenylist` in `infrastructure.ingestion.web` — simple `Set<String>` loaded
  from `application.yml` at `aihealthcare.ingestion.domain-denylist` (empty by default);
  denylist overrides robots.txt allow
- `application.yml` — add `aihealthcare.ingestion.domain-denylist: []`

**Scope limit:** robots.txt gate applies ONLY to `WebPageHarvester` (the raw HTML scraper).
`RomeFeedHarvester` consumes public RSS feeds (explicitly published for syndication).
`HuggingFaceHarvester` uses a public API. Neither needs robots.txt gating.

**Test:** `RobotsTxtGateTest` — disallowed path blocks, allowed path passes, denylist
overrides allow, missing robots.txt defaults to allow, cache TTL respected.

---

## Files NOT Changed

- `NewsArticle.java` (domain record) — no new fields; `expiresAt` is infrastructure-only
  (the domain doesn't care about retention policy)
- `NewsletterRenderer.java` — doesn't touch bodyText, not involved
- Wiki layer (`SourceRef`, `WikiPage`) — already has proper attribution
- LLM prompts — out of scope (prompt quality is a separate concern)
- `source_documents` table — not created; reuse `news_articles`
- `AnswerAttributionAssembler` — not needed; attribution already exists via wiki `SourceRef`
  and newsletter section source links

## Deferred (Not This Slice)

- **License status enum/constraint** — nothing is currently under a paid license agreement;
  add when that becomes real, not speculatively
- **N-gram overlap guardrail** — the render path already paraphrases via LLM; verify prompt
  doesn't invite verbatim reproduction before building detection
- **`source_domain_policy` table** — the denylist `Set<String>` is sufficient until there are
  enough policy variations to warrant a table
- **Per-domain rate limiting** — `WebPageHarvester` runs once daily on ~4 configured pages;
  rate limiting is overkill at this scale

## Test Targets (actual)

| Test class | Count | What it covers |
|---|---|---|
| `ExcerptTruncatorTest` | 10 | Sentence-boundary trim, cap at 500 and 2000, short passthrough, empty/null input |
| `RobotsTxtGateTest` | 6 | Disallow, allow, denylist override, missing robots.txt, cache |
| `WebPageHarvesterTest` | 13 | Existing 11 + robots.txt blocking + 500-char truncation |
| `ArticleContentEnricherTest` | 11 | Existing tests updated for 2000-char cap |
| `ArticleStorageAdapterTest` | 5 | Existing tests unchanged |
| **Total** | **45** | |

## Build Order (actual)

```
1. ExcerptTruncator + tests (pure utility, no dependencies)
2. RobotsTxtGate + tests (independent of 1)
3. WebPageHarvester update (wire in ExcerptTruncator(500) + RobotsTxtGate)
4. RomeFeedHarvester update (wire in ExcerptTruncator(2000))
5. ArticleContentEnricher update (cap 10K→2K, use ExcerptTruncator)
6. application.yml config additions
```

## Config Additions (application.yml)

```yaml
aihealthcare:
  ingestion:
    domain-denylist: []
      RESEARCH: 2000
```
