# Market Analysis — Phase 3 Claude Code Prompts

Seven ready-to-paste prompts, one per Phase 3 slice. Each restates the architectural context so you can paste it into a fresh Claude Code session (Plan Mode recommended) without re-explaining the project.

**Dependencies:** all Phase 3 slices assume Phase 1 (Slices 1.1–1.9) is complete. Slices 3.1 and 3.2 also assume Slice 1.6 (full pipeline) and, for 3.2, Slice 1.7 (tracked-ticker list) are merged. Slice 3.3 depends only on Slice 1.5 (classifier). Slices 3.4 and 3.5 depend on Slice 1.8 (REST layer), and 3.5 additionally depends on Slice 1.9 (embedding dedup). Slice 3.6 depends on Slice 1.7 (Alpaca account/auth) and Slice 1.6 (pipeline). Slice 3.7 depends on Slice 1.7 and, for its digest-entry attachment step, on Slice 3.3 (deal terms). Phase 2 slices (2.1–2.3) are NOT hard dependencies for Phase 3, but note that Slices 2.1/2.3 touch the same classifier response schema as Slice 3.3 — see the cross-slice coordination note at the end.

**Suggested session order:** 3.1 and 3.4 can start any time after their individual dependencies (1.6 and 1.8 respectively) are merged, in parallel with everything else. 3.2's only dependency (1.7) is also independent, so it can run in parallel too. 3.6 can also run in parallel any time after 1.6/1.7 are merged — it touches disjoint files. 3.3 is the one exception: it shares the `ImpactClassificationResponse` DTO with Phase 2's Slices 2.1 and 2.3, so the zero-risk plan is to run it last in the sequence **2.1 → 2.3 → 3.3**, merging each into mainline before starting the next session — do not run 3.3 in the same window as 2.1/2.3 without merging in between. 3.7 should run after 3.3 is merged, since its digest-entry attachment step targets entries carrying Slice 3.3's `DealTerms` (it can still start earlier for the standalone corporate-actions persistence, but hold the attachment wiring until 3.3 lands). 3.5 should run last of all, since it depends on 1.9 and benefits from 3.1–3.3 (and 3.6/3.7) already being in the pipeline (though it will still work without them).

**Step-by-step checklist for the 2.1 → 2.3 → 3.3 sequence:** use the "Market Analysis — Sequential Merge Checklist" document for the concrete before/during/after steps to follow when running Slice 3.3, plus a final cross-check once all three are merged.

All provider decisions below are final — do not re-litigate them in the Claude Code session:
- **Slice 3.1 (funding):** Perplexity Search API (no new vendor).
- **Slice 3.2 (analyst ratings):** Benzinga Analyst Ratings API, $99/mo.
- **Slice 3.6 (news cross-check):** Alpaca News API, free with the existing Slice 1.7 Alpaca account (Benzinga-sourced under the hood).
- **Slice 3.7 (corporate actions):** Alpaca Corporate Actions API, free with the existing Slice 1.7 Alpaca account.

---

## Prompt 3.1 — Private funding round tracking

