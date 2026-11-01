# Market Analysis — Phase 1 Claude Code Prompts

Nine ready-to-paste prompts, one per Phase 1 slice. Each is self-contained — it restates the architectural context (hexagonal architecture, Spring Boot, Spring AI, PostgreSQL/pgvector) so you can paste it into a fresh Claude Code session (Plan Mode recommended) without needing to re-explain the project.

**Suggested session order:** 1.1 → 1.2 → 1.3 → (1.4, 1.5, 1.7 in parallel sessions) → 1.6 → 1.8 → 1.9.

Before running any prompt, make sure `CLAUDE.md` in the target repo already includes the "Market Analysis" spec section (domain model, ports, schema) — these prompts reference it as shared context.

None of the Phase 1 slices touch the shared classifier response DTO, so there's no merge-conflict risk within this phase. That risk starts in Phase 2 (Slices 2.1 and 2.3) and continues into Phase 3 (Slice 3.3), all three of which extend the same `ImpactClassificationResponse` DTO first introduced here in Slice 1.5. When you get to that stage, see the "Market Analysis — Sequential Merge Checklist" document for the concrete before/during/after steps to run those three slices safely in order (2.1 → 2.3 → 3.3).

---

## Prompt 1.1 — Domain model and ports

```
Implement Slice 1.1 of the Market Analysis module: the domain model and port
interfaces, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses hexagonal architecture. Domain types live under
domain/marketanalysis/ and must have zero framework dependencies (no Spring,
no Jackson annotations, no JPA). Ports are plain interfaces in
domain/marketanalysis/port/ that adapters will implement in later slices —
do not implement any adapter in this session.

Goal: establish the pure-Java domain records and port interfaces for the
Market Analysis module, with no I/O, so the ranking/filtering logic can be
unit tested before any adapter exists.

Create these files:
- domain/marketanalysis/MarketNewsItem.java
- domain/marketanalysis/NewsCategory.java
- domain/marketanalysis/FactClassification.java
- domain/marketanalysis/ImpactDimension.java
- domain/marketanalysis/ImpactAssessment.java
- domain/marketanalysis/MarketImpactRank.java
- domain/marketanalysis/AffectedCompany.java
- domain/marketanalysis/MarketDigestEntry.java
- domain/marketanalysis/MarketDigest.java
- domain/marketanalysis/port/MarketNewsResearchPort.java
- domain/marketanalysis/port/MarketDataPort.java
- domain/marketanalysis/port/MarketDigestRepository.java
- domain/marketanalysis/port/MarketDigestNotifier.java
- domain/marketanalysis/port/ImpactClassifierPort.java

Implementation notes:
- Use Java records for all value objects. MarketDigestEntry and MarketDigest
  are aggregates — add a small static factory where useful, e.g.
  MarketDigest.empty(date) for the no-qualifying-news case.
- NewsCategory enum values: EARNINGS, REGULATORY, FUNDING, M_AND_A,
  MAJOR_PARTNERSHIP, OTHER.
- FactClassification enum values: CONFIRMED, SPECULATIVE.
- ImpactDimension enum values: REVENUE, EARNINGS, VALUATION,
  INVESTOR_SENTIMENT, FUTURE_GROWTH.
- MarketImpactRank should constrain to the range 1 (highest impact) to 5
  (lowest) — use a small value type or validated int, your call.
- Look at how existing domain packages in this project are structured
  (e.g. the Trends pipeline's domain package) and match the existing
  conventions for package layout and record style.

Acceptance criteria (write tests to verify each):
1. All domain types compile with zero dependency on Spring, Jackson, or any
   adapter package — verify by checking imports, not just by compiling.
2. MarketDigest.empty(date) produces a digest with an empty entry list and
   the given date.
3. A hand-constructed MarketDigestEntry for each NewsCategory value
   round-trips through equals/hashCode correctly.

When done, summarize which files were created and confirm the acceptance
criteria pass.
```

