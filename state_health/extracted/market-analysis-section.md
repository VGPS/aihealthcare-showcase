## Market Analysis

### Overview

The Market Analysis module tracks daily market-moving news in AI-powered healthcare — regulatory actions, public-company earnings/guidance, funding rounds, and M&A — and produces a ranked, fact-vs-speculation-separated digest. It reuses the existing Deep Research + scheduled-job pattern already established for the Trends pipeline (async Perplexity Deep Research calls, Postgres persistence, Claude/Bedrock post-processing for formatting), and adds a lightweight market-data lookup step for any publicly traded company mentioned in the news.

This section is designed to be pasted into `CLAUDE.md` (or a linked `docs/market-analysis-spec.md` referenced from it) as a new spec slice, following the same spec-driven development approach used for the rest of the app.

---

### Domain model (hexagonal — domain layer)

```
domain/
  marketanalysis/
    MarketNewsItem.java          // record: headline, summary, sourceUrls, publishedAt, category
    FactClassification.java      // enum: CONFIRMED, SPECULATIVE
    ConfidenceScore.java         // 0-100: how certain the classification/sourcing is, independent of rank
    ImpactDimension.java         // enum: REVENUE, EARNINGS, VALUATION, INVESTOR_SENTIMENT, FUTURE_GROWTH
    ImpactAssessment.java        // record: dimension, direction (POSITIVE/NEGATIVE/NEUTRAL), rationale
    MarketImpactRank.java        // 1 (highest) .. 5 (lowest) -- magnitude of impact, distinct from ConfidenceScore
    AffectedCompany.java         // record: name, tickerSymbol (nullable for private cos), role (e.g. "earnings subject", "deal counterparty"), peerGroup
    PeerGroup.java                // enum: AI_SCRIBE_DOCUMENTATION, VALUE_BASED_CARE_PLATFORM, DIAGNOSTIC_IMAGING_AI, DRUG_DISCOVERY_AI, DIGITAL_THERAPEUTICS, OTHER
    PrivateFundingRound.java      // record: companyName, roundStage (SEED/SERIES_A.../GROWTH), amountUsd, leadInvestors, announcedAt
    DealTerms.java                 // record: upfrontCashUsd, milestonePaymentsUsd, equityStakePct, royaltyPct, disclosedPortion (FULL/PARTIAL/UNDISCLOSED)
    AnalystRatingChange.java      // record: firm, tickerSymbol, previousRating, newRating, previousPriceTarget, newPriceTarget, changedAt
    GuidanceComparison.java       // record: tickerSymbol, priorGuidanceLow, priorGuidanceHigh, newGuidanceLow, newGuidanceHigh, metric (e.g. "FY2027 revenue")
    RulemakingStage.java          // enum: DISCUSSION_PAPER, COMMENT_PERIOD, DRAFT_GUIDANCE, FINAL_GUIDANCE, ENFORCEMENT
    Jurisdiction.java              // enum: US_FDA, EU_AI_ACT, UK_MHRA, US_STATE, OTHER
    RegulatoryTracker.java         // record: jurisdiction, stage, docketId, commentDeadline, lastUpdatedAt -- links back to the originating MarketDigestEntry
    MarketDigestEntry.java       // aggregate root: NewsItem + List<ImpactAssessment> + FactClassification + ConfidenceScore + rank + List<AffectedCompany> + optional DealTerms/PrivateFundingRound/RegulatoryTracker
    MarketDigest.java            // aggregate: date, List<MarketDigestEntry>, generatedAt
```

**Peer clustering rationale:** tagging each `AffectedCompany` with a `PeerGroup` lets a single earnings beat (e.g. Doximity in `AI_SCRIBE_DOCUMENTATION`) surface as a read-through signal for private peers (Abridge, Nabla, Suki) that have no ticker to enrich but still belong in the digest narrative — cross-reference `PeerGroup` against your existing AI-healthcare company database (populated via the Agent API/wide-research pipeline) so peers can be named even without market data.

**Confidence vs. rank:** `ConfidenceScore` and `MarketImpactRank` are deliberately separate axes — a confirmed SEC filing with modest dollar impact is high-confidence/low-rank, while an unconfirmed rumor of a large acquisition is low-confidence/high-rank. Collapsing these into one score loses information the classifier otherwise has to discard.