```
Implement Slice 3.1 of the Market Analysis module: private funding round
tracking, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 of Market
Analysis is complete — domain/marketanalysis/ has the core model and
ports, application/marketanalysis/MarketDigestService.java runs the daily
research → enrich → classify → filter → save → notify pipeline, and
infrastructure/marketanalysis/perplexity/ already has a Perplexity Search
API adapter and client wrapper from Slice 1.4 (PerplexityMarketNewsAdapter)
— find and reuse that same client/wrapper rather than creating a new one.

Goal: surface private funding rounds for AI-healthcare companies (no
public ticker) via the Perplexity Search API, applying the same >$50M
qualifying threshold already used for public funding news.

PROVIDER DECISION (already made): use the Perplexity Search API rather
than a dedicated funding database like Crunchbase or PitchBook. Rationale:
Crunchbase's actual API (not just its web dashboard) sits behind a
custom-quoted Enterprise tier, typically $5,000+/year — the advertised
$49/mo Pro plan is web/UI access only, not API access. PitchBook starts
around $20,000+/year per seat. Both are out of scope for this project's
budget. Since the qualifying bar is funding rounds strictly over $50M,
these are infrequent, high-signal events that receive real press
coverage, making LLM extraction from news reasonably reliable. Do not
re-litigate this choice or build against Crunchbase/PitchBook; proceed
directly to implementation against Perplexity Search.

Create:
- domain/marketanalysis/PrivateFundingRound.java
- domain/marketanalysis/port/PrivateFundingPort.java
- infrastructure/marketanalysis/funding/PerplexityFundingAdapter.java
- db/migration/V<next>__create_private_funding_round.sql — check the
  actual latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/PrivateFundingRepositoryAdapter.java
- infrastructure/marketanalysis/funding/PerplexityFundingAdapterTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — wire in a funding
  sweep step (find the existing pipeline steps and add this as an
  additional enrichment/research source feeding the same classify/filter/
  save/notify pipeline, not a separate parallel pipeline).

Search prompt should target: "private funding rounds over $50M announced
in AI healthcare in the last 24-48 hours, with investor names, round size,
and round stage. Cite sources with URLs." Use the same recency-filtered
Search API pattern as the Slice 1.4 news adapter.

PrivateFundingRound fields should include at minimum: companyName,
dealSizeUsd (nullable), roundStage (e.g. SEED/SERIES_A/SERIES_B/etc, or
UNKNOWN), leadInvestors (list of names), announcedAt, sourceUrls.

Implementation notes:
- The mapper must be conservative: if the source article gives a range
  (e.g. "$40-60M") or doesn't clearly state a specific round size, do NOT
  guess a number — set dealSizeUsd to null and exclude it from the
  qualifying filter (which requires a known amount strictly over
  $50,000,000) rather than risk a false positive or false negative on the
  threshold.
- Implement MarketNewsItem.fromFundingRound(PrivateFundingRound) — a
  factory method mapping a PrivateFundingRound into the shared
  MarketNewsItem shape so it flows through the same classify/filter/
  notify pipeline as regularly-searched news. Category should be FUNDING.
- If a PeerGroup domain concept already exists in this codebase (check
  domain/marketanalysis/PeerGroup.java), tag the private company with it
  at ingestion if a resolver is available; otherwise leave it null — do
  not build peer-group resolution logic in this slice if it doesn't
  already exist.
- A failed/errored funding search must not fail the whole day's digest
  generation — catch and log, then continue the pipeline with whatever
  other news sources succeeded.

Acceptance criteria:
1. A private round above $50M for an AI-healthcare company appears in the
   digest with NewsCategory.FUNDING and passes the qualifying filter.
2. A private round at or below $50M (or with an unclear/unparseable
   amount) is persisted for audit but does not trigger notification.
3. A simulated provider/search failure degrades gracefully — the day's
   digest generation still completes using other news sources.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 3.2 — Analyst rating and price-target change tracking

```
Implement Slice 3.2 of the Market Analysis module: analyst rating and
price-target change tracking, per the "Market Analysis" section of
CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 is complete,
including a tracked-ticker list (Slice 1.7, likely a config property like
market-analysis.tracked-tickers) and the full daily digest pipeline
(Slice 1.6). This slice adds a new enrichment source that surfaces
sell-side analyst rating/price-target changes, since these often lag the
earnings reaction by 1-3 days and deserve their own surfacing logic rather
than only appearing bundled into same-day earnings entries.

Goal: implement a new AnalystRatingPort against Benzinga's Analyst Ratings
API, attach rating changes to the originating earnings entry when
possible, and surface standalone rating-change entries when a change
crosses a materiality bar.