---

## Prompt 1.2 — Qualifying-bar filter and ranking logic

```
Implement Slice 1.2 of the Market Analysis module: the qualifying-bar filter
and ranking logic, per the "Market Analysis" section of CLAUDE.md.

Context: Slice 1.1 already created the domain model and ports under
domain/marketanalysis/ (MarketNewsItem, NewsCategory, MarketDigestEntry,
MarketDigest, MarketImpactRank, and the port interfaces). This slice adds
the beginning of the application-layer service that will eventually
orchestrate the full digest pipeline, but for now ONLY implement the
filtering and sorting logic — stub every other method to throw
UnsupportedOperationException with a comment noting which future slice
completes it.

Goal: implement MarketDigestService.isMarketMoving() and the sort-by-rank
step as pure, thoroughly tested business logic, using hand-rolled stub/mock
ports (do not wire real adapters — none exist yet).

Create/modify:
- application/marketanalysis/MarketDigestService.java (partial)
- application/marketanalysis/MarketDigestServiceTest.java

The qualifying bar (isMarketMoving) must return true when an entry is:
- category == EARNINGS, OR
- category == REGULATORY, OR
- category == FUNDING AND dealSizeUsd() > 50_000_000 (strict inequality —
  exactly $50,000,000 does NOT qualify), OR
- category == M_AND_A, OR
- category == MAJOR_PARTNERSHIP

Everything else (OTHER, or FUNDING at/below the threshold) does not qualify.

Test boundary values explicitly:
- FUNDING at exactly $50,000,000 → does NOT qualify
- FUNDING at $50,000,001 → qualifies
- FUNDING at $49,999,999 → does NOT qualify

Also test that given an unsorted list of qualifying entries with distinct
MarketImpactRank values, the pipeline emits them sorted ascending by rank
(rank 1 first).

Acceptance criteria:
1. isMarketMoving() returns true for EARNINGS, REGULATORY, M_AND_A,
   MAJOR_PARTNERSHIP regardless of deal size.
2. isMarketMoving() returns true for FUNDING only when dealSizeUsd() is
   strictly greater than 50,000,000.
3. isMarketMoving() returns false for OTHER.
4. Sorting test passes as described above.

When done, run the tests and confirm all pass before summarizing.
```

---

## Prompt 1.3 — Postgres schema and repository adapter