**Ports (interfaces owned by domain):**

```java
public interface MarketNewsResearchPort {
    List<MarketNewsItem> findRecentAiHealthcareNews(Instant since);
}

public interface MarketDataPort {
    Optional<Quote> getQuote(String tickerSymbol);
    Optional<PriceHistory> getPriceHistory(String tickerSymbol, LocalDate start, LocalDate end);
}

public interface PrivateFundingPort {
    // Crunchbase/PitchBook-style lookup for rounds involving companies with no ticker.
    // Complements MarketDataPort, which only covers public tickers.
    List<PrivateFundingRound> findRecentRounds(Instant since, PeerGroup peerGroupFilter);
}

public interface AnalystRatingPort {
    // Sell-side rating/price-target changes, typically lag the earnings reaction
    // by 1-3 days -- query on a rolling window, not just same-day.
    List<AnalystRatingChange> findRecentChanges(String tickerSymbol, Instant since);
}

public interface GuidancePort {
    // Stores each company's last-given guidance so a beat/raise can be quantified
    // precisely ("raised from $X-Y to $A-B") instead of restated qualitatively.
    Optional<GuidanceComparison> getPriorGuidance(String tickerSymbol, String metric);
    void recordGuidance(GuidanceComparison comparison);
}

public interface RegulatoryTrackerRepository {
    // Tracks a regulatory item across its rulemaking lifecycle (discussion paper ->
    // comment period -> draft guidance -> final guidance -> enforcement) so a
    // digest can re-surface an item when its stage changes or a deadline nears.
    void upsert(RegulatoryTracker tracker);
    List<RegulatoryTracker> findApproachingDeadlines(Duration within);
}

public interface MarketDigestRepository {
    void save(MarketDigest digest);
    Optional<MarketDigest> findByDate(LocalDate date);
}

public interface MarketDigestNotifier {
    void notify(MarketDigest digest); // only called when digest has >=1 qualifying entry
}

public interface TickerWatchlistRepository {
    // Per-subscriber watchlist so the digest can call out "your tracked names"
    // separately from general sector news.
    List<String> findWatchedTickers(UUID subscriberId);
}
```

---

### Application layer — the analysis pipeline