PROVIDER DECISION (already made): use Benzinga's Analyst Ratings API
($99/month, via the Massive/Polygon partner catalog or Benzinga's direct
API). Rationale: it returns per-analyst action (upgrade/downgrade/
initiate/maintain/reiterate) with rating_current/rating_prior and
pt_current/pt_prior fields already computed, which map directly onto this
slice's domain type without needing to build day-over-day diffing logic
yourself — unlike Finnhub's Price Target endpoint, which only returns a
consensus snapshot (mean/median/high/low target) with no individual-
analyst-action history. Do not re-litigate this choice; proceed directly
to implementation against Benzinga.

Benzinga API basics to use:
- Endpoint: GET https://api.benzinga.com/api/v2.1/calendar/ratings
- Auth: API token as a query parameter or header per Benzinga's
  documentation (confirm exact param name against current docs before
  hardcoding — historically `token=`).
- Filter by ticker (comma-separated list) and date range using the
  endpoint's query params.
- Key response fields per rating action: ticker, date, analyst_name,
  firm name, action_pt (action on price target), action_company (action
  on the company rating, e.g. Upgrades/Downgrades/Initiates/Maintains),
  rating_current, rating_prior, pt_current, pt_prior.
- Load the API token from a config property sourced from an environment
  variable (e.g. benzinga.api.token) — do not hardcode credentials.

Create:
- domain/marketanalysis/AnalystRatingChange.java
- domain/marketanalysis/port/AnalystRatingPort.java
- infrastructure/marketanalysis/ratings/BenzingaAnalystRatingAdapter.java
- infrastructure/marketanalysis/ratings/BenzingaRatingsClientConfig.java
- db/migration/V<next>__create_analyst_rating_change.sql — check the
  actual latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/AnalystRatingRepositoryAdapter.java
- infrastructure/marketanalysis/ratings/BenzingaAnalystRatingAdapterTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — add or complete
  the ratings half of enrichment (query Benzinga for the tracked-ticker
  list on each run).

AnalystRatingChange fields should include at minimum: tickerSymbol,
analystName, firmName, actionType (UPGRADE/DOWNGRADE/INITIATE/MAINTAIN/
REITERATE), ratingCurrent, ratingPrior (nullable for initiations),
priceTargetCurrent (nullable), priceTargetPrior (nullable), changedAt.

Implementation notes:
- Query on a rolling 3-day window (not same-day only) — a rating change
  from 2 days ago that's still relevant to today's earnings-reaction entry
  should still surface.
- Attach rating changes to the originating earnings entry where possible
  (link by ticker + date proximity, e.g. within the same 3-day window) so
  they enrich that entry rather than creating orphan digest entries.
- If a rating change has no associated recent news item, only create a
  standalone digest entry when the change crosses a materiality bar:
  a 2+ notch rating downgrade OR a price-target cut greater than 20%.
  Anything below that bar should still be persisted (for the audit trail)
  but must not independently trigger notification.
- Handle initiations gracefully: ratingPrior and priceTargetPrior will be
  null/absent — don't compute a "change" for these, just record the new
  rating/target as-is.

Acceptance criteria:
1. A rating change dated within 3 days of an earnings entry for the same
   ticker is attached to that entry's enrichment.
2. A rating change with no associated recent news item, but crossing the
   materiality bar (2+ notch downgrade or >20% price-target cut), produces
   its own standalone digest entry.
3. A minor rating change (one notch, small price-target tweak) below the
   materiality bar is persisted but does not independently trigger
   notification.
4. A Benzinga API failure/timeout degrades gracefully — the day's digest
   generation still completes without ratings enrichment rather than
   failing entirely.

When done, run the tests and confirm they pass before summarizing. If you
cannot confirm the exact Benzinga auth parameter name from current public
documentation, flag this clearly and use a placeholder that's easy to
find-and-replace.
```

---

## Prompt 3.3 — Deal terms structuring

```
Implement Slice 3.3 of the Market Analysis module: deal terms structuring,
per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Slice 1.5 already
implemented a Claude/Bedrock impact classifier
(infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapter.java) that
takes a MarketNewsItem and returns a structured ImpactClassificationResponse
(fact classification, confidence score, 5 impact-dimension assessments,
market impact rank) via Spring AI's structured-output support.