```
Implement Slice 1.3 of the Market Analysis module: the core Postgres schema
and repository adapter, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses PostgreSQL with the pgvector extension, and
[Flyway/Liquibase — confirm which one this project actually uses before
writing the migration file naming]. Domain types from Slice 1.1 already
exist under domain/marketanalysis/, including the MarketDigestRepository
port interface which this slice must implement.

Goal: create the core migration for the four Phase-1 tables and implement
MarketDigestRepository against them. Do NOT create any Phase 2/3 tables
(no guidance_history, regulatory_tracker, private_funding_round,
analyst_rating_change, or ticker_watchlist — those belong to later slices).

Create:
- db/migration/V<next>__create_market_digest_tables.sql — check the actual
  latest migration version number in this repo first and use the correct
  next number, do not hardcode a guess.
- infrastructure/marketanalysis/persistence/MarketDigestEntity.java
- infrastructure/marketanalysis/persistence/MarketDigestEntryEntity.java
- infrastructure/marketanalysis/persistence/MarketDigestJpaRepository.java
- infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapter.java
  (implements the domain MarketDigestRepository port)
- infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapterIT.java

Schema (four tables):

CREATE TABLE market_digest (
    id BIGSERIAL PRIMARY KEY,
    digest_date DATE NOT NULL UNIQUE,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE market_digest_entry (
    id BIGSERIAL PRIMARY KEY,
    digest_id BIGINT NOT NULL REFERENCES market_digest(id) ON DELETE CASCADE,
    headline TEXT NOT NULL,
    summary TEXT NOT NULL,
    source_urls TEXT[] NOT NULL,
    category VARCHAR(32) NOT NULL,
    fact_classification VARCHAR(16) NOT NULL,
    market_impact_rank SMALLINT NOT NULL CHECK (market_impact_rank BETWEEN 1 AND 5),
    published_at TIMESTAMPTZ NOT NULL,
    embedding VECTOR(1536)
);

CREATE TABLE market_digest_impact_assessment (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    dimension VARCHAR(32) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    rationale TEXT NOT NULL
);

CREATE TABLE market_digest_affected_company (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    ticker_symbol VARCHAR(16),
    role VARCHAR(64) NOT NULL,
    quote_price NUMERIC(12,2),
    quote_change_pct NUMERIC(6,2),
    market_cap NUMERIC(20,2)
);

CREATE INDEX idx_market_digest_entry_date ON market_digest_entry(digest_id);
CREATE INDEX idx_market_digest_entry_embedding ON market_digest_entry USING hnsw (embedding vector_cosine_ops);

Implementation notes:
- Add the embedding column now (schema-only change) even though nothing
  populates or queries it until a later dedup slice — leave it nullable.
- Follow this project's existing migration-naming convention.
- Use whatever integration-test setup (Testcontainers or otherwise) this
  project already uses for other repository adapters — match that pattern,
  don't introduce a new test infrastructure approach.
- The unique constraint on digest_date means a re-run for the same date
  should upsert (INSERT ... ON CONFLICT or a find-then-update pattern in
  the adapter) rather than throwing — confirm this is what's wanted, but
  default to upsert since daily jobs may be re-run manually.

Acceptance criteria:
1. Migration applies cleanly with no naming collisions against the current
   schema.
2. save() persists a MarketDigest with N entries, N impact assessments, and
   M affected companies in one transaction.
3. findByDate() round-trips a saved digest back into an equivalent domain
   object.
4. findByDate() for a non-existent date returns Optional.empty().
5. Saving twice for the same date upserts rather than throwing.

When done, run the migration and integration tests and confirm they pass
before summarizing.
```

---

## Prompt 1.4 — Perplexity news research adapter

```
Implement Slice 1.4 of the Market Analysis module: the Perplexity news
research adapter, per the "Market Analysis" section of CLAUDE.md.

Context: this project already has a Perplexity API integration used by the
existing Trends pipeline (an async Deep Research job). Find that existing
client/wrapper class and reuse it — do not create a second HTTP client for
Perplexity. This slice implements the domain port
domain/marketanalysis/port/MarketNewsResearchPort.java created in Slice 1.1.

Goal: implement MarketNewsResearchPort using Perplexity's Search API (NOT
Deep Research — Search is faster and cheaper, appropriate for a daily
cadence; Deep Research stays reserved for the weekly Trends job).

Create:
- infrastructure/marketanalysis/perplexity/PerplexityMarketNewsAdapter.java
- infrastructure/marketanalysis/perplexity/MarketNewsItemMapper.java
- infrastructure/marketanalysis/perplexity/PerplexityMarketNewsAdapterTest.java

The adapter should search for: "the latest major news from the last 24
hours affecting AI in healthcare — regulatory actions (FDA, EU AI Act),
earnings/guidance from public AI-healthcare companies, big pharma AI
drug-discovery deals, major funding rounds (>$50M), and M&A. Cite sources
with URLs." Use the Search API's built-in recency filter for the last-24h
window rather than relying on prompt text alone to filter by date.

Implementation notes:
- The mapper's job is to extract NewsCategory, sourceUrls, and any
  mentioned company names/tickers from the raw search response into
  MarketNewsItem instances. If the raw search response isn't already
  structured enough to do this reliably, emit a looser intermediate shape
  here and defer full structured extraction to the classifier adapter
  (Slice 1.5) rather than adding a second LLM call in this adapter —
  avoid duplicating LLM calls across slices.
- Match whatever rate-limit/retry policy the existing Trends pipeline
  Perplexity integration already uses.
- Catch and wrap any Perplexity client exceptions in a typed exception
  (e.g. MarketNewsResearchException) so the domain/service layer never
  sees a raw HTTP client exception.

Acceptance criteria:
1. Adapter returns a non-empty list for a known historical date with
   confirmed AI-healthcare news (use a fixed past date in the test, not
   "today", for reproducibility).
2. Adapter returns an empty list, not an exception, when the search
   genuinely finds nothing.
3. Every returned MarketNewsItem has at least one non-blank source URL.
4. A simulated network/API failure surfaces as the typed exception, not
   a raw HTTP client exception.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 1.5 — Claude/Bedrock impact classifier adapter

```
Implement Slice 1.5 of the Market Analysis module: the Claude/Bedrock
impact classifier adapter, per the "Market Analysis" section of CLAUDE.md.