```java
@Service
public class MarketDigestService {

    private final MarketNewsResearchPort newsResearch;      // adapter: Perplexity Deep Research / Search API
    private final MarketDataPort marketData;                // adapter: stock quote/OHLCV provider
    private final PrivateFundingPort privateFunding;         // adapter: Crunchbase/PitchBook-style lookup
    private final AnalystRatingPort analystRatings;           // adapter: sell-side rating/PT changes
    private final GuidancePort guidance;                      // adapter: prior-guidance store/comparison
    private final RegulatoryTrackerRepository regulatoryTracker;
    private final ImpactClassifierPort impactClassifier;     // adapter: Claude/Bedrock LLM call
    private final MarketDigestRepository repository;
    private final MarketDigestNotifier notifier;

    public MarketDigest generateDailyDigest(LocalDate date) {
        // 1. Research: pull last-24h AI-healthcare news
        List<MarketNewsItem> rawItems = newsResearch.findRecentAiHealthcareNews(
                date.atStartOfDay(ZoneOffset.UTC).minusDays(1).toInstant()
        );

        // 1b. Sweep private funding rounds (>$50M threshold applied at the filter
        // step below) and regulatory items approaching a comment/decision deadline
        // -- these can surface even on days with no fresh news item, since a
        // deadline crossing is itself notification-worthy.
        List<MarketNewsItem> fundingItems = privateFunding
                .findRecentRounds(date.atStartOfDay(ZoneOffset.UTC).minusDays(1).toInstant(), null)
                .stream().map(MarketNewsItem::fromFundingRound).toList();
        List<RegulatoryTracker> approachingDeadlines = regulatoryTracker.findApproachingDeadlines(Duration.ofDays(7));

        List<MarketNewsItem> allItems = Stream.concat(rawItems.stream(), fundingItems.stream()).toList();
        if (allItems.isEmpty() && approachingDeadlines.isEmpty()) {
            return MarketDigest.empty(date);
        }

        // 2. Enrich: resolve any mentioned public companies + pull quotes/price
        //    history, prior-guidance deltas, and recent analyst rating changes
        List<MarketDigestEntry> enriched = allItems.stream()
                .map(this::enrichWithMarketData)
                .map(this::enrichWithGuidanceAndRatings)
                .toList();

        // 3. Classify: LLM pass separates fact vs. speculation, assigns a
        //    confidence score, scores 5 impact dimensions, and ranks 1-5 by
        //    magnitude of market impact
        List<MarketDigestEntry> classified = impactClassifier.classify(enriched);

        // 4. Filter: keep only entries that clear the "qualifying" bar
        List<MarketDigestEntry> qualifying = classified.stream()
                .filter(this::isMarketMoving)
                .sorted(Comparator.comparingInt(MarketDigestEntry::rank))
                .toList();

        MarketDigest digest = new MarketDigest(date, qualifying, Instant.now());
        repository.save(digest);

        if (!qualifying.isEmpty() || !approachingDeadlines.isEmpty()) {
            notifier.notify(digest); // notifier also renders approaching-deadline callouts, see formatter notes below
        }
        return digest;
    }

    private boolean isMarketMoving(MarketDigestEntry entry) {
        // Qualifying bar — mirrors the daily-alert filter used in production:
        // stock-moving earnings result, regulatory ruling, funding round > $50M,
        // M&A, or a large partnership/deal.
        return entry.category() == NewsCategory.EARNINGS
            || entry.category() == NewsCategory.REGULATORY
            || (entry.category() == NewsCategory.FUNDING && entry.dealSizeUsd() > 50_000_000)
            || entry.category() == NewsCategory.M_AND_A
            || entry.category() == NewsCategory.MAJOR_PARTNERSHIP;
    }

    private MarketDigestEntry enrichWithMarketData(MarketNewsItem item) {
        List<AffectedCompany> companies = item.affectedCompanies();
        Map<String, Quote> quotes = companies.stream()
                .filter(c -> c.tickerSymbol() != null)
                .collect(toMap(AffectedCompany::tickerSymbol,
                        c -> marketData.getQuote(c.tickerSymbol()).orElse(null)));
        return MarketDigestEntry.fromNewsItem(item, quotes);
    }

    private MarketDigestEntry enrichWithGuidanceAndRatings(MarketDigestEntry entry) {
        if (entry.newsItem().category() != NewsCategory.EARNINGS) {
            return entry; // guidance/rating deltas are only meaningful for earnings items
        }
        List<GuidanceComparison> guidanceDeltas = entry.affectedCompanies().stream()
                .filter(c -> c.tickerSymbol() != null)
                .flatMap(c -> guidance.getPriorGuidance(c.tickerSymbol(), "revenue").stream())
                .toList();
        List<AnalystRatingChange> ratingChanges = entry.affectedCompanies().stream()
                .filter(c -> c.tickerSymbol() != null)
                .flatMap(c -> analystRatings.findRecentChanges(c.tickerSymbol(), Instant.now().minus(3, ChronoUnit.DAYS)).stream())
                .toList();
        return entry.withGuidanceAndRatings(guidanceDeltas, ratingChanges);
    }
}
```

---

### Scheduled job

Follows the same `@Scheduled` + async pattern already used for the weekly Trends job — daily cadence instead of weekly, timed for early morning before market open.

```java
@Component
public class MarketDigestScheduler {

    private final MarketDigestService digestService;

    // 7:00 AM America/Chicago daily — before market open, after overnight news cycle
    @Scheduled(cron = "0 0 7 * * *", zone = "America/Chicago")
    public void runDailyDigest() {
        digestService.generateDailyDigest(LocalDate.now(ZoneId.of("America/Chicago")));
    }
}
```

Enable async execution so the Deep Research call (which can take 60-120s) doesn't block the scheduler thread pool:

```java
@Configuration
@EnableAsync
@EnableScheduling
public class MarketAnalysisConfig {
    @Bean
    public Executor marketAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("market-digest-");
        executor.initialize();
        return executor;
    }
}
```

---

### Adapters (infrastructure layer)

**1. News research adapter — Perplexity API**