IMPORTANT — check for schema conflicts before starting: Phase 2 slices
2.1 (guidance extraction) and 2.3 (regulatory metadata extraction) may
have already extended this same ImpactClassificationResponse DTO and its
prompt. Read the current state of
infrastructure/marketanalysis/ai/ImpactClassificationResponse.java and
infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java FIRST,
and add this slice's field as an ADDITIONAL optional field alongside
whatever Phase 2 already added — do not overwrite or restructure fields
you don't recognize; ask before removing anything that looks unrelated
to deal terms.

Goal: extend the classifier so that M_AND_A and MAJOR_PARTNERSHIP category
entries also extract structured DealTerms (upfront cash, milestone
payments, equity stake, royalty percentage, and how completely the deal
economics were disclosed) in the same LLM call, instead of leaving deal
economics as unstructured prose in the summary.

Create:
- domain/marketanalysis/DealTerms.java
- infrastructure/marketanalysis/ai/DealTermsExtractionTest.java

Modify:
- infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java —
  extend the schema instructions for M_AND_A / MAJOR_PARTNERSHIP entries
  specifically (do not require these fields for other categories).
- infrastructure/marketanalysis/ai/ImpactClassificationResponse.java —
  add an optional dealTerms field.

DealTerms fields: upfrontCashUsd (nullable BigDecimal), milestonePaymentsUsd
(nullable BigDecimal), maxTotalDealValueUsd (nullable BigDecimal),
equityStakePercent (nullable), royaltyPercent (nullable),
disclosedPortion (enum: FULL, PARTIAL, UNDISCLOSED).

Implementation notes:
- Explicitly instruct the model in the prompt to set
  disclosedPortion = UNDISCLOSED and leave all numeric fields null when
  the source article doesn't state deal economics at all, rather than
  guessing or hallucinating a number. This matters for real cases like an
  "up to $110M" headline with no upfront/milestone breakdown disclosed —
  the model must NOT invent a 50/50 or other split; it should extract
  maxTotalDealValueUsd = 110000000 with disclosedPortion = PARTIAL and
  leave upfrontCashUsd/milestonePaymentsUsd null.
- Only require dealTerms extraction for M_AND_A and MAJOR_PARTNERSHIP
  categories — other categories (EARNINGS, REGULATORY, FUNDING) should
  have this field absent/null, not an empty DealTerms object.
- Extend the existing single classifier call rather than adding a second
  LLM call — the model is already reading the full news item text.

Acceptance criteria:
1. A fixture representing a fully-disclosed deal (explicit upfront +
   milestone numbers stated in the source text) extracts matching numeric
   values with disclosedPortion = FULL.
2. A fixture representing an "up to $110M" style headline with no further
   breakdown extracts disclosedPortion = PARTIAL, maxTotalDealValueUsd
   populated, and the milestone/upfront split left null rather than a
   guessed split.
3. A fixture with zero dollar figures anywhere in the source text extracts
   disclosedPortion = UNDISCLOSED with all numeric fields null.
4. A fixture for a non-M_AND_A/MAJOR_PARTNERSHIP category (e.g. EARNINGS)
   does not populate dealTerms at all.
5. Existing golden-fixture tests from Slice 1.5 (and Phase 2's classifier
   extensions, if present) still pass after this change — run the full
   classifier test suite, not just the new tests, to catch regressions.

When done, run the full classifier test suite and confirm everything
passes before summarizing. Explicitly report whether you found existing
Phase 2 extensions to this DTO and how you avoided conflicting with them.
```

---

## Prompt 3.4 — Ticker watchlists

```
Implement Slice 3.4 of the Market Analysis module: ticker watchlists, per
the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Slice 1.8 already
implemented the REST layer
(web/marketanalysis/MarketDigestController.java,
web/marketanalysis/MarketDigestResponse.java) with endpoints for fetching
digests by date/latest/range and a subscribe endpoint. This slice adds
personal ticker watchlists so a subscriber can flag which tickers matter
most to them, and have digest responses sort those to the top.

