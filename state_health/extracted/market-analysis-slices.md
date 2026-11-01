## Market Analysis — Implementation Slices

Companion to the `Market Analysis` CLAUDE.md section. Each slice below is scoped to be handed to Claude Code independently (e.g. one slice per Plan Mode session). Slices are grouped into the same three phases as the parent spec and are meant to be implemented roughly in order within each phase, though slices within a phase marked "parallelizable" have no dependency on each other.

Each slice lists: **Goal**, **Depends on**, **Files touched**, **Implementation notes**, and **Acceptance criteria**. Paste a slice's content directly as a Claude Code prompt when you're ready to build it.

---

## Phase 1 — Core daily digest loop

### Slice 1.1 — Domain model and ports

**Goal:** Establish the pure-Java domain records and port interfaces for the Market Analysis module, with no I/O, so the ranking/filtering logic can be unit tested before any adapter exists.

**Depends on:** none

**Files touched:**
```
domain/marketanalysis/MarketNewsItem.java
domain/marketanalysis/NewsCategory.java
domain/marketanalysis/FactClassification.java
domain/marketanalysis/ImpactDimension.java
domain/marketanalysis/ImpactAssessment.java
domain/marketanalysis/MarketImpactRank.java
domain/marketanalysis/AffectedCompany.java
domain/marketanalysis/MarketDigestEntry.java
domain/marketanalysis/MarketDigest.java
domain/marketanalysis/port/MarketNewsResearchPort.java
domain/marketanalysis/port/MarketDataPort.java
domain/marketanalysis/port/MarketDigestRepository.java
domain/marketanalysis/port/MarketDigestNotifier.java
domain/marketanalysis/port/ImpactClassifierPort.java
```

**Implementation notes:**
- Use Java records for all value objects; `MarketDigestEntry` and `MarketDigest` are aggregates so consider whether they need a small amount of validation logic in a static factory (e.g. `MarketDigest.empty(date)`).
- `NewsCategory` enum: `EARNINGS, REGULATORY, FUNDING, M_AND_A, MAJOR_PARTNERSHIP, OTHER`.
- Keep ports in a `port` sub-package per your existing hexagonal convention.
- No Spring annotations anywhere in this slice — this package should compile with zero framework dependencies.

**Acceptance criteria:**
- [ ] All domain types compile with no dependency on Spring, Jackson, or any adapter package.
- [ ] Unit test: `MarketDigest.empty(date)` produces a digest with an empty entry list and the given date.
- [ ] Unit test: a hand-constructed `MarketDigestEntry` for each `NewsCategory` value round-trips through equals/hashCode correctly (records give this for free — test is mostly a compile-time sanity check).

---

### Slice 1.2 — Qualifying-bar filter and ranking logic

**Goal:** Implement `MarketDigestService.isMarketMoving()` and the sort-by-rank logic as pure, thoroughly tested business logic, independent of any real adapter (use hand-rolled stub ports).

**Depends on:** Slice 1.1

**Files touched:**
```
application/marketanalysis/MarketDigestService.java   (partial — just isMarketMoving + sorting, rest stubbed/throws UnsupportedOperationException)
application/marketanalysis/MarketDigestServiceTest.java
```

**Implementation notes:**
- This slice exists to lock in the filter semantics before wiring real I/O, since the $50M funding threshold and category list are easy to get subtly wrong later.
- Test boundary values explicitly: a `FUNDING` entry at exactly $50,000,000 (should NOT qualify — "over $50M" is strict), at $50,000,001 (should qualify), and at $49,999,999 (should not).

**Acceptance criteria:**
- [ ] `isMarketMoving()` returns true for `EARNINGS`, `REGULATORY`, `M_AND_A`, `MAJOR_PARTNERSHIP` regardless of deal size.
- [ ] `isMarketMoving()` returns true for `FUNDING` only when `dealSizeUsd() > 50_000_000` (strict inequality).
- [ ] `isMarketMoving()` returns false for `OTHER`.
- [ ] Given an unsorted list of qualifying entries with distinct ranks, the pipeline emits them sorted ascending by `MarketImpactRank` (1 first).

---

### Slice 1.3 — Postgres schema and repository adapter

**Goal:** Create the core Flyway/Liquibase migration for `market_digest`, `market_digest_entry`, `market_digest_impact_assessment`, `market_digest_affected_company`, and implement `MarketDigestRepository` against them.

**Depends on:** Slice 1.1

**Files touched:**
```
db/migration/V<next>__create_market_digest_tables.sql
infrastructure/marketanalysis/persistence/MarketDigestJpaRepository.java
infrastructure/marketanalysis/persistence/MarketDigestEntity.java
infrastructure/marketanalysis/persistence/MarketDigestEntryEntity.java
infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapter.java   (implements domain MarketDigestRepository)
infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapterIT.java
```

**Implementation notes:**
- Use the exact schema from the parent spec's Persistence section (four core tables only — skip the Phase 2/3 tables and `ALTER TABLE` additions for now, they belong to their own slices).
- `embedding VECTOR(1536)` column can be added now (cheap, no logic depends on it yet) or deferred to Slice 1.7 (dedup) — recommend adding the column now since it's a schema-only change, but leave it nullable/unused until 1.7.
- Follow your existing migration-naming convention (check the latest `V*` number already in the project).
- Use Testcontainers (or your existing IT test setup) against real Postgres with pgvector installed, matching how other repository adapters in the app are tested.