Context: this project uses Spring AI with a Bedrock-backed ChatClient
elsewhere in the codebase (for newsletter formatting in the Trends
pipeline). Reuse the existing ChatClient bean/configuration pattern rather
than creating a new one. This slice implements the domain port
domain/marketanalysis/port/ImpactClassifierPort.java created in Slice 1.1.

Goal: for each MarketDigestEntry, produce in a SINGLE structured LLM call:
- FactClassification (CONFIRMED vs SPECULATIVE)
- A confidence score from 0-100 (how certain the classification/sourcing
  is — independent of impact magnitude)
- Exactly 5 ImpactAssessment records, one per ImpactDimension (REVENUE,
  EARNINGS, VALUATION, INVESTOR_SENTIMENT, FUTURE_GROWTH), each with a
  direction (POSITIVE/NEGATIVE/NEUTRAL) and a one-sentence rationale
- A MarketImpactRank from 1 (highest impact) to 5 (lowest)

Do NOT split confidence scoring into a separate call — the model is already
reasoning about source reliability while classifying, so capture it in the
same structured response schema.

Create:
- infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapter.java
- infrastructure/marketanalysis/ai/ImpactClassificationPrompts.java
- infrastructure/marketanalysis/ai/ImpactClassificationResponse.java (DTO
  matching the structured JSON schema)
- infrastructure/marketanalysis/ai/ClaudeImpactClassifierAdapterTest.java