```java
@Component
public class PerplexityMarketNewsAdapter implements MarketNewsResearchPort {

    private final PerplexityClient client; // existing wrapper used by Trends pipeline

    private static final String QUERY_TEMPLATE = """
        Research the latest major news from the last 24 hours affecting AI in
        healthcare: regulatory actions (FDA, EU AI Act), earnings/guidance from
        public AI-healthcare companies, big pharma AI drug-discovery deals,
        major funding rounds (>$50M), and M&A. Cite sources with URLs.
        """;

    @Override
    public List<MarketNewsItem> findRecentAiHealthcareNews(Instant since) {
        // Use Search API (not Deep Research) for this — daily cadence + speed/cost
        // profile favors search over the slower/more-expensive Deep Research call,
        // which remains reserved for the weekly Trends pipeline.
        var response = client.search(QUERY_TEMPLATE, SearchOptions.builder()
                .recency(Duration.ofHours(24))
                .domain("healthcare,finance")
                .build());
        return newsItemMapper.map(response);
    }
}
```

**2. Market data adapter — plug in your quote/OHLCV provider of choice**

```java
@Component
public class StockQuoteAdapter implements MarketDataPort {

    private final RestClient marketDataClient; // e.g. a market-data REST API client

    @Override
    public Optional<Quote> getQuote(String tickerSymbol) {
        return Optional.ofNullable(
            marketDataClient.get()
                .uri("/quotes/{ticker}", tickerSymbol)
                .retrieve()
                .body(Quote.class)
        );
    }

    @Override
    public Optional<PriceHistory> getPriceHistory(String tickerSymbol, LocalDate start, LocalDate end) {
        return Optional.ofNullable(
            marketDataClient.get()
                .uri("/ohlcv/{ticker}?start={start}&end={end}", tickerSymbol, start, end)
                .retrieve()
                .body(PriceHistory.class)
        );
    }
}
```

**3. Impact classifier adapter — Claude via Spring AI / Bedrock**

```java
@Component
public class ClaudeImpactClassifierAdapter implements ImpactClassifierPort {

    private final ChatClient chatClient; // Spring AI ChatClient, Bedrock-backed

    private static final String SYSTEM_PROMPT = """
        You are a financial news analyst. For each news item, classify it as
        CONFIRMED (official press release, SEC filing, regulatory announcement,
        earnings call) or SPECULATIVE (analyst commentary, unconfirmed pacing
        claims, "why it matters" framing). Then score impact across five
        dimensions — revenue, earnings, valuation, investor sentiment, future
        growth — each as POSITIVE/NEGATIVE/NEUTRAL with a one-sentence
        rationale. Finally assign a market impact rank from 1 (highest) to 5
        (lowest), considering stock price magnitude, company market cap, and
        whether the news is a near-term catalyst or a long-term structural
        signal. Respond in strict JSON matching the provided schema.
        """;

    @Override
    public List<MarketDigestEntry> classify(List<MarketDigestEntry> entries) {
        return entries.stream()
                .map(entry -> chatClient.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(entry.toPromptPayload())
                        .call()
                        .entity(MarketDigestEntry.class))
                .toList();
    }
}
```

**4. Notifier adapter — reuse existing SES/email pipeline**

```java
@Component
public class SesMarketDigestNotifier implements MarketDigestNotifier {

    private final SesEmailSender emailSender; // existing SES wrapper
    private final MarketDigestFormatter formatter;

    @Override
    public void notify(MarketDigest digest) {
        String html = formatter.toHtml(digest); // ranked top-3, fact/speculation labels, citations
        emailSender.send(
            "AI Healthcare Market Alert",
            html,
            subscriberRepository.findActiveMarketAlertSubscribers()
        );
    }
}
```

---

### Persistence (PostgreSQL)