**Acceptance criteria:**
- [ ] Migration applies cleanly on top of the current schema with no naming collisions.
- [ ] `save()` persists a `MarketDigest` with N entries and N impact assessments and M affected companies in one transaction.
- [ ] `findByDate()` round-trips a saved digest back into an equivalent domain object.
- [ ] `findByDate()` for a non-existent date returns `Optional.empty()`.
- [ ] Unique constraint on `digest_date` is enforced — saving twice for the same date either upserts or throws, per your chosen conflict policy (recommend upsert to support re-runs).

---

### Slice 1.4 — Perplexity news research adapter

**Goal:** Implement `MarketNewsResearchPort` using the Search API (not Deep Research), reusing the existing Perplexity client/config from the Trends pipeline.

**Depends on:** Slice 1.1

**Files touched:**
```
infrastructure/marketanalysis/perplexity/PerplexityMarketNewsAdapter.java
infrastructure/marketanalysis/perplexity/MarketNewsItemMapper.java
infrastructure/marketanalysis/perplexity/PerplexityMarketNewsAdapterTest.java
```

**Implementation notes:**
- Confirm which existing bean/wrapper class wraps the Perplexity client for the Trends pipeline and reuse it rather than creating a second HTTP client.
- Use the Search API's recency filter (last 24h) rather than a manual date-window prompt instruction — more reliable than asking the model to self-filter by date.
- The mapper is responsible for extracting `NewsCategory`, `sourceUrls`, and any mentioned company names/tickers from the raw search response — this is a good place for a small, separate LLM call if the raw search results don't come back structured, OR defer full structuring to the Claude classifier in Slice 1.5 and have this adapter emit a looser intermediate shape. Recommend the latter to avoid duplicating LLM calls.
- Rate-limit/retry policy should match whatever the Trends pipeline already does.

**Acceptance criteria:**
- [ ] Adapter returns a non-empty list for a known historical date with confirmed AI-healthcare news (use a fixed past date in tests, not "today", for reproducibility).
- [ ] Adapter returns an empty list (not an exception) when the search genuinely finds nothing.
- [ ] Each returned `MarketNewsItem` has at least one non-blank `sourceUrl`.
- [ ] Network/API failures surface as a typed exception the service layer can catch, not a raw HTTP client exception leaking into the domain.

---

### Slice 1.5 — Claude/Bedrock impact classifier adapter

**Goal:** Implement `ImpactClassifierPort` via Spring AI's `ChatClient`, producing fact classification, confidence score, five-dimension impact assessment, and a 1-5 market impact rank in a single structured LLM call per entry (or batched, if the model handles a batch prompt reliably).

**Depends on:** Slice 1.1

**Files touched:**
```
infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapter.java
infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java
infrastructure/marketanalysis/ai/ImpactClassificationResponse.java   (DTO matching the structured JSON schema)
infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapterTest.java
```

**Implementation notes:**
- Use Spring AI's structured output support (`.entity(Type.class)`) rather than hand-parsing JSON strings.
- Include `ConfidenceScore` (0-100) in the same prompt/response schema as fact classification and rank — the parent spec calls this out explicitly since the model is already reasoning about source reliability at that point; don't split it into a second call.
- Pin a specific Bedrock model version in config (not "latest") so classification behavior doesn't silently drift.
- Write the golden-fixture contract test now even though it's listed under Testing in the parent spec — it's cheap here and prevents prompt-schema drift from breaking the pipeline silently later.

**Acceptance criteria:**
- [ ] Given a fixture representing a confirmed SEC-filed earnings beat, adapter returns `FactClassification.CONFIRMED` with a confidence score of 80+.
- [ ] Given a fixture representing an analyst's "why it matters" speculative framing with no primary-source citation, adapter returns `FactClassification.SPECULATIVE`.
- [ ] Every returned entry has exactly 5 `ImpactAssessment` records, one per `ImpactDimension` value, each with a non-blank rationale.
- [ ] Malformed/non-conforming LLM output raises a typed adapter exception rather than propagating a parse exception up through the service layer.

---

### Slice 1.6 — Scheduler, async wiring, and end-to-end pipeline assembly

**Goal:** Wire `MarketDigestService` together with the Slice 1.3/1.4/1.5 adapters, add the `@Scheduled` daily job, and enable async execution so the pipeline doesn't block the scheduler thread pool.

**Depends on:** Slices 1.2, 1.3, 1.4, 1.5

**Files touched:**
```
application/marketanalysis/MarketDigestService.java   (complete — remove Slice 1.2 stubs)
application/marketanalysis/MarketDigestScheduler.java
config/MarketAnalysisConfig.java
application/marketanalysis/MarketDigestServiceIT.java
```