System prompt should instruct the model to classify as CONFIRMED (official
press release, SEC filing, regulatory announcement, earnings call) or
SPECULATIVE (analyst commentary, unconfirmed pacing claims, "why it
matters" framing), then score the 5 impact dimensions, then assign the
1-5 rank considering stock price magnitude, company market cap, and
whether the news is a near-term catalyst vs. a long-term structural
signal. Require strict JSON output matching the DTO schema.

Implementation notes:
- Use Spring AI's structured output support (.entity(Type.class)) rather
  than hand-parsing JSON strings.
- Pin a specific Bedrock model version in configuration — do not use a
  "latest" alias, since that would let classification behavior drift
  silently over time.
- Write a golden-fixture contract test now (fixed input, expected output
  shape) even though this is also listed as a general testing
  recommendation — cheap to add here, prevents future prompt-schema drift
  from silently breaking the pipeline.

Acceptance criteria:
1. Given a fixture representing a confirmed SEC-filed earnings beat, the
   adapter returns FactClassification.CONFIRMED with a confidence score of
   80 or higher.
2. Given a fixture representing speculative analyst "why it matters"
   framing with no primary-source citation, the adapter returns
   FactClassification.SPECULATIVE.
3. Every returned entry has exactly 5 ImpactAssessment records, one per
   ImpactDimension value, each with a non-blank rationale.
4. Malformed/non-conforming LLM output raises a typed adapter exception
   (e.g. ImpactClassificationException) rather than letting a raw JSON
   parse exception propagate to the service layer.

When done, run the tests and confirm they pass before summarizing.
```

---

## Prompt 1.6 — Scheduler, async wiring, and end-to-end pipeline assembly

```
Implement Slice 1.6 of the Market Analysis module: the scheduler, async
wiring, and full pipeline assembly, per the "Market Analysis" section of
CLAUDE.md.

Context: Slices 1.1-1.5 already exist:
- domain/marketanalysis/ — domain model and ports (Slice 1.1)
- application/marketanalysis/MarketDigestService.java — currently has only
  isMarketMoving() and sorting implemented, everything else throws
  UnsupportedOperationException (Slice 1.2)
- infrastructure/marketanalysis/persistence/ — MarketDigestRepository
  adapter (Slice 1.3)
- infrastructure/marketanalysis/perplexity/ — MarketNewsResearchPort
  adapter (Slice 1.4)
- infrastructure/marketanalysis/ai/ — ImpactClassifierPort adapter
  (Slice 1.5)

IMPORTANT: the MarketDataPort adapter (Slice 1.7) may or may not exist yet
when you run this session. Check infrastructure/marketanalysis/marketdata/
first. If it doesn't exist, create a temporary no-op stub adapter (a
@Component implementing MarketDataPort that always returns Optional.empty()
for getQuote/getPriceHistory) so this slice compiles and runs end-to-end —
the real adapter from Slice 1.7 will replace this stub later without
needing changes to this slice's code.

Goal: complete MarketDigestService.generateDailyDigest() to run the full
research → enrich(market data) → classify → filter → save → notify
pipeline, wire it to a daily @Scheduled job, and ensure it runs on a
dedicated async executor so it never blocks the Spring scheduler thread
pool.

Create/modify:
- application/marketanalysis/MarketDigestService.java (complete — remove
  the Slice 1.2 UnsupportedOperationException stubs)
- application/marketanalysis/MarketDigestScheduler.java
- config/MarketAnalysisConfig.java
- application/marketanalysis/MarketDigestServiceIT.java

Pipeline logic for generateDailyDigest(LocalDate date):
1. Call newsResearch.findRecentAiHealthcareNews() for the last 24h window.
2. If empty, return MarketDigest.empty(date) immediately.
3. Enrich each item with market data (resolve mentioned public companies,
   pull quotes via marketData.getQuote()).
4. Classify all enriched entries via the impact classifier (single batch
   or per-entry — match how Slice 1.5's adapter is designed).
5. Filter to only entries passing isMarketMoving(), sort ascending by rank.
6. Save the resulting MarketDigest via the repository.
7. If there's at least one qualifying entry, call the notifier port (a
   simple mock/no-op notifier is fine for this slice — real SES wiring is
   Slice 1.8).

Scheduler: daily at 7:00 AM America/Chicago —
@Scheduled(cron = "0 0 7 * * *", zone = "America/Chicago")

Async config: create a dedicated executor bean named
"marketAnalysisExecutor" (core pool size 2, max pool size 4) — do NOT reuse
or collide with whatever executor bean the existing Trends pipeline uses;
check its bean name first and pick a distinct one.

Acceptance criteria:
1. Manually invoking generateDailyDigest() (bypassing the cron trigger)
   runs the full pipeline against the real Slice 1.3-1.5 adapters (market
   data can be the no-op stub) and persists a MarketDigest.
2. The scheduled job is registered and its cron expression parses
   correctly (verify via Spring's scheduling infrastructure or an actuator
   endpoint).
3. A run with zero qualifying entries persists an empty digest and does
   NOT call the notifier (verify with a mock).
4. The pipeline executes on the dedicated async executor thread, not the
   calling thread (assert via Thread.currentThread().getName() in the
   integration test).