Goal: let subscribers configure a personal ticker watchlist via REST, and
have MarketDigestResponse flag/sort watched-ticker entries to the top of
the response, independent of the underlying market-impact rank.

Create:
- domain/marketanalysis/port/TickerWatchlistRepository.java
- db/migration/V<next>__create_ticker_watchlist.sql — check the actual
  latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/TickerWatchlistRepositoryAdapter.java
- web/marketanalysis/TickerWatchlistRequest.java
- web/marketanalysis/MarketDigestControllerIT.java (extend existing file
  if it already has tests — add to it, don't replace it)

Modify:
- web/marketanalysis/MarketDigestController.java — add
  GET /api/market-digest/watchlist and PUT /api/market-digest/watchlist
  endpoints, scoped to the current authenticated user (match however this
  project already identifies the current user in other controllers).
- web/marketanalysis/MarketDigestResponse.java — add an isWatched boolean
  flag per entry.

Endpoint semantics:
- PUT /api/market-digest/watchlist accepts a full ticker list (e.g.
  {"tickers": ["DOCS", "GSK"]}) and REPLACES the subscriber's entire
  watchlist — this is simplest semantics (client sends the complete
  desired set) rather than incremental add/remove. An empty array clears
  the watchlist entirely (not a no-op).
- GET /api/market-digest/watchlist returns the current subscriber's
  watchlist as a list of ticker symbols.

Sorting logic (apply when building any MarketDigestResponse for a
subscriber with a non-empty watchlist):
- Partition qualifying entries into two groups: entries mentioning a
  watched ticker (via AffectedCompany.tickerSymbol), and everything else.
- Sort each group internally by MarketImpactRank ascending.
- Concatenate: watched group first, then the rest.
- Do NOT mutate or renumber the underlying rank field to accomplish this
  — that would corrupt the rank for any other subscriber viewing the same
  digest without a watchlist. This is purely a response-ordering concern.
- A subscriber with no watchlist configured sees unchanged behavior (pure
  rank sort, as before this slice).

Acceptance criteria:
1. PUT /api/market-digest/watchlist with ["DOCS","GSK"] followed by
   GET /api/market-digest/watchlist returns exactly those two tickers.
2. PUT /api/market-digest/watchlist with an empty array clears the
   watchlist (verify a subsequent GET returns an empty list, not the
   previous list).
3. For a subscriber with a non-empty watchlist, GET /api/market-digest/latest
   returns watched-ticker entries first (each internally still sorted by
   rank), followed by all other entries sorted by rank.
4. For a subscriber with no watchlist, response ordering is unchanged from
   pre-Slice-3.4 behavior (pure rank sort) — write a regression test for
   this explicitly.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 3.5 — Weekly rollup view

```
Implement Slice 3.5 of the Market Analysis module: a weekly rollup view,
per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Slice 1.9 already
implemented embedding-based deduplication
(infrastructure/marketanalysis/embedding/EntryEmbeddingService.java) using
pgvector cosine similarity within a rolling 7-day window, to avoid
re-notifying on a story that resurfaces across multiple days. Slice 1.8
implemented the REST layer. This slice adds a weekly "biggest movers"
view that collapses near-duplicate stories from Slice 1.9 into a single
rollup entry per week, rather than showing every day's repeated coverage
separately.

Goal: implement a GET /api/market-digest/weekly-rollup endpoint that
aggregates a calendar week's daily digests, using the existing similarity
logic to collapse near-duplicate stories that appeared across multiple
days into one rollup entry.

Create:
- application/marketanalysis/WeeklyRollupService.java
- web/marketanalysis/WeeklyRollupResponse.java
- application/marketanalysis/WeeklyRollupServiceTest.java

Modify:
- web/marketanalysis/MarketDigestController.java — add
  GET /api/market-digest/weekly-rollup?weekOf=<date> returning entries
  sorted by rank, scoped strictly to digests within that calendar week
  (weekOf identifies any date in the target week; resolve to the
  week's Monday-Sunday or your project's existing week-boundary
  convention — check if one already exists elsewhere in the app).

Implementation notes:
- Reuse the Slice 1.9 similarity threshold/window logic rather than
  reimplementing deduplication logic from scratch. If that logic is
  currently a private method on MarketDigestService or
  EntryEmbeddingService, extract it into a small shared helper class both
  can call, rather than copy-pasting the similarity query.
- "Biggest movers" ranking for the week: when multiple occurrences of a
  near-duplicate story are collapsed into one rollup entry, use the BEST
  (lowest-numbered) MarketImpactRank achieved by any occurrence — not an
  average. Rationale: a story that peaked at rank 1 on day 3 shouldn't be
  diluted by quieter follow-up coverage on days 4-5.
- Fact classification for a collapsed entry: use the most-confirmed state
  seen across all occurrences (CONFIRMED if ANY occurrence was confirmed,
  even if the first occurrence was speculative) — a story that starts
  speculative and gets confirmed later should report as confirmed in the
  rollup.
- Stories that only appeared once during the week pass through to the
  rollup unchanged (a "collapse" of one occurrence is just that occurrence).

Acceptance criteria:
1. A story appearing (as a near-duplicate, per Slice 1.9's similarity
   threshold) on 3 consecutive days within one calendar week collapses
   into exactly one rollup entry.
2. The collapsed entry's rank equals the best (lowest-numbered) rank
   achieved across its occurrences that week.
3. The collapsed entry's fact classification reflects the most-confirmed
   state seen (CONFIRMED if any occurrence was confirmed).
4. GET /api/market-digest/weekly-rollup?weekOf=<date> returns entries
   sorted by rank, and strictly excludes entries from digests outside that
   calendar week (verify with a fixture spanning a week boundary).

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 3.6 — Alpaca news cross-check

```
Implement Slice 3.6 of the Market Analysis module: Alpaca news
cross-check, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 of Market
Analysis is complete — domain/marketanalysis/ has the core model and
ports, application/marketanalysis/MarketDigestService.java runs the daily
research → enrich → classify → filter → save → notify pipeline, and
Slice 1.7 already set up an Alpaca account/API client for market data
(quotes/bars) plus the tracked-ticker list used by Slice 3.2 — find and
reuse that same Alpaca client credentials/config rather than creating a
new connection.

Goal: add a secondary, free news source against Alpaca's News API
(Benzinga-sourced) as a cross-check pass over the tracked-ticker list, to
catch same-day headlines the existing Perplexity Search adapter
(Slice 1.4) might miss, and reduce reliance on LLM search calls for
routine tracked-ticker news.

PROVIDER DECISION (already made): use Alpaca's News API. It is free with
the existing Slice 1.7 Alpaca account (no new vendor, no incremental
cost) — rate limits are 200 calls/minute on the Free market-data plan or
unlimited on the $99/mo Algo Trader Plus tier; confirm which plan this
repo's Alpaca account is on and that a once-daily sweep across the
tracked-ticker list fits within it. The API is itself powered by
Benzinga, the same news vendor already used for Slice 3.2's analyst
ratings. Do not re-litigate this choice.

This is a cross-check, NOT a replacement for the Perplexity Search
adapter — Perplexity's search covers private companies, regulatory
filings, and general web coverage that Alpaca/Benzinga's wire feed does
not. Scope this narrowly to the tracked-ticker list (the same list
Slice 3.2 uses).

Create:
- domain/marketanalysis/port/SecondaryNewsCheckPort.java
- infrastructure/marketanalysis/alpaca/AlpacaNewsAdapter.java — call the
  REST endpoint GET /v1beta1/news, filtered by symbols and a start/end
  date range covering the prior 24-48 hours. Do NOT build against the
  WebSocket stream (wss://stream.data.alpaca.markets/v1beta1/news) — that
  is for real-time push delivery and is out of scope for this daily-batch
  use case.
- infrastructure/marketanalysis/alpaca/AlpacaNewsAdapterTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — add a cross-check
  step after primary research, before classification. Find the existing
  pipeline steps and slot this in as an additional research source
  feeding the same classify/filter/save/notify pipeline, not a separate
  parallel pipeline.

Implementation notes:
- Map returned articles into the existing MarketNewsItem shape via a
  fromAlpacaNews(...) factory method, following the same pattern as
  Slice 3.1's fromFundingRound(...). Default category to OTHER unless
  simple keyword/title heuristics can confidently classify it (e.g.
  "acquisition" → M_AND_A) — do not build a second LLM classification
  pass here; let the existing Slice 1.5 classifier handle categorization
  once the item enters the shared pipeline.
- De-duplicate against same-day items already surfaced by the Slice 1.4
  adapter before persisting — compare by ticker + headline similarity (a
  simple string-similarity check is sufficient; this is a separate,
  earlier dedup pass from the Slice 1.9 embedding-based cross-day dedup,
  which still applies afterward).
- A failed/errored Alpaca News call must NOT fail the whole day's digest
  generation — catch and log, then continue using whatever the primary
  Perplexity Search adapter already found.

Acceptance criteria:
1. A tracked-ticker headline present in Alpaca's News API but missed by
   the Perplexity Search adapter surfaces in the digest via this
   cross-check path.
2. A tracked-ticker headline already found by the Perplexity Search
   adapter is not duplicated when Alpaca's News API also returns it.
3. A simulated Alpaca News API failure degrades gracefully — the day's
   digest still completes using only the primary research source.
4. Items sourced via this adapter flow through the existing
   classify/filter/save/notify pipeline unchanged — no separate
   notification path.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 3.7 — Corporate actions confirmation

```
Implement Slice 3.7 of the Market Analysis module: corporate actions
confirmation, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Phase 1 of Market
Analysis is complete, and Slice 1.7 already set up an Alpaca account/API
client — find and reuse that same Alpaca client credentials/config
rather than creating a new connection. If Slice 3.3 (deal terms
extraction) is already merged, MarketDigestEntry entries with a
DealTerms attached will exist; if 3.3 is not yet merged, this slice can
still persist corporate-actions records standalone, but skip the
digest-entry attachment step until 3.3 lands (check whether DealTerms
exists in the codebase first, and adapt accordingly).

Goal: cross-reference Alpaca's Corporate Actions API (mergers, spinoffs,
splits, dividends) against M_AND_A and MAJOR_PARTNERSHIP digest entries
for tracked tickers, to attach a structured, dated confirmation signal
alongside any Slice 3.3 LLM-extracted deal terms, rather than relying
purely on news-text extraction for whether/when a deal is legally
confirmed.

PROVIDER DECISION (already made): use Alpaca's Corporate Actions API. It
is free with the existing Slice 1.7 Alpaca account (no new vendor, no
incremental cost). Use the CURRENT endpoint: GET /v1/corporate-actions,
filterable by symbols, types (merger, spinoff, forward_split,
reverse_split, cash_dividend, stock_dividend), and a date range with a
selectable date type (declaration_date, ex_date, record_date,
payable_date). Do NOT build against the older GET
v1/corporate_actions/announcements endpoint — it is deprecated. Do not
re-litigate this choice.

Create:
- domain/marketanalysis/CorporateActionConfirmation.java — fields at
  minimum: tickerSymbol, actionType (mirroring Alpaca's ca_types),
  declarationDate, exDate (nullable), recordDate (nullable), payableDate
  (nullable), sourceId (Alpaca's corporate action ID, for traceability).
- domain/marketanalysis/port/CorporateActionPort.java
- infrastructure/marketanalysis/alpaca/AlpacaCorporateActionsAdapter.java
- db/migration/V<next>__create_corporate_action_confirmation.sql — check
  the actual latest migration version number in this repo first.
- infrastructure/marketanalysis/persistence/CorporateActionRepositoryAdapter.java
- infrastructure/marketanalysis/alpaca/AlpacaCorporateActionsAdapterTest.java

Modify:
- application/marketanalysis/MarketDigestService.java — add a
  confirmation-attach step for M_AND_A/MAJOR_PARTNERSHIP entries, after
  the main classify/save step.

Implementation notes:
- The API's date range is capped at 90 days per request — the daily
  sweep only needs a narrow trailing window (e.g. the last 7 days), so
  this cap is not a practical constraint.
- Only attempt to attach a confirmation to an existing digest entry when
  there's a clear match: same ticker, and the corporate action's
  declaration date falls within a few days of the digest entry's date.
  Do NOT guess at a match across unrelated tickers or distant dates —
  leave CorporateActionConfirmation unattached (persisted standalone)
  rather than force a low-confidence link.
- This slice does not replace or override Slice 3.3's LLM-extracted
  DealTerms — it supplements it with a separate, independently-sourced
  confirmation field (e.g. a boolean officiallyConfirmedViaCorporateAction
  plus the linked CorporateActionConfirmation, both nullable) so the
  digest can distinguish "press-reported deal" from "deal with a
  confirmed corporate-actions record."
- A failed/errored Corporate Actions call must NOT fail digest generation
  — catch and log; the entry simply lacks the confirmation attachment
  for that run.

Acceptance criteria:
1. A tracked-ticker M_AND_A entry with a matching Alpaca corporate action
   (same ticker, declaration date within a few days) gets a
   CorporateActionConfirmation attached and
   officiallyConfirmedViaCorporateAction = true.
2. A tracked-ticker M_AND_A entry with no matching corporate action
   record is left unattached rather than force-matched to an unrelated
   action.
3. A corporate action with no matching digest entry (e.g. a routine
   dividend with no news coverage) is still persisted standalone for
   audit purposes.
4. A simulated Corporate Actions API failure degrades gracefully — the
   day's digest still completes without the confirmation attachment.

When done, run the tests and confirm they pass before summarizing.
```

---

## Notes on running these sessions

- **Parallel-safe:** 3.1, 3.2, 3.4, and 3.6 touch disjoint file sets and can run in separate sessions simultaneously once their prerequisites (1.6/1.7/1.8 respectively) are merged.
- **Sequencing decision for 3.3:** it modifies the same `ImpactClassificationResponse` DTO and prompt file that Phase 2 Slices 2.1 and 2.3 also touch. Adopted plan: run **2.1 → 2.3 → 3.3** sequentially, merging each into mainline before starting the next Claude Code session — this is the zero-risk option and avoids babysitting a merge conflict on the shared DTO. The prompt still instructs Claude Code to read the current file state first and add fields alongside whatever's already there, as a safety net, but sequential execution is the primary plan, not a fallback.
- **3.7 should run after 3.3 is merged:** its digest-entry attachment step targets entries carrying Slice 3.3's `DealTerms`. The standalone corporate-actions persistence half of 3.7 can start earlier if you want to get ahead, but hold the attachment wiring until 3.3 lands.
- **3.5 should run last** among Phase 3 slices — it's the one slice that meaningfully benefits from 3.1–3.3, 3.6, and 3.7 already being in the pipeline (more entry types to roll up), though it will still function correctly without them.
- **Before each session:** paste the corresponding prompt as-is into a fresh Claude Code Plan Mode session. Review the plan it proposes against the acceptance criteria before letting it execute.
- **Cost callout:** Slice 3.2 introduces a new $99/mo recurring vendor cost (Benzinga). Slice 3.1 has no new incremental cost (reuses the existing Perplexity Search integration). Slices 3.6 and 3.7 also have no new incremental cost — both ride on the Alpaca account/plan already paid for (or used free) in Slice 1.7.
- **Checklist cross-reference:** the 2.1 → 2.3 → 3.3 sequence still uses the "Market Analysis — Sequential Merge Checklist" document; Slices 3.6 and 3.7 do not touch the shared classifier DTO, so they are not part of that checklist's scope.
