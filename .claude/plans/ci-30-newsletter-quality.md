# CI-30 Plan: Newsletter Digest Quality — Cap + Weight Sort + Tier Cleanup

## Problem
The free-tier digest newsletter contained 1973 articles (subject line: "1973 News Articles From 8/15/2026").
Root cause: `DigestNewsletterRenderer.buildDigest()` called `fetchRecentArticles(3)` — all articles
ingested in the last 3 days across all 69 feeds, with no count limit. Only filter was COMPETITOR +
HUGGINGFACE exclusion, plus title dedup. With 69 feeds running daily, 3 days × all topics = thousands
of articles.

## What Was Done (CI-30)

### Core fix — `DigestNewsletterRenderer.java`
- `LOOKBACK_DAYS`: 3 → 1 (daily digest needs 1-day window, not 3)
- Added `MAX_DIGEST_ARTICLES = 75` constant
- After `filterAndDedup()`, sort by `sourceWeight` DESC using `Collections.sort()`
- Cap list to top 75 by weight (highest-signal sources always win)
- Removed "HUGGINGFACE" from exclusion filter (tier no longer exists; old DB records pass through at
  low weight rank, naturally falling below the cap)
- Fixed email subject/title: was `"N News Articles From M/d/yyyy"` → `"AI Healthcare Intelligence — Month d, yyyy"`
- `wrapInEmailLayout()` signature updated to accept subject string

### Tier system cleanup — `FeedSourceConfig.FeedTier`
- **Removed**: HUGGINGFACE tier
- **Added as top tiers** (between REGULATORY and ACADEMIC in priority):
  - LEGAL — legal, legislative, compliance sources
  - RESEARCH — research institutions, university publications

New priority order: REGULATORY ≥ LEGAL ≥ RESEARCH ≥ ACADEMIC > INDUSTRY > COMPETITOR

### `HuggingFaceHarvester.java`
- Changed tier filter from `FeedTier.HUGGINGFACE` → `FeedTier.RESEARCH`
- HuggingFace model cards now tagged as RESEARCH (weight 0.75 vs old 0.5)

### `RomeFeedHarvester.java`
- Removed `FeedTier.HUGGINGFACE` exclusion from RSS tier filter

### `SampleNewsletterRenderer.java`
- Removed "HUGGINGFACE" string from tier exclusion filter

### `application.yml` feed tier changes
| Feed | Old tier | New tier | Old weight | New weight |
|------|----------|----------|------------|------------|
| Google News AI Healthcare Legal | INDUSTRY | LEGAL | 0.7 | 0.9 |
| Google News AI Medical Device Regulation | INDUSTRY | LEGAL | 0.7 | 0.9 |
| Stanford Law and Biosciences Blog | ACADEMIC | LEGAL | 0.9 | 0.9 |
| Stanford CodeX AI Law | ACADEMIC | LEGAL | 0.85 | 0.85 |
| AI Now Institute | ACADEMIC | LEGAL | 0.85 | 0.85 |
| HuggingFace Healthcare LLMs | HUGGINGFACE | RESEARCH | 0.5 | 0.75 |

## What Was NOT Done (future work)
- [ ] Remove `HuggingFaceHarvester` pipeline entirely (it now runs as RESEARCH tier)
- [ ] Add more RESEARCH-tier feeds (university health systems, NIH, CDC research arms)
- [ ] Add `POST /monitoring/digest-newsletter/send?email=xxx` endpoint for single-address test sends
- [ ] DB migration to update existing articles with `sourceTier = 'HUGGINGFACE'` → `'RESEARCH'`
- [ ] Per-topic `sourceWeight` boosting after AI relevance scoring (dynamic quality signal)

## Expected outcomes
- Digest: ~50-75 articles/day (was 1973)
- Subject line: "AI Healthcare Intelligence — August 15, 2026" (was "1973 News Articles From 8/15/2026")
- Top articles: REGULATORY (0.85-0.95) → LEGAL (0.85-0.9) → RESEARCH/ACADEMIC → INDUSTRY
- HuggingFace model discovery survives as RESEARCH tier at weight 0.75

## Verification
After newsletter send, confirm in email:
1. Subject line shows "AI Healthcare Intelligence — [date]"
2. Article count ≤ 75
3. Top articles are from REGULATORY/LEGAL/ACADEMIC sources (high sourceWeight)
4. No HuggingFace model card IDs as article titles