When done, run the integration test and confirm it passes before
summarizing. Flag clearly whether you used a real or stubbed MarketDataPort
adapter.
```

---

## Prompt 1.7 — Market data adapter (Alpaca, quotes/OHLCV)

```
Implement Slice 1.7 of the Market Analysis module: the market data adapter,
per the "Market Analysis" section of CLAUDE.md. This slice can be done in
parallel with Slices 1.4 and 1.5 — it only depends on Slice 1.1's domain
model/ports.

Context: this implements domain/marketanalysis/port/MarketDataPort.java
created in Slice 1.1. If Slice 1.6 already created a temporary no-op stub
adapter for this port (check infrastructure/marketanalysis/marketdata/
first), this slice's real adapter should replace that stub — the port
interface and MarketDigestService should require zero changes.

Goal: implement MarketDataPort against Alpaca's Market Data API, scoped
initially to a fixed, config-driven list of tracked tickers rather than
general ticker-resolution logic.

PROVIDER DECISION (already made): use Alpaca (https://alpaca.markets)
Market Data API — specifically the IEX real-time quote feed and the
historical bars (OHLCV) endpoint. Rationale: genuinely free for this
workload (no daily request cap, no credit card required), real-time
quotes rather than delayed, and commercial use is permitted at this
scale — a good fit for a once-daily digest job querying a handful of
tracked tickers. Do not re-litigate this choice or ask which provider to
use; proceed directly to implementation.

Alpaca API basics to use:
- Base URL for market data: https://data.alpaca.markets/v2
- Auth: API key ID + secret key sent as APCA-API-KEY-ID and
  APCA-API-SECRET-KEY headers (NOT a bearer token). Load these from
  config properties (e.g. alpaca.api.key-id / alpaca.api.secret-key),
  sourced from environment variables — do not hardcode credentials.
- Latest quote: GET /v2/stocks/{symbol}/quotes/latest
- Latest trade/price: GET /v2/stocks/{symbol}/trades/latest (or use the
  snapshot endpoint GET /v2/stocks/{symbol}/snapshot for quote + daily
  bar in one call — prefer snapshot since it also gives you the daily
  OHLC needed for "change" calculation without a second request)
- Historical bars (OHLCV): GET /v2/stocks/{symbol}/bars with
  start/end/timeframe query params (e.g. timeframe=1Day)
- Market cap is NOT provided by Alpaca's market data API — you'll need to
  compute it from shares outstanding (not available either) or omit it/
  mark it optional in the Quote domain type. Flag this explicitly: either
  relax the acceptance criteria to not require market cap from this
  adapter, or source market cap from a secondary free source. Recommend
  making market cap Optional<BigDecimal> in the Quote record and leaving
  it empty from this adapter for now — do not block on this.

Create:
- infrastructure/marketanalysis/marketdata/AlpacaMarketDataAdapter.java
- infrastructure/marketanalysis/marketdata/AlpacaClientConfig.java
- infrastructure/marketanalysis/marketdata/AlpacaQuoteResponse.java (DTO
  matching Alpaca's snapshot/quote JSON shape)
- infrastructure/marketanalysis/marketdata/AlpacaBarsResponse.java (DTO
  matching Alpaca's bars JSON shape)
- infrastructure/marketanalysis/marketdata/AlpacaMarketDataAdapterTest.java

Tracked-ticker list should be a config property (e.g. a comma-separated
list or a small YAML list) — start with DOCS, EVH, ASTH, GSK and make it
easy to extend without a code change.

Implementation notes:
- getQuote(tickerSymbol) should return Optional.empty() (not throw) for any
  ticker not in the tracked list, and also for genuine provider outages —
  a market-data hiccup must never fail the whole digest run.
- getPriceHistory(tickerSymbol, start, end) returns OHLCV rows for the
  requested range, mapped from Alpaca's /v2/stocks/{symbol}/bars response.
- Use Spring's RestClient (matching the project's existing HTTP client
  conventions) rather than introducing a new HTTP library.
- Alpaca's free tier has no hard daily cap but does have a fair-use rate
  limit — add basic retry-with-backoff on 429 responses.
- Sign up for an Alpaca account (paper/trading account is fine — market
  data access does not require funding a live brokerage account) to
  obtain API key ID and secret key before running integration tests.

Acceptance criteria:
1. getQuote() for a tracked ticker returns a populated Quote with price and
   change populated from Alpaca's snapshot endpoint (market cap may be
   empty/Optional — Alpaca does not provide it).
2. getQuote() for an untracked/unknown ticker returns Optional.empty(), not
   an exception.
3. getPriceHistory() returns OHLCV rows covering the requested date range
   for a tracked ticker, sourced from Alpaca's bars endpoint.
4. A simulated provider timeout/outage (or 429 rate-limit response) is
   caught and surfaces as Optional.empty() at the port boundary after
   retry/backoff is exhausted, not a propagated exception.

When done, run the tests and confirm they pass before summarizing. Flag
clearly that market cap is not populated by this adapter so a later slice
(or a secondary data source) can address it if it turns out to be needed.
```

---

## Prompt 1.8 — SES notifier, REST endpoints, and digest formatter

```
Implement Slice 1.8 of the Market Analysis module: the SES notifier, REST
endpoints, and HTML digest formatter, per the "Market Analysis" section of
CLAUDE.md.

Context: Slice 1.6 already wired the full MarketDigestService pipeline with
a mock/no-op notifier. This slice replaces that with a real
MarketDigestNotifier implementation using the project's existing SES email
integration, and adds the read-only REST API for the React frontend.

Find and reuse the existing SES email sender wrapper and subscriber-list
query pattern already used elsewhere in this app for transactional/digest
email (check how the Trends pipeline or any other newsletter-adjacent
feature sends email) — do not build a second SES client.

Create:
- infrastructure/marketanalysis/notification/SesMarketDigestNotifier.java
- infrastructure/marketanalysis/notification/MarketDigestFormatter.java
- web/marketanalysis/MarketDigestController.java
- web/marketanalysis/MarketDigestResponse.java
- web/marketanalysis/MarketDigestSummary.java
- infrastructure/marketanalysis/notification/MarketDigestFormatterTest.java
  (snapshot test)
- web/marketanalysis/MarketDigestControllerIT.java

REST endpoints:
- GET /api/market-digest/{date} -> MarketDigestResponse (404 if no digest
  exists for that date)
- GET /api/market-digest/latest -> MarketDigestResponse (most recent
  digest regardless of whether it had qualifying entries)
- GET /api/market-digest?from=&to= -> paginated List<MarketDigestSummary>,
  sorted by date descending
- POST /api/market-digest/subscribe -> subscribes the current
  authenticated user to the daily email digest (idempotent — calling twice
  should not create duplicate subscriptions)

MarketDigestResponse fields: ranked entries, each with a confirmed/
speculative badge, the five impact-dimension scores with rationale, and
inline source links. Design the DTO now to tolerate null/absent
confidence-score, deal-terms, guidance-delta, and analyst-rating fields
(those get populated by Phase 2/3 slices later) — do not require them.

MarketDigestFormatter.toHtml() renders the email body: top-3 ranked
entries max, with confirmed/speculative labels and citation links.

Implementation notes:
- Snapshot-test the formatter's HTML output specifically so future changes
  to the template are caught as an intentional diff, not a silent
  regression.
- The notifier should only be invoked by MarketDigestService when the
  digest has at least one qualifying entry — this logic already exists
  in Slice 1.6's service; just confirm the mock-based Slice 1.6 test still
  passes with this real adapter wired in.

Acceptance criteria:
1. GET /api/market-digest/{date} returns 200 with a populated response for
   an existing digest, 404 for a date with none.
2. GET /api/market-digest/latest returns the most recent digest even if it
   had zero qualifying entries.
3. GET /api/market-digest?from=&to= returns a paginated, date-descending
   list of summaries within range.
4. POST /api/market-digest/subscribe is idempotent on repeat calls for the
   same user.
5. The notifier is only invoked for digests with >=1 qualifying entry.
6. The formatter snapshot test fails loudly on any HTML structure change.

When done, run all tests and confirm they pass before summarizing.
```

---

## Prompt 1.9 — Deduplication via pgvector embedding similarity

```
Implement Slice 1.9 of the Market Analysis module: embedding-based
deduplication, per the "Market Analysis" section of CLAUDE.md. This slice
can be done any time after Slice 1.3 (it only needs the persistence layer
and the embedding column already present in the schema).

Context: the market_digest_entry table already has a nullable
embedding VECTOR(1536) column (added in Slice 1.3) that has been unused
until now. This project's Trends pipeline already uses pgvector with some
embedding provider — find and reuse that same embedding model/provider,
do not introduce a second one.

Goal: prevent re-notifying on a story that resurfaces across multiple days
(e.g. ongoing earnings-reaction coverage) by comparing each new entry's
embedding against recent entries via cosine similarity, within a rolling
7-day window. Deduplication must suppress re-NOTIFICATION only — every
entry should still always be persisted regardless of whether it's a
near-duplicate.

Create/modify:
- infrastructure/marketanalysis/embedding/EntryEmbeddingService.java
- infrastructure/marketanalysis/persistence/MarketDigestRepositoryAdapter.java
  (add a similarity-search query method, e.g.
  findSimilarEntriesWithinWindow(embedding, sinceDate, threshold))
- application/marketanalysis/MarketDigestService.java (add a dedup check
  before invoking the notifier — after saving, not before)
- infrastructure/marketanalysis/embedding/EntryEmbeddingServiceTest.java

Implementation notes:
- Embed on the concatenation of headline + summary.
- Use a cosine-similarity threshold as a named Spring config property
  (e.g. market-analysis.dedup.similarity-threshold), starting around
  0.92-0.95 — do not hardcode it as a magic number in the code.
- The dedup check: for each newly classified qualifying entry, query for
  similar entries already persisted within the last 7 days above the
  threshold. If one exists AND it already triggered a notification, skip
  notifying for the new entry (but still save it). If no similar entry
  exists, or a similar one exists but was never notified, proceed to
  notify normally.
- Use pgvector's cosine distance operator (<=>) in the repository query.

Acceptance criteria:
1. Two near-identical entries (same earnings event, reworded headline)
   generated on consecutive days are both persisted, but only the first
   triggers a notification.
2. Two genuinely distinct entries with superficially similar wording (e.g.
   two different companies' earnings on the same day) are both notified.
3. The similarity threshold is read from configuration, not hardcoded.

When done, run the tests and confirm they pass before summarizing.
```

---

## Notes on running these sessions

- **Parallel-safe:** Prompts 1.4, 1.5, and 1.7 can run in separate Claude Code sessions simultaneously once Prompt 1.1 has landed and been merged — they touch disjoint file sets.
- **Sequential requirement:** Prompt 1.6 needs 1.2, 1.3, 1.4, and 1.5 all merged first (it wires them together). Prompts 1.8 and 1.9 need 1.6 (or at minimum 1.3, for 1.9) merged.
- **Before each session:** paste the corresponding prompt as-is into a fresh Claude Code Plan Mode session. Review the plan it proposes against the acceptance criteria listed in the prompt before letting it execute.
- **Provider decision resolved:** Slice 1.7 is now locked to Alpaca's Market Data API (free tier, real-time IEX quotes, no daily cap, commercial use permitted at this scale). You'll need an Alpaca account and API key/secret before running that session's integration tests. Note that Alpaca does not expose market cap, so the Quote type should treat that field as optional — this is called out in the prompt itself.