```sql
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
    category VARCHAR(32) NOT NULL,             -- EARNINGS, REGULATORY, FUNDING, M_AND_A, MAJOR_PARTNERSHIP, OTHER
    fact_classification VARCHAR(16) NOT NULL,  -- CONFIRMED, SPECULATIVE
    market_impact_rank SMALLINT NOT NULL CHECK (market_impact_rank BETWEEN 1 AND 5),
    published_at TIMESTAMPTZ NOT NULL,
    embedding VECTOR(1536)                     -- pgvector, for dedup/similarity vs. prior digests
);

CREATE TABLE market_digest_impact_assessment (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    dimension VARCHAR(32) NOT NULL,   -- REVENUE, EARNINGS, VALUATION, INVESTOR_SENTIMENT, FUTURE_GROWTH
    direction VARCHAR(16) NOT NULL,   -- POSITIVE, NEGATIVE, NEUTRAL
    rationale TEXT NOT NULL
);

CREATE TABLE market_digest_affected_company (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    ticker_symbol VARCHAR(16),
    role VARCHAR(64) NOT NULL,
    peer_group VARCHAR(32),                    -- AI_SCRIBE_DOCUMENTATION, VALUE_BASED_CARE_PLATFORM, etc.
    quote_price NUMERIC(12,2),
    quote_change_pct NUMERIC(6,2),
    market_cap NUMERIC(20,2)
);

-- Confidence score + deal terms live on the entry itself
ALTER TABLE market_digest_entry ADD COLUMN confidence_score SMALLINT CHECK (confidence_score BETWEEN 0 AND 100);
ALTER TABLE market_digest_entry ADD COLUMN upfront_cash_usd NUMERIC(20,2);
ALTER TABLE market_digest_entry ADD COLUMN milestone_payments_usd NUMERIC(20,2);
ALTER TABLE market_digest_entry ADD COLUMN equity_stake_pct NUMERIC(5,2);
ALTER TABLE market_digest_entry ADD COLUMN royalty_pct NUMERIC(5,2);
ALTER TABLE market_digest_entry ADD COLUMN deal_terms_disclosed VARCHAR(16); -- FULL, PARTIAL, UNDISCLOSED

CREATE TABLE private_funding_round (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    round_stage VARCHAR(32) NOT NULL,          -- SEED, SERIES_A, SERIES_B, ..., GROWTH
    amount_usd NUMERIC(20,2) NOT NULL,
    lead_investors TEXT[],
    peer_group VARCHAR(32),
    announced_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE analyst_rating_change (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT REFERENCES market_digest_entry(id) ON DELETE CASCADE,
    firm VARCHAR(128) NOT NULL,
    ticker_symbol VARCHAR(16) NOT NULL,
    previous_rating VARCHAR(32),
    new_rating VARCHAR(32) NOT NULL,
    previous_price_target NUMERIC(12,2),
    new_price_target NUMERIC(12,2),
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE guidance_history (
    id BIGSERIAL PRIMARY KEY,
    ticker_symbol VARCHAR(16) NOT NULL,
    metric VARCHAR(64) NOT NULL,               -- e.g. "FY2027 revenue", "full-year adjusted EBITDA"
    guidance_low NUMERIC(20,2) NOT NULL,
    guidance_high NUMERIC(20,2) NOT NULL,
    given_at TIMESTAMPTZ NOT NULL,
    entry_id BIGINT REFERENCES market_digest_entry(id) ON DELETE SET NULL,
    UNIQUE (ticker_symbol, metric, given_at)
);
CREATE INDEX idx_guidance_history_latest ON guidance_history(ticker_symbol, metric, given_at DESC);

CREATE TABLE regulatory_tracker (
    id BIGSERIAL PRIMARY KEY,
    jurisdiction VARCHAR(32) NOT NULL,         -- US_FDA, EU_AI_ACT, UK_MHRA, US_STATE, OTHER
    docket_id VARCHAR(64),
    stage VARCHAR(32) NOT NULL,                -- DISCUSSION_PAPER, COMMENT_PERIOD, DRAFT_GUIDANCE, FINAL_GUIDANCE, ENFORCEMENT
    comment_deadline DATE,
    first_entry_id BIGINT REFERENCES market_digest_entry(id) ON DELETE SET NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (jurisdiction, docket_id)
);
CREATE INDEX idx_regulatory_tracker_deadline ON regulatory_tracker(comment_deadline) WHERE comment_deadline IS NOT NULL;

CREATE TABLE ticker_watchlist (
    id BIGSERIAL PRIMARY KEY,
    subscriber_id UUID NOT NULL REFERENCES subscriber(id) ON DELETE CASCADE,
    ticker_symbol VARCHAR(16) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (subscriber_id, ticker_symbol)
);

CREATE INDEX idx_market_digest_entry_date ON market_digest_entry(digest_id);
CREATE INDEX idx_market_digest_entry_embedding ON market_digest_entry USING hnsw (embedding vector_cosine_ops);
```