**Implementation notes:**
- Use the exact cron expression and executor config from the parent spec (`0 0 7 * * *`, `America/Chicago`, dedicated `marketAnalysisExecutor` bean with core pool size 2).
- The end-to-end integration test should stub the three adapters (news, market data can be a no-op/empty stub for this slice since Market Data adapter is Slice 1.7... wait, check: Market Data enrichment needs to exist for `enrichWithMarketData` to compile — see note below) — actually, hold off: `MarketDigestService.generateDailyDigest()` calls `enrichWithMarketData`, so this slice technically needs *some* `MarketDataPort` implementation. If Slice 1.7 (market data adapter) isn't done yet, stub it with a no-op adapter that always returns `Optional.empty()` for quotes, and swap in the real adapter in Slice 1.7 without touching this slice's code.
- Confirm this doesn't collide with the Trends pipeline's existing scheduler thread pool — use a distinct executor bean name (`marketAnalysisExecutor`, not the Trends job's executor).

**Acceptance criteria:**
- [ ] Manually invoking `generateDailyDigest()` (bypassing the cron trigger) runs the full research → enrich → classify → filter → save pipeline against real Slice 1.3-1.5 adapters (market data can be the no-op stub) and persists a `MarketDigest`.
- [ ] The scheduled job is registered and its next-fire time is inspectable via Spring's `ScheduledAnnotationBeanPostProcessor` or an actuator endpoint, confirming the cron expression parses correctly.
- [ ] A run with zero qualifying entries persists an empty digest and does not call the notifier port (verify via mock).
- [ ] The pipeline runs on the dedicated async executor, not the calling thread — verify via a `Thread.currentThread().getName()` assertion in the IT test.

---

### Slice 1.7 — Market data adapter (quotes/OHLCV) — parallelizable with 1.4/1.5

**Goal:** Implement `MarketDataPort` against your chosen quote/OHLCV provider, initially scoped to a fixed set of tracked tickers.

**Depends on:** Slice 1.1

**Files touched:**
```
infrastructure/marketanalysis/marketdata/StockQuoteAdapter.java
infrastructure/marketanalysis/marketdata/MarketDataClientConfig.java
infrastructure/marketanalysis/marketdata/StockQuoteAdapterTest.java
```

**Implementation notes:**
- Start with a hardcoded/config-driven tracked-ticker list (DOCS, EVH, ASTH, GSK, and any others you're watching) rather than building general ticker-resolution logic — expand later only if you find yourself needing arbitrary company-name-to-ticker lookups.
- Provider decision resolved: use Alpaca's Market Data API (`https://data.alpaca.markets/v2`), auth via `APCA-API-KEY-ID`/`APCA-API-SECRET-KEY` headers. Free tier, real-time IEX quotes, no daily cap, commercial use permitted at this scale — a good fit for a once-daily job against a handful of tickers. Use the snapshot endpoint for quote+daily-OHLC in one call and the bars endpoint for historical OHLCV. Note: Alpaca does not expose market cap — make that field `Optional` on the `Quote` type rather than blocking on it.

**Acceptance criteria:**
- [ ] `getQuote()` for a tracked ticker returns a populated `Quote` with price and change from Alpaca's snapshot endpoint (market cap may be empty/`Optional` — Alpaca does not provide it).
- [ ] `getQuote()` for an untracked/unknown ticker returns `Optional.empty()`, not an exception.
- [ ] `getPriceHistory()` returns OHLCV rows covering the requested date range for a tracked ticker.
- [ ] Provider outages/timeouts are caught and surfaced as `Optional.empty()` at the port boundary so a market-data hiccup never fails the whole digest run.

---

### Slice 1.8 — SES notifier, REST endpoints, and digest formatter

**Goal:** Implement `MarketDigestNotifier` via the existing SES pipeline, the read-only REST endpoints, and the HTML formatter for the email/API response shape.

**Depends on:** Slice 1.6

**Files touched:**
```
infrastructure/marketanalysis/notification/SesMarketDigestNotifier.java
infrastructure/marketanalysis/notification/MarketDigestFormatter.java
web/marketanalysis/MarketDigestController.java
web/marketanalysis/MarketDigestResponse.java
web/marketanalysis/MarketDigestSummary.java
infrastructure/marketanalysis/notification/MarketDigestFormatterTest.java   (snapshot test)
web/marketanalysis/MarketDigestControllerIT.java
```

**Implementation notes:**
- Reuse the existing `SesEmailSender` wrapper and subscriber-list query pattern from wherever else the app sends transactional/digest email.
- `MarketDigestResponse` fields: ranked entries, each with confirmed/speculative badge, five impact-dimension scores, and inline source links (confidence score, deal-terms, guidance-delta, and analyst-rating fields can be null/omitted until their respective Phase 2/3 slices land — design the DTO to tolerate that now so you don't have to break the contract later).
- Snapshot-test the formatter output specifically to catch future email template regressions, per the parent spec's testing notes.

**Acceptance criteria:**
- [ ] `GET /api/market-digest/{date}` returns 200 with a populated response for an existing digest, 404 for a date with no digest.
- [ ] `GET /api/market-digest/latest` returns the most recently generated digest regardless of whether it had qualifying entries.
- [ ] `GET /api/market-digest?from=&to=` returns a paginated list of summaries within the range, sorted by date descending.
- [ ] `POST /api/market-digest/subscribe` adds the current user to the active subscriber list (idempotent on repeat calls).
- [ ] Notifier only fires when `MarketDigest` has at least one qualifying entry (verify via the Slice 1.6 mock-based test still passes with the real adapter wired in).
- [ ] Formatter snapshot test fails loudly if the HTML structure changes unexpectedly.

---

### Slice 1.9 — Deduplication via pgvector embedding similarity — parallelizable, can land any time after 1.3

**Goal:** Prevent re-notifying on a story that resurfaces across multiple days (e.g. ongoing earnings-reaction coverage) by comparing each new entry's embedding against recent entries via cosine similarity.

**Depends on:** Slice 1.3

**Files touched:**
```
infrastructure/marketanalysis/embedding/EntryEmbeddingService.java
infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapter.java   (add similarity query method)
application/marketanalysis/MarketDigestService.java   (add dedup filter step before notify)
infrastructure/marketanalysis/embedding/EntryEmbeddingServiceTest.java
```

**Implementation notes:**
- Embed on `headline + summary` concatenation using whichever embedding model/provider the Trends pipeline already uses for pgvector (reuse, don't introduce a second embedding provider).
- Use a cosine-similarity threshold (start around 0.92-0.95, tune empirically) and a rolling 7-day window per the parent spec.
- Dedup should suppress re-notification but should NOT suppress persistence — always save the entry, just skip triggering `MarketDigestNotifier` for it if a near-duplicate was already notified within the window.

**Acceptance criteria:**
- [ ] Two near-identical entries (same earnings event, reworded headline) generated on consecutive days are both persisted, but only the first triggers a notification.
- [ ] Two genuinely distinct entries with superficially similar wording (e.g. two different companies' earnings on the same day) are both notified.
- [ ] The similarity threshold is a named config property, not a magic number buried in code.

---

## Phase 2 — Guidance tracking, peer grouping, regulatory lifecycle

### Slice 2.1 — Guidance history tracking and beat/raise quantification

**Goal:** Persist each company's guidance over time and use it to precisely quantify a "beat/raise" ("$X-Y raised to $A-B") instead of restating the news qualitatively.

**Depends on:** Slice 1.6 (needs the working pipeline to plug enrichment into)

**Files touched:**
```
domain/marketanalysis/GuidanceComparison.java
domain/marketanalysis/port/GuidancePort.java
db/migration/V<next>__create_guidance_history.sql
infrastructure/marketanalysis/persistence/GuidancePortAdapter.java
application/marketanalysis/MarketDigestService.java   (add enrichWithGuidanceAndRatings — guidance half only for this slice)
application/marketanalysis/GuidancePortAdapterTest.java
```

**Implementation notes:**
- `guidance_history` is a standing reference table, not digest-scoped — a later digest entry looks up "what did this company guide to last time" independent of which digest first recorded it.
- On each `EARNINGS` entry, extract the new guidance range from the classifier's structured output (may require extending the Slice 1.5 classifier prompt/schema to also emit a proposed `GuidanceComparison` — coordinate with whoever touches that adapter next) and call `recordGuidance()` after computing the delta against `getPriorGuidance()`.
- Handle the first-ever guidance for a ticker/metric pair gracefully — `getPriorGuidance()` returns empty, so the comparison should render as "initial guidance" rather than a delta.

**Acceptance criteria:**
- [ ] First guidance ever recorded for a ticker/metric produces a `GuidanceComparison` with no prior range (initial-guidance case handled, not thrown as an error).
- [ ] Second guidance for the same ticker/metric correctly computes low/high deltas against the immediately prior record (not the oldest historical record).
- [ ] `guidance_history` unique constraint on `(ticker_symbol, metric, given_at)` prevents duplicate rows from a re-run of the same day's digest.
- [ ] `MarketDigestEntry` for an earnings item now includes the guidance delta in its enriched form, verified end-to-end against a fixture.

---

### Slice 2.2 — Peer group tagging and company database cross-reference

**Goal:** Tag each `AffectedCompany` with a `PeerGroup`, and cross-reference the peer group against the existing AI-healthcare company database (populated via the Agent API / wide-research pipeline) so private peers can be named in a digest entry even without market data.

**Depends on:** Slice 1.6

**Files touched:**
```
domain/marketanalysis/PeerGroup.java
domain/marketanalysis/AffectedCompany.java   (add peerGroup field)
infrastructure/marketanalysis/persistence/MarketDigestEntity.java   (add peer_group column)
db/migration/V<next>__add_peer_group_to_affected_company.sql
infrastructure/marketanalysis/companydb/PeerGroupResolver.java   (queries existing company DB)
infrastructure/marketanalysis/companydb/PeerGroupResolverTest.java
```

**Implementation notes:**
- `PeerGroupResolver` reads from your existing company database/table (the one populated via Agent API structured extraction + wide-research, per your Trends pipeline notes) rather than introducing a second company registry.
- Resolution can be a simple lookup-by-name-or-ticker against the existing table if that table already has a sector/category field; otherwise this slice needs to add a `peer_group` column to the existing company table too — check that table's current schema before writing the migration here to avoid a redundant column.
- The classifier (Slice 1.5) is a reasonable place to *propose* a `PeerGroup` for a company it hasn't seen before, with `PeerGroupResolver` as the source of truth for known companies overriding any classifier guess.

**Acceptance criteria:**
- [ ] A known public company (already in the company DB with an assigned peer group) resolves to the correct `PeerGroup` without an LLM call.
- [ ] An unrecognized company falls back to the classifier's proposed peer group and is flagged for manual review (e.g. a `needsReview` boolean) rather than silently trusted.
- [ ] Given an earnings entry for a company in `AI_SCRIBE_DOCUMENTATION`, a query for "other companies in this peer group" returns known private peers from the company DB.
- [ ] Existing digest entries created before this slice landed do not break when read back (peer_group nullable, backward compatible).

---

### Slice 2.3 — Regulatory rulemaking lifecycle tracker and deadline sweep

**Goal:** Track a regulatory item (FDA discussion paper, EU AI Act deferral, etc.) across its lifecycle stage and proactively surface it again when a comment deadline is within 7 days, even on a day with no fresh news.

**Depends on:** Slice 1.6

**Files touched:**
```
domain/marketanalysis/RulemakingStage.java
domain/marketanalysis/Jurisdiction.java
domain/marketanalysis/RegulatoryTracker.java
domain/marketanalysis/port/RegulatoryTrackerRepository.java
db/migration/V<next>__create_regulatory_tracker.sql
infrastructure/marketanalysis/persistence/RegulatoryTrackerRepositoryAdapter.java
application/marketanalysis/MarketDigestService.java   (add step 1b: approaching-deadline sweep, per parent spec)
infrastructure/marketanalysis/notification/MarketDigestFormatter.java   (render deadline callouts)
application/marketanalysis/RegulatoryDeadlineSweepTest.java
```

**Implementation notes:**
- `regulatory_tracker` is upserted keyed on `(jurisdiction, docket_id)` — a new digest entry about an existing docket updates its `stage`/`last_updated_at` rather than creating a duplicate tracker row.
- The classifier (Slice 1.5) needs to additionally extract jurisdiction + docket ID + stage + comment deadline for `REGULATORY` category entries — extend its structured-output schema; entries with no discernible docket ID (e.g. a vague policy statement) should upsert with a null `docket_id`, which means the unique constraint needs a partial-unique-index approach (`docket_id IS NOT NULL`) rather than a plain composite unique constraint — handle this in the migration.
- `findApproachingDeadlines(Duration.ofDays(7))` should be idempotent-safe against repeated daily calls — once a deadline has been surfaced, don't re-surface it every single day for the full 7-day window unless you want that (recommend surfacing once at T-7 and once at T-1, not daily; make this configurable).

**Acceptance criteria:**
- [ ] A new `REGULATORY` entry for an unseen docket creates a `regulatory_tracker` row at `DISCUSSION_PAPER` stage.
- [ ] A later entry referencing the same docket ID updates the existing row's stage (e.g. to `COMMENT_PERIOD`) rather than duplicating it.
- [ ] `findApproachingDeadlines(Duration.ofDays(7))` returns a tracker whose `comment_deadline` is 6 days away and excludes one that's 10 days away.
- [ ] A day with zero fresh news items but one approaching deadline still triggers a notification containing the deadline callout.
- [ ] Entries with no docket ID (null) never collide with each other under the unique constraint.

---

## Phase 3 — Private funding, analyst ratings, deal terms, watchlists, rollups

### Slice 3.1 — Private funding round tracking — parallelizable

**Goal:** Surface funding rounds for private AI-healthcare companies (no ticker) via a dedicated data source, applying the same >$50M qualifying threshold as public funding news.

**Depends on:** Slice 1.6

**Files touched:**
```
domain/marketanalysis/PrivateFundingRound.java
domain/marketanalysis/port/PrivateFundingPort.java
infrastructure/marketanalysis/funding/PerplexityFundingAdapter.java   (Perplexity Search API-based provider)
db/migration/V<next>__create_private_funding_round.sql
infrastructure/marketanalysis/persistence/PrivateFundingRepositoryAdapter.java
application/marketanalysis/MarketDigestService.java   (wire step 1b funding sweep, per parent spec)
infrastructure/marketanalysis/funding/PerplexityFundingAdapterTest.java
```

**Implementation notes:**
- Provider decision resolved: use the Perplexity Search API (reusing the same client/wrapper as the Slice 1.4 news adapter) rather than a dedicated funding database. Crunchbase's actual API access sits behind a custom-quoted Enterprise tier (typically $5,000+/yr — the advertised $49/mo Pro plan is web/UI access only, not API access), and PitchBook starts around $20,000+/yr per seat — both are out of scope for this project's budget. Since the qualifying bar is funding rounds strictly over $50M, these are infrequent, high-signal events that receive real press coverage, making LLM extraction from news reasonably reliable.
- Search prompt should target: "private funding rounds over $50M announced in AI healthcare in the last 24-48 hours, with investor names, round size, and round stage, citing sources." Reuse the recency-filtered Search API pattern from Slice 1.4.
- The mapper should be conservative: if the source article gives a range or doesn't clearly state the round size, do not guess a number — treat it as `dealSizeUsd = null` and exclude from the qualifying filter (which requires a known amount over $50M) rather than risk a false positive/negative on the threshold.
- `MarketNewsItem::fromFundingRound` (referenced in the parent spec's service code) needs a real implementation mapping a `PrivateFundingRound` into the shared `MarketNewsItem` shape so it flows through the same classify/filter/notify pipeline as searched news.
- Tag with `PeerGroup` at ingestion if Slice 2.2 has landed; otherwise leave null and backfill later.
- Revisit this decision later if search-based extraction proves unreliable (missed rounds, wrong amounts) — Crunchbase Enterprise remains the fallback if a budget line opens up for it.

**Acceptance criteria:**
- [ ] A round above $50M for an AI-healthcare company appears in the digest with `NewsCategory.FUNDING` and passes the qualifying filter.
- [ ] A round at or below $50M is persisted (for audit) but does not trigger notification, matching the public-funding threshold semantics from Slice 1.2.
- [ ] Provider outage degrades gracefully — a failed funding lookup does not fail the whole day's digest generation.

---

### Slice 3.2 — Analyst rating and price-target change tracking — parallelizable

**Goal:** Surface sell-side rating/price-target changes for tracked tickers, on a rolling window since these often lag the earnings reaction by 1-3 days.

**Depends on:** Slice 1.6, Slice 1.7 (needs tracked-ticker list)

**Files touched:**
```
domain/marketanalysis/AnalystRatingChange.java
domain/marketanalysis/port/AnalystRatingPort.java
infrastructure/marketanalysis/ratings/BenzingaAnalystRatingAdapter.java
infrastructure/marketanalysis/ratings/BenzingaRatingsClientConfig.java
db/migration/V<next>__create_analyst_rating_change.sql
infrastructure/marketanalysis/persistence/AnalystRatingRepositoryAdapter.java
application/marketanalysis/MarketDigestService.java   (complete enrichWithGuidanceAndRatings — ratings half)
infrastructure/marketanalysis/ratings/BenzingaAnalystRatingAdapterTest.java
```

**Implementation notes:**
- Provider decision resolved: use Benzinga's Analyst Ratings API ($99/mo, via the Massive/Polygon partner catalog or Benzinga's direct API). It returns per-analyst action (upgrade/downgrade/initiate/maintain/reiterate) with `rating_current`/`rating_prior` and `pt_current`/`pt_prior` fields, which map directly onto `AnalystRatingChange` without needing to build day-over-day diffing logic yourself. Endpoint: `GET /api/v2.1/calendar/ratings`, filterable by ticker and date range. Confirm the "individual use" licensing language on the partner-catalog listing actually covers this commercial newsletter product before going live — the direct Benzinga enterprise API may have different terms than the Massive-hosted listing.
- Query on a rolling 3-day window (not same-day only) per the parent spec's port documentation — a rating change from 2 days ago that's still relevant to today's earnings-reaction entry should still surface.
- Attach rating changes to the *originating* earnings entry where possible (link by ticker + date proximity) rather than creating orphan digest entries for rating changes alone, unless a rating change happens independent of any tracked news item — in that case it may warrant its own lightweight entry (design decision: recommend only surfacing standalone rating-change entries if the rating change itself crosses a materiality bar, e.g. a 2+ notch downgrade or >20% price-target cut).

**Acceptance criteria:**
- [ ] A rating change dated within 3 days of an earnings entry for the same ticker is attached to that entry's enrichment.
- [ ] A rating change with no associated recent news item, but crossing the materiality bar, produces its own standalone digest entry.
- [ ] A minor rating change (e.g. one-notch, small PT tweak) below the materiality bar is persisted but does not independently trigger notification.

---

### Slice 3.3 — Deal terms structuring — parallelizable

**Goal:** Normalize M&A/partnership announcements into structured `DealTerms` (upfront cash, milestones, equity stake, royalty %, disclosure completeness) instead of leaving deal economics as unstructured prose.

**Depends on:** Slice 1.5 (classifier)

**Files touched:**
```
domain/marketanalysis/DealTerms.java
infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java   (extend schema for M_AND_A / MAJOR_PARTNERSHIP entries)
infrastructure/marketanalysis/ai/ImpactClassificationResponse.java   (add optional dealTerms field)
db/migration/V<next>__add_deal_terms_to_market_digest_entry.sql
infrastructure/marketanalysis/ai/DealTermsExtractionTest.java
```

**Implementation notes:**
- Extend the existing Slice 1.5 classifier prompt rather than adding a second LLM call — the model is already reading the full news item text and can extract deal terms in the same pass for `M_AND_A`/`MAJOR_PARTNERSHIP` categories.
- Explicitly instruct the model to set `disclosedPortion = UNDISCLOSED` (and leave numeric fields null) rather than guessing/hallucinating a number when the source article doesn't state deal economics — this is important given the parent spec's example (the GSK-Relation deal) where the guaranteed-upfront vs. milestone split wasn't disclosed.

**Acceptance criteria:**
- [ ] A fixture representing a fully-disclosed deal (explicit upfront + milestone numbers in the source text) extracts matching numeric values.
- [ ] A fixture representing an "up to $110M" style headline with no further breakdown extracts `disclosedPortion = PARTIAL` and leaves the milestone/upfront split null rather than guessing a 50/50 split or similar.
- [ ] A fixture with zero dollar figures at all extracts `disclosedPortion = UNDISCLOSED` with all numeric fields null.

---

### Slice 3.4 — Ticker watchlists

**Goal:** Let subscribers configure a personal ticker watchlist; digest responses flag/sort watched-ticker entries to the top independent of overall rank.

**Depends on:** Slice 1.8 (REST layer)

**Files touched:**
```
domain/marketanalysis/port/TickerWatchlistRepository.java
db/migration/V<next>__create_ticker_watchlist.sql
infrastructure/marketanalysis/persistence/TickerWatchlistRepositoryAdapter.java
web/marketanalysis/MarketDigestController.java   (add GET/PUT /watchlist endpoints)
web/marketanalysis/TickerWatchlistRequest.java
web/marketanalysis/MarketDigestResponse.java   (add isWatched flag per entry)
web/marketanalysis/MarketDigestControllerIT.java
```

**Implementation notes:**
- `PUT /watchlist` replaces the full list (simplest semantics — client sends the complete desired set) rather than supporting incremental add/remove; add incremental endpoints later only if the full-replace UX proves clunky.
- Sorting: partition qualifying entries into "watched" and "unwatched" groups, sort each internally by rank, then concatenate watched-first — don't just bump a rank number, since that would corrupt the underlying rank field for anyone else viewing the same digest without a watchlist.

**Acceptance criteria:**
- [ ] `PUT /watchlist` with `["DOCS","GSK"]` followed by `GET /watchlist` returns exactly those two tickers.
- [ ] `PUT /watchlist` with an empty array clears the watchlist (not a no-op).
- [ ] For a subscriber with a non-empty watchlist, `GET /market-digest/latest` returns watched-ticker entries first, each internally still sorted by rank, followed by all other entries sorted by rank.
- [ ] For a subscriber with no watchlist, response ordering is unchanged from pre-Slice-3.4 behavior (pure rank sort).

---

### Slice 3.5 — Weekly rollup view

**Goal:** Aggregate a week's daily digests into a "biggest movers" rollup, collapsing near-duplicate stories (via the Slice 1.9 embedding similarity) that resurfaced across multiple days into a single rollup entry.

**Depends on:** Slice 1.9 (embedding dedup), Slice 1.8 (REST layer)

**Files touched:**
```
application/marketanalysis/WeeklyRollupService.java
web/marketanalysis/MarketDigestController.java   (add GET /weekly-rollup)
web/marketanalysis/WeeklyRollupResponse.java
application/marketanalysis/WeeklyRollupServiceTest.java
```

**Implementation notes:**
- Reuse the Slice 1.9 similarity threshold/window logic rather than reimplementing dedup — extract it into a shared helper if it's currently private to `MarketDigestService`.
- "Biggest movers" ranking for the week can simply take the best (lowest-numbered) `MarketImpactRank` achieved by any occurrence of a collapsed story, or average across occurrences — recommend best-rank, since a story that peaked at rank 1 on day 3 shouldn't be diluted by quieter follow-up coverage on days 4-5.
- Confidence score for a collapsed rollup entry: use the highest confidence score seen across occurrences (a story that starts speculative and gets confirmed later should report as confirmed).

**Acceptance criteria:**
- [ ] A story appearing (near-duplicate) on 3 consecutive days within one week collapses into exactly one rollup entry.
- [ ] The collapsed entry's rank equals the best (lowest-numbered) rank achieved across its occurrences.
- [ ] The collapsed entry's fact classification reflects the most-confirmed state seen (CONFIRMED if any occurrence was confirmed, even if the first occurrence was speculative).
- [ ] `GET /weekly-rollup?weekOf=<date>` returns entries sorted by rank, scoped strictly to digests within that calendar week.

---

### Slice 3.6 — Alpaca news cross-check — parallelizable

**Goal:** Add a secondary, free news source against Alpaca's News API (Benzinga-sourced, already included with the Slice 1.7 Alpaca account) as a cross-check pass over the tracked-ticker list, to catch same-day headlines the Perplexity Search adapter's queries might miss and reduce reliance on LLM search calls for routine tracked-ticker news.

**Depends on:** Slice 1.7 (Alpaca account/auth already wired), Slice 1.6 (pipeline to plug into)

**Files touched:**
```
domain/marketanalysis/port/SecondaryNewsCheckPort.java
infrastructure/marketanalysis/alpaca/AlpacaNewsAdapter.java
infrastructure/marketanalysis/alpaca/AlpacaNewsAdapterTest.java
application/marketanalysis/MarketDigestService.java   (add a cross-check step after primary research, before classification)
```

**Implementation notes:**
- Alpaca's News API is powered by Benzinga and is free with the existing Slice 1.7 account (no new provider decision, no new cost) — confirm your Alpaca plan's rate limit (200 calls/min on Free, unlimited on the $99/mo Algo Trader Plus tier) is sufficient for a once-daily sweep across the tracked-ticker list.
- Use the REST endpoint (`GET /v1beta1/news`, filtered by `symbols` and a `start`/`end` date range covering the prior 24-48 hours) for the daily batch pass — the WebSocket stream (`wss://stream.data.alpaca.markets/v1beta1/news`) is for real-time push delivery and is not needed for this daily-batch use case; only revisit it if you later want intraday alerting.
- This is a cross-check, not a replacement for Slice 1.4's Perplexity Search adapter: Perplexity's search covers private companies, regulatory filings, and general web coverage that Alpaca/Benzinga's wire feed does not. Scope Slice 3.6 narrowly to the tracked-ticker list (the same list Slice 3.2 uses) where Alpaca's symbol-tagged news is a good fit.
- Map returned articles into the existing `MarketNewsItem` shape via a `fromAlpacaNews(...)` factory method, same pattern as Slice 3.1's `fromFundingRound(...)`. Category should default to `OTHER` unless simple keyword/title heuristics can confidently classify it (e.g. "acquisition" → `M_AND_A`) — do not build a second LLM classification pass here; let the existing Slice 1.5 classifier handle categorization once the item enters the shared pipeline.
- De-duplicate against same-day items already surfaced by the Slice 1.4 adapter before persisting — compare by ticker + headline similarity (a simple string-similarity check is sufficient here; this is not the same as the Slice 1.9 embedding-based cross-day dedup, which still applies afterward as a second pass).
- A failed/errored Alpaca News call must not fail the whole day's digest generation — catch and log, then continue using whatever the primary Perplexity Search adapter already found.

**Acceptance criteria:**
- [ ] A tracked-ticker headline present in Alpaca's News API but missed by the Perplexity Search adapter's queries surfaces in the digest via this cross-check path.
- [ ] A tracked-ticker headline already found by the Perplexity Search adapter is not duplicated when Alpaca's News API also returns it.
- [ ] A simulated Alpaca News API failure degrades gracefully — the day's digest still completes using only the primary research source.
- [ ] Items sourced via this adapter flow through the existing classify/filter/save/notify pipeline unchanged — no separate notification path.

---

### Slice 3.7 — Corporate actions confirmation — parallelizable

**Goal:** Cross-reference Alpaca's Corporate Actions API (mergers, spinoffs, splits, dividends — free with the existing Slice 1.7 account) against `M_AND_A` and `MAJOR_PARTNERSHIP` digest entries for tracked tickers, to attach a structured, dated confirmation signal alongside the Slice 3.3 LLM-extracted deal terms, rather than relying purely on news-text extraction for whether/when a deal is legally confirmed.

**Depends on:** Slice 1.7 (Alpaca account/auth), Slice 3.3 (deal terms — this slice attaches to entries already carrying `DealTerms`; if 3.3 is not yet merged, this slice can still persist corporate-actions records standalone, but the digest-entry attachment step should wait for 3.3)

**Files touched:**
```
domain/marketanalysis/CorporateActionConfirmation.java
domain/marketanalysis/port/CorporateActionPort.java
infrastructure/marketanalysis/alpaca/AlpacaCorporateActionsAdapter.java
db/migration/V<next>__create_corporate_action_confirmation.sql
infrastructure/marketanalysis/persistence/CorporateActionRepositoryAdapter.java
application/marketanalysis/MarketDigestService.java   (add confirmation-attach step for M_AND_A/MAJOR_PARTNERSHIP entries)
infrastructure/marketanalysis/alpaca/AlpacaCorporateActionsAdapterTest.java
```

**Implementation notes:**
- Use the current (non-deprecated) endpoint: `GET /v1/corporate-actions`, filterable by `symbols`, `types` (e.g. `merger`, `spinoff`, `forward_split`, `reverse_split`, `cash_dividend`, `stock_dividend`), and a date range with a selectable date type (`declaration_date`, `ex_date`, `record_date`, `payable_date`). The older `GET v1/corporate_actions/announcements` endpoint is deprecated — do not build against it.
- The API's date range is capped at 90 days per request — the daily sweep only needs a narrow trailing window (e.g. the last 7 days), so this cap is not a practical constraint, but note it if you ever build a backfill job.
- `CorporateActionConfirmation` fields should include at minimum: tickerSymbol, actionType (mirroring Alpaca's `ca_types`), declarationDate, exDate (nullable), recordDate (nullable), payableDate (nullable), sourceId (Alpaca's corporate action ID, for traceability).
- Only attempt to attach a confirmation to an existing digest entry when there's a clear match: same ticker, and the corporate action's declaration date falls within a few days of the digest entry's date. Do not guess at a match across unrelated tickers or distant dates — leave `CorporateActionConfirmation` unattached (persisted standalone) rather than force a low-confidence link.
- This slice does not replace or override the Slice 3.3 LLM-extracted `DealTerms` — it supplements it with a separate, independently-sourced confirmation field (e.g. a boolean `officiallyConfirmedViaCorporateAction` plus the linked `CorporateActionConfirmation`, both nullable) so the digest can distinguish "press-reported deal" from "deal with a confirmed corporate-actions record."
- A failed/errored Corporate Actions call must not fail digest generation — catch and log; the entry simply lacks the confirmation attachment for that run (a later day's sweep may still pick it up once Alpaca's data vendor ingests it).

**Acceptance criteria:**
- [ ] A tracked-ticker `M_AND_A` entry with a matching Alpaca corporate action (same ticker, declaration date within a few days) gets a `CorporateActionConfirmation` attached and `officiallyConfirmedViaCorporateAction = true`.
- [ ] A tracked-ticker `M_AND_A` entry with no matching corporate action record is left unattached (`officiallyConfirmedViaCorporateAction = false` or null) rather than force-matched to an unrelated action.
- [ ] A corporate action with no matching digest entry (e.g. a routine dividend with no news coverage) is still persisted standalone for audit purposes, without needing a digest entry to attach to.
- [ ] A simulated Corporate Actions API failure degrades gracefully — the day's digest still completes without the confirmation attachment.

---

## Cross-slice notes for Claude Code sessions

- **Coordinate classifier schema changes — run sequentially, not in parallel:** Slices 2.1 (guidance extraction), 2.3 (regulatory metadata extraction), and 3.3 (deal terms extraction) all extend the same Slice 1.5 classifier prompt/response schema (`ImpactClassificationResponse` / `ImpactClassificationPrompts`). Decision: run these one at a time, in the order **2.1 → 2.3 → 3.3**, merging each into the branch/mainline before starting the next Claude Code session. This is the zero-risk option — it avoids any merge-conflict babysitting on the shared DTO. (Order among the three doesn't matter for correctness, since each adds independent optional fields; 2.1→2.3→3.3 is just the sequence used when generating their prompts.) Only fall back to running two of them in parallel with a manual diff review if you're intentionally trading speed for that risk.
- **Migration numbering:** Every slice with a `db/migration/V<next>__...sql` file needs to check the actual latest version number in the repo at implementation time — the placeholder `<next>` is intentional and must not be taken as literal filenames.
- **Backward compatibility:** Slices 2.x and 3.x all add nullable columns/fields to entities created in Phase 1 — confirm each new column is nullable (or has a sensible default) so existing Phase 1 digests continue to deserialize correctly after each Phase 2/3 slice lands.
- **Provider decisions resolved:** Slice 3.1 uses the Perplexity Search API (no new vendor, no incremental cost) rather than Crunchbase/PitchBook. Slice 3.2 uses Benzinga's Analyst Ratings API ($99/mo) rather than Finnhub, chosen for its per-analyst upgrade/downgrade/price-target-change data versus Finnhub's consensus-only shape. Slices 3.6 and 3.7 use Alpaca's News API and Corporate Actions API respectively — both free with the existing Slice 1.7 Alpaca account, no new vendor, no incremental cost (Alpaca's News API is itself Benzinga-sourced, the same vendor as Slice 3.2). No further vendor decisions block Phase 3.