**On the new tables:** `guidance_history` and `regulatory_tracker` are intentionally *not* scoped to a single digest — they're standing reference tables that persist across digests so a later entry can look up "what did this company guide to last time" or "has this docket's comment period closed yet" regardless of which day's digest first introduced them. `private_funding_round` and `analyst_rating_change` stay entry-scoped since they're one-off signals tied to a specific day's news.

Use the `embedding` column + pgvector cosine similarity to dedupe stories that resurface across multiple days (e.g., an earnings reaction that keeps generating follow-up coverage) — skip re-notifying on near-duplicate entries within a rolling 7-day window.

---

### REST API (for the React frontend)

```
GET  /api/market-digest/{date}          -> MarketDigestResponse
GET  /api/market-digest/latest          -> MarketDigestResponse
GET  /api/market-digest?from=&to=       -> List<MarketDigestSummary>  (paginated)
GET  /api/market-digest/weekly-rollup?weekOf=  -> WeeklyRollupResponse  (biggest movers, aggregated across the week's digests)
POST /api/market-digest/subscribe       -> subscribe current user to daily email alert
GET  /api/market-digest/watchlist       -> current user's tracked tickers
PUT  /api/market-digest/watchlist       -> replace current user's tracked ticker list
```

`MarketDigestResponse` should mirror the presentation format already validated in the chat digest: ranked entries, each with confirmed/speculative badges, a confidence score, the five impact-dimension scores, deal-terms/guidance-delta/analyst-rating callouts where applicable, and inline source links. When a subscriber has a non-empty watchlist, entries mentioning a watched ticker should be flagged/sorted to the top of the response independent of their overall rank.

`WeeklyRollupResponse` aggregates the week's `MarketDigest` rows into a "biggest movers" view — reuse the pgvector dedup logic from the daily pipeline so a story that resurfaces across several days (e.g., ongoing earnings-reaction coverage) is collapsed into a single rollup entry rather than counted once per day.

---

### Notification qualifying bar (reused verbatim)

Only notify/publish when at least one entry meets:

- Stock-moving earnings result or guidance change
- Regulatory ruling or agency action (FDA, EU AI Act, MHRA, etc.)
- Funding round over $50M
- M&A
- A large partnership or deal (pharma AI collaboration, major vendor deal)

If nothing qualifies for a given day, `generateDailyDigest` still persists an empty `MarketDigest` (for audit/continuity) but skips the notifier call.

---

### Testing notes

- Unit test `MarketDigestService.isMarketMoving()` against each `NewsCategory` boundary (especially the `$50M` funding threshold).
- Contract-test the Claude classifier adapter with a fixed prompt fixture and golden JSON response to catch schema drift.
- Integration test the full pipeline against a stubbed `MarketNewsResearchPort` returning canned news items (e.g., a fixture earnings beat + a sub-threshold funding round) to verify filtering and ranking end-to-end.
- Snapshot-test `MarketDigestFormatter.toHtml()` output to catch email template regressions.

---

### Suggested implementation order

1. Domain records + ports (no I/O, pure Java) — enables unit testing the ranking/filtering logic immediately.
2. Postgres schema + repository adapter (core tables first: `market_digest`, `market_digest_entry`, `market_digest_impact_assessment`, `market_digest_affected_company`).
3. Perplexity news research adapter (reuse existing client/config from Trends pipeline).
4. Claude/Bedrock classifier adapter — include `ConfidenceScore` in the same LLM pass as fact classification and impact ranking, since the model is already reasoning about source reliability at that point.
5. Market data adapter (quotes/OHLCV) — stub with a fixed set of tracked tickers (DOCS, EVH, ASTH, GSK, etc.) initially; expand ticker resolution later if needed.
6. Scheduler + async executor wiring.
7. SES notifier + REST endpoints + React digest view.
8. **Phase 2 additions** (once the core loop is stable): `GuidancePort` + `guidance_history` table (needed before you can quantify "beat/raise" precisely), `PeerGroup` tagging cross-referenced against your existing company database, `RegulatoryTrackerRepository` + deadline sweep.
9. **Phase 3 additions** (lower urgency, add as the digest proves out): `PrivateFundingPort`, `AnalystRatingPort`, `DealTerms` structuring, ticker watchlists, weekly rollup view.

Phases 2 and 3 are ordered by expected value-to-effort ratio, not strict dependency — feel free to reorder based on which signal you find most useful once the daily digest is live.
