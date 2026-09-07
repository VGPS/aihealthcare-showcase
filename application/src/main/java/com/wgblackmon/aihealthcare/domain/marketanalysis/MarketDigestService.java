package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.CorporateActionPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ImpactClassifierPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.SecondaryNewsCheckPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application-layer service orchestrating the daily AI-healthcare market digest pipeline.
 *
 * <p>The full daily digest pipeline:
 * <ol>
 *   <li>Skip if a digest for {@code date} already exists in the repository.</li>
 *   <li>Research: fetch raw news from the 24h window ending at midnight on {@code date}.</li>
 *   <li>Cross-check (optional): fetch secondary news from Alpaca News API for tracked tickers;
 *       merge with Perplexity results, de-duplicating by normalized headline.</li>
 *   <li>Wrap each item in a preliminary {@link MarketDigestEntry} (SPECULATIVE/rank-5/empty assessments).</li>
 *   <li>Classify: enrich assessments, fact classification, and rank via {@link ImpactClassifierPort}.</li>
 *   <li>Peer-tag companies against the peer-group classifier.</li>
 *   <li>Filter: discard non-qualifying entries; sort qualifying entries rank-ascending.</li>
 *   <li>Persist the resulting {@link MarketDigest} via {@link MarketDigestRepository}.</li>
 *   <li>Notify via {@link MarketDigestNotifier} when ≥1 entry qualifies (notifier is nullable).</li>
 * </ol>
 *
 * <p>Qualifying bar: an entry clears when its category is EARNINGS, REGULATORY, M_AND_A,
 * or MAJOR_PARTNERSHIP, or when it is FUNDING with a disclosed deal size strictly greater
 * than $50,000,000 (exact $50M does NOT qualify).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-07  added regulatory tracker, private funding, and deal terms enrichment
 */
@Slf4j
public class MarketDigestService implements ProduceMarketDigestUseCase {

    static final int DEDUP_LOOKBACK_DAYS = 7;

    private static final PeerGroupTagger PEER_GROUP_TAGGER = new PeerGroupTagger();
    private static final Pattern DOCKET_PATTERN = Pattern.compile(
            "([A-Z]{2,5}-\\d{4}-[A-Z]-\\d{3,5}|\\d{3,4}/\\d{5,7}|K\\d{6,}|DEN\\d{6,})");

    private final MarketNewsResearchPort    newsResearch;
    private final MarketDataPort            marketData;
    private final ImpactClassifierPort      impactClassifier;
    private final MarketDigestRepository    repository;
    private final MarketDigestNotifier      notifier;
    private final EntryEmbeddingPort        embeddingPort;
    private final double                    dedupThreshold;
    private final SecondaryNewsCheckPort    secondaryNewsCheck;
    private final CorporateActionPort       corporateActionPort;
    private final RegulatoryTrackerRepository regulatoryTrackerRepository;
    private final PrivateFundingPort         privateFundingPort;
    private final DealTermsPort             dealTermsPort;

    public MarketDigestService(
            MarketNewsResearchPort newsResearch,
            MarketDataPort marketData,
            ImpactClassifierPort impactClassifier,
            MarketDigestRepository repository,
            MarketDigestNotifier notifier,
            EntryEmbeddingPort embeddingPort,
            double dedupThreshold,
            SecondaryNewsCheckPort secondaryNewsCheck,
            CorporateActionPort corporateActionPort,
            RegulatoryTrackerRepository regulatoryTrackerRepository,
            PrivateFundingPort privateFundingPort,
            DealTermsPort dealTermsPort) {
        this.newsResearch               = newsResearch;
        this.marketData                 = marketData;
        this.impactClassifier           = impactClassifier;
        this.repository                 = repository;
        this.notifier                   = notifier;
        this.embeddingPort              = embeddingPort;
        this.dedupThreshold             = dedupThreshold;
        this.secondaryNewsCheck         = secondaryNewsCheck;
        this.corporateActionPort        = corporateActionPort;
        this.regulatoryTrackerRepository = regulatoryTrackerRepository;
        this.privateFundingPort          = privateFundingPort;
        this.dealTermsPort              = dealTermsPort;
    }

    /**
     * Runs the full daily digest pipeline for the given date.
     *
     * <p>Idempotent: if a digest already exists for {@code date} it is returned
     * unchanged — the pipeline is not re-executed.
     *
     * @param date the digest date (non-null)
     * @return the persisted (or pre-existing) digest
     */
    @Override
    public MarketDigest generateDailyDigest(LocalDate date) {
        log.debug("generateDailyDigest() | date={}", date);

        Optional<MarketDigest> existing = repository.findByDate(date);
        if (existing.isPresent()) {
            log.info("generateDailyDigest() | digest already exists for {} — skipping pipeline", date);
            log.debug("generateDailyDigest() | return=existing");
            return existing.get();
        }

        Instant since = date.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant until = date.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<MarketNewsItem> newsItems = newsResearch.findRecentAiHealthcareNews(since);
        log.info("generateDailyDigest() | research returned {} raw items for {}", newsItems.size(), date);

        // Step 1b: secondary cross-check from Alpaca News API (optional, fail-open)
        List<MarketNewsItem> mergedItems = mergeWithSecondaryCheck(newsItems, since, until);

        if (mergedItems.isEmpty()) {
            MarketDigest emptyDigest = MarketDigest.empty(date);
            repository.save(emptyDigest);
            log.info("generateDailyDigest() | saved empty digest for {}", date);
            log.debug("generateDailyDigest() | return=emptyDigest");
            return emptyDigest;
        }

        List<MarketDigestEntry> preliminary = new ArrayList<>();
        for (MarketNewsItem item : mergedItems) {
            preliminary.add(new MarketDigestEntry(
                    item,
                    new ArrayList<>(),
                    FactClassification.SPECULATIVE,
                    new MarketImpactRank(5),
                    new ArrayList<>()
            ));
        }

        List<MarketDigestEntry> classified = impactClassifier.classify(preliminary);

        List<MarketDigestEntry> peerTagged = new ArrayList<>();
        for (MarketDigestEntry entry : classified) {
            peerTagged.add(PEER_GROUP_TAGGER.tagEntry(entry));
        }
        log.debug("generateDailyDigest() | peer-tagged {} entries", peerTagged.size());

        List<MarketDigestEntry> qualified  = filterAndSort(peerTagged);

        log.info("generateDailyDigest() | {} of {} items cleared qualifying bar for {}",
                qualified.size(), classified.size(), date);

        MarketDigest digest = new MarketDigest(date, qualified, Instant.now());
        repository.save(digest);
        log.info("generateDailyDigest() | digest saved for {}", date);

        // Optional corporate action confirmation step (Slice 3.7)
        if (corporateActionPort != null && !qualified.isEmpty()) {
            try {
                corporateActionPort.attachConfirmations(digest, date);
            } catch (Exception e) {
                log.warn("generateDailyDigest() | corporate action confirmation failed (non-fatal): {}", e.getMessage());
            }
        }

        // Post-save enrichment: extract and persist regulatory trackers, funding rounds, deal terms
        extractRegulatoryTrackers(qualified);
        extractPrivateFundingRounds(qualified);
        extractDealTerms(qualified);

        if (!qualified.isEmpty() && notifier != null) {
            List<MarketDigestEntry> notifiable = deduplicateForNotification(qualified, date);
            log.info("generateDailyDigest() | dedup: {} of {} entries are novel (threshold={})",
                    notifiable.size(), qualified.size(), dedupThreshold);
            if (!notifiable.isEmpty()) {
                try {
                    notifier.notify(new MarketDigest(date, notifiable, digest.generatedAt()));
                    log.info("generateDailyDigest() | notifier invoked for {} novel entries", notifiable.size());
                } catch (Exception e) {
                    log.warn("generateDailyDigest() | notifier failed (non-fatal): {}", e.getMessage());
                }
            }
        }

        log.debug("generateDailyDigest() | return={}", digest);
        return digest;
    }

    /**
     * Returns the most recently generated digest, or empty if none has been persisted yet.
     *
     * @return the latest digest wrapped in Optional, or empty
     */
    @Override
    public Optional<MarketDigest> findLatest() {
        log.debug("findLatest()");
        Optional<MarketDigest> result = repository.findLatest();
        log.debug("findLatest() | return=present:{}", result.isPresent());
        return result;
    }

    /**
     * Returns the digest for the given date, or empty if none exists.
     *
     * @param date the digest date to query (non-null)
     * @return the digest wrapped in Optional, or empty
     */
    @Override
    public Optional<MarketDigest> findByDate(LocalDate date) {
        log.debug("findByDate() | date={}", date);
        Optional<MarketDigest> result = repository.findByDate(date);
        log.debug("findByDate() | return=present:{}", result.isPresent());
        return result;
    }

    /**
     * Returns all persisted digests ordered newest-first.
     *
     * @return list of all digests; empty list if none exist
     */
    @Override
    public List<MarketDigest> findAll() {
        log.debug("findAll()");
        List<MarketDigest> result = repository.findAll();
        log.debug("findAll() | return.size={}", result.size());
        return result;
    }

    /**
     * Returns digests whose date falls within [{@code from}, {@code to}] (inclusive),
     * ordered newest-first.
     *
     * @param from start date, inclusive (non-null)
     * @param to   end date, inclusive (non-null)
     * @return list of matching digests; empty list if none found
     */
    @Override
    public List<MarketDigest> findByDateRange(LocalDate from, LocalDate to) {
        log.debug("findByDateRange() | from={}, to={}", from, to);
        List<MarketDigest> result = repository.findByDateRange(from, to);
        log.debug("findByDateRange() | return.size={}", result.size());
        return result;
    }

    /**
     * Merges primary Perplexity news items with secondary Alpaca News items,
     * de-duplicating by normalized headline (lowercase + stripped punctuation).
     *
     * <p>Perplexity items are always preserved. Alpaca items are appended only when
     * their normalized headline does not already appear in the primary set.
     * If {@link #secondaryNewsCheck} is null or the call fails, the primary list
     * is returned as-is (fail-open behaviour).
     *
     * @param primary  items from the primary Perplexity research adapter
     * @param since    start of the time window passed to the secondary adapter
     * @param until    end of the time window passed to the secondary adapter
     * @return merged, de-duplicated list (never null)
     */
    List<MarketNewsItem> mergeWithSecondaryCheck(
            List<MarketNewsItem> primary, Instant since, Instant until) {
        log.debug("mergeWithSecondaryCheck() | primary.size={}", primary.size());

        if (secondaryNewsCheck == null) {
            log.debug("mergeWithSecondaryCheck() | no secondaryNewsCheck — returning primary only");
            return primary;
        }

        List<MarketNewsItem> secondary;
        try {
            secondary = secondaryNewsCheck.findRecentNews(since, until);
        } catch (Exception e) {
            log.warn("mergeWithSecondaryCheck() | secondary check failed (non-fatal): {}", e.getMessage());
            return primary;
        }

        if (secondary.isEmpty()) {
            log.debug("mergeWithSecondaryCheck() | secondary returned 0 items");
            return primary;
        }

        // Build normalized headline set from primary items
        java.util.Set<String> seenHeadlines = new java.util.HashSet<>();
        for (MarketNewsItem item : primary) {
            seenHeadlines.add(normalizeHeadline(item.headline()));
        }

        List<MarketNewsItem> merged = new ArrayList<>(primary);
        int added = 0;
        for (MarketNewsItem item : secondary) {
            String normalized = normalizeHeadline(item.headline());
            if (!seenHeadlines.contains(normalized)) {
                merged.add(item);
                seenHeadlines.add(normalized);
                added++;
            }
        }

        log.info("mergeWithSecondaryCheck() | secondary added {} novel items (total={})", added, merged.size());
        log.debug("mergeWithSecondaryCheck() | return.size={}", merged.size());
        return merged;
    }

    /**
     * Normalizes a headline for de-duplication comparison: lowercase and strips non-alphanumeric.
     *
     * @param headline the raw headline string (non-null)
     * @return lowercase alphanumeric-only string
     */
    static String normalizeHeadline(String headline) {
        return headline.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Returns {@code true} when the entry clears the market-moving qualifying bar.
     *
     * <p>Qualifying categories: EARNINGS, REGULATORY, M_AND_A, MAJOR_PARTNERSHIP always qualify.
     * FUNDING qualifies only when {@code entry.dealSizeUsd()} is strictly greater than
     * $50,000,000 (exact $50M does NOT qualify). All other categories (OTHER, and FUNDING
     * at or below the threshold) return {@code false}.
     *
     * @param entry the classified digest entry to evaluate (non-null)
     * @return true if this entry should be included in the daily digest and notification
     */
    boolean isMarketMoving(MarketDigestEntry entry) {
        log.debug("isMarketMoving() | category={}, dealSizeUsd={}", entry.category(), entry.dealSizeUsd());
        boolean result;
        NewsCategory category = entry.category();
        if (category == NewsCategory.EARNINGS) {
            result = true;
        } else if (category == NewsCategory.REGULATORY) {
            result = true;
        } else if (category == NewsCategory.FUNDING) {
            result = entry.dealSizeUsd() > 50_000_000L;
        } else if (category == NewsCategory.M_AND_A) {
            result = true;
        } else if (category == NewsCategory.MAJOR_PARTNERSHIP) {
            result = true;
        } else {
            result = false;
        }
        log.debug("isMarketMoving() | return={}", result);
        return result;
    }

    /**
     * Filters candidate entries against recent digests using embedding cosine similarity.
     *
     * <p>For each candidate, computes its embedding and compares against embeddings of all
     * entries from the preceding {@link #DEDUP_LOOKBACK_DAYS} days. If any recent entry's
     * similarity exceeds {@link #dedupThreshold}, the candidate is suppressed (not returned).
     *
     * <p>If {@link #embeddingPort} is null, or if any embedding call fails, the candidate
     * is included in the output (fail-open: prefer over-notification to missed alerts).
     *
     * @param candidates new qualifying entries to evaluate
     * @param date       the digest date (used to compute the lookback window)
     * @return subset of candidates that are novel enough to warrant notification
     */
    List<MarketDigestEntry> deduplicateForNotification(List<MarketDigestEntry> candidates, LocalDate date) {
        log.debug("deduplicateForNotification() | candidates={}, date={}", candidates.size(), date);

        if (embeddingPort == null) {
            log.debug("deduplicateForNotification() | no embeddingPort — skipping dedup, return all");
            return candidates;
        }

        LocalDate lookbackFrom = date.minusDays(DEDUP_LOOKBACK_DAYS);
        LocalDate lookbackTo   = date.minusDays(1);
        List<MarketDigest> recentDigests = repository.findByDateRange(lookbackFrom, lookbackTo);

        List<MarketDigestEntry> recentEntries = new ArrayList<>();
        for (MarketDigest recent : recentDigests) {
            for (MarketDigestEntry entry : recent.entries()) {
                recentEntries.add(entry);
            }
        }

        if (recentEntries.isEmpty()) {
            log.debug("deduplicateForNotification() | no recent entries — all candidates are novel");
            return candidates;
        }

        List<float[]> recentEmbeddings = new ArrayList<>();
        for (MarketDigestEntry recent : recentEntries) {
            float[] vec = embeddingPort.embed(recent.newsItem().headline(), recent.newsItem().summary());
            recentEmbeddings.add(vec);
        }

        List<MarketDigestEntry> novel = new ArrayList<>();
        for (MarketDigestEntry candidate : candidates) {
            float[] candidateVec = embeddingPort.embed(
                    candidate.newsItem().headline(), candidate.newsItem().summary());

            if (candidateVec == null) {
                novel.add(candidate);
                continue;
            }

            boolean isDuplicate = false;
            for (float[] recentVec : recentEmbeddings) {
                if (recentVec != null && cosineSimilarity(candidateVec, recentVec) >= dedupThreshold) {
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                novel.add(candidate);
            }
        }

        log.debug("deduplicateForNotification() | return.size={}", novel.size());
        return novel;
    }

    /**
     * Computes cosine similarity between two float vectors.
     *
     * <p>Returns 0.0 if either vector is zero-length (degenerate case — treated as dissimilar).
     *
     * @param a first vector (non-null, same length as b)
     * @param b second vector (non-null, same length as a)
     * @return similarity in [-1.0, 1.0] where 1.0 = identical direction
     */
    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Filters the given entries to only qualifying ones, then sorts them ascending by rank
     * (rank 1 = highest market impact, returned first).
     *
     * @param entries entries to filter and sort (non-null)
     * @return new list containing only qualifying entries, sorted rank ascending
     */
    List<MarketDigestEntry> filterAndSort(List<MarketDigestEntry> entries) {
        log.debug("filterAndSort() | entries.size={}", entries.size());
        List<MarketDigestEntry> qualifying = new ArrayList<>();
        for (MarketDigestEntry entry : entries) {
            if (isMarketMoving(entry)) {
                qualifying.add(entry);
            }
        }
        qualifying.sort((a, b) -> Integer.compare(a.rank().value(), b.rank().value()));
        log.debug("filterAndSort() | return.size={}", qualifying.size());
        return qualifying;
    }

    // ─── post-save enrichment ──────────────────────────────────────────────

    void extractRegulatoryTrackers(List<MarketDigestEntry> entries) {
        log.debug("extractRegulatoryTrackers() | entries={}", entries.size());
        if (regulatoryTrackerRepository == null) {
            log.debug("extractRegulatoryTrackers() | port absent — skipping");
            return;
        }
        int upserted = 0;
        for (MarketDigestEntry entry : entries) {
            if (entry.category() != NewsCategory.REGULATORY) {
                continue;
            }
            try {
                String combined = entry.newsItem().headline() + " " + entry.newsItem().summary();
                Jurisdiction jurisdiction = inferJurisdiction(combined);
                RulemakingStage stage = inferRulemakingStage(combined);
                String docketId = extractDocketId(combined, entry.newsItem().headline());

                String sourceUrl = entry.newsItem().sourceUrls() != null
                        && !entry.newsItem().sourceUrls().isEmpty()
                        ? entry.newsItem().sourceUrls().get(0) : null;
                RegulatoryTracker tracker = new RegulatoryTracker(
                        jurisdiction, stage, docketId,
                        entry.newsItem().headline(), null, Instant.now(), sourceUrl);
                regulatoryTrackerRepository.upsert(tracker);
                upserted++;
            } catch (Exception e) {
                log.warn("extractRegulatoryTrackers() | failed for '{}': {}",
                        entry.newsItem().headline(), e.getMessage());
            }
        }
        log.info("extractRegulatoryTrackers() | upserted {} trackers", upserted);
        log.debug("extractRegulatoryTrackers() | return=void");
    }

    void extractPrivateFundingRounds(List<MarketDigestEntry> entries) {
        log.debug("extractPrivateFundingRounds() | entries={}", entries.size());
        if (privateFundingPort == null) {
            log.debug("extractPrivateFundingRounds() | port absent — skipping");
            return;
        }
        int saved = 0;
        for (MarketDigestEntry entry : entries) {
            if (entry.category() != NewsCategory.FUNDING) {
                continue;
            }
            for (AffectedCompany company : entry.affectedCompanies()) {
                if (company.tickerSymbol() != null && !company.tickerSymbol().isBlank()) {
                    continue;
                }
                try {
                    String roundStage = inferFundingStage(
                            entry.newsItem().headline() + " " + entry.newsItem().summary());
                    String fundingSourceUrl = entry.newsItem().sourceUrls() != null
                            && !entry.newsItem().sourceUrls().isEmpty()
                            ? entry.newsItem().sourceUrls().get(0) : null;
                    PrivateFundingRound round = new PrivateFundingRound(
                            company.name(), roundStage,
                            entry.newsItem().dealSizeUsd(),
                            List.of(), entry.newsItem().publishedAt(), fundingSourceUrl);
                    PeerGroup peerGroup = company.peerGroup() != null
                            ? company.peerGroup() : PeerGroup.OTHER;
                    privateFundingPort.save(round, peerGroup);
                    saved++;
                } catch (Exception e) {
                    log.warn("extractPrivateFundingRounds() | failed for '{}': {}",
                            company.name(), e.getMessage());
                }
            }
        }
        log.info("extractPrivateFundingRounds() | saved {} funding rounds", saved);
        log.debug("extractPrivateFundingRounds() | return=void");
    }

    void extractDealTerms(List<MarketDigestEntry> entries) {
        log.debug("extractDealTerms() | entries={}", entries.size());
        if (dealTermsPort == null) {
            log.debug("extractDealTerms() | port absent — skipping");
            return;
        }
        int saved = 0;
        for (MarketDigestEntry entry : entries) {
            if (entry.category() != NewsCategory.M_AND_A) {
                continue;
            }
            try {
                Long dealSize = entry.newsItem().dealSizeUsd();
                DisclosedPortion portion = dealSize != null
                        ? DisclosedPortion.PARTIAL : DisclosedPortion.UNDISCLOSED;
                String dealSourceUrl = entry.newsItem().sourceUrls() != null
                        && !entry.newsItem().sourceUrls().isEmpty()
                        ? entry.newsItem().sourceUrls().get(0) : null;
                DealTerms terms = new DealTerms(dealSize, null, null, null, portion, dealSourceUrl);
                dealTermsPort.save(entry.newsItem().headline(), terms);
                saved++;
            } catch (Exception e) {
                log.warn("extractDealTerms() | failed for '{}': {}",
                        entry.newsItem().headline(), e.getMessage());
            }
        }
        log.info("extractDealTerms() | saved {} deal terms", saved);
        log.debug("extractDealTerms() | return=void");
    }

    // ─── keyword inference helpers ─────────────────────────────────────────

    static Jurisdiction inferJurisdiction(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("fda") || lower.contains("food and drug")) {
            return Jurisdiction.US_FDA;
        }
        if (lower.contains("eu ai act") || lower.contains("european") || lower.contains("ce mark")) {
            return Jurisdiction.EU_AI_ACT;
        }
        if (lower.contains("mhra") || lower.contains("uk ")) {
            return Jurisdiction.UK_MHRA;
        }
        if (lower.contains("state") && (lower.contains("law") || lower.contains("bill")
                || lower.contains("legislation"))) {
            return Jurisdiction.US_STATE;
        }
        if (lower.contains("cms") || lower.contains("medicare") || lower.contains("medicaid")) {
            return Jurisdiction.US_FDA;
        }
        return Jurisdiction.OTHER;
    }

    static RulemakingStage inferRulemakingStage(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("enforce") || lower.contains("penalty") || lower.contains("fine")) {
            return RulemakingStage.ENFORCEMENT;
        }
        if (lower.contains("final guidance") || lower.contains("finalize") || lower.contains("approve")
                || lower.contains("clearance") || lower.contains("cleared")) {
            return RulemakingStage.FINAL_GUIDANCE;
        }
        if (lower.contains("draft guidance") || lower.contains("draft rule")
                || lower.contains("proposed rule")) {
            return RulemakingStage.DRAFT_GUIDANCE;
        }
        if (lower.contains("comment period") || lower.contains("public comment")
                || lower.contains("request for comment")) {
            return RulemakingStage.COMMENT_PERIOD;
        }
        if (lower.contains("discussion") || lower.contains("concept release")
                || lower.contains("white paper")) {
            return RulemakingStage.DISCUSSION_PAPER;
        }
        return RulemakingStage.FINAL_GUIDANCE;
    }

    static String extractDocketId(String combined, String headline) {
        Matcher matcher = DOCKET_PATTERN.matcher(combined);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "REG-" + Integer.toHexString(headline.hashCode());
    }

    static String inferFundingStage(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("series a")) return "Series A";
        if (lower.contains("series b")) return "Series B";
        if (lower.contains("series c")) return "Series C";
        if (lower.contains("series d")) return "Series D";
        if (lower.contains("series e")) return "Series E";
        if (lower.contains("seed")) return "Seed";
        if (lower.contains("pre-seed")) return "Pre-Seed";
        if (lower.contains("growth")) return "Growth";
        if (lower.contains("ipo") || lower.contains("initial public offering")) return "Pre-IPO";
        return "Undisclosed";
    }
}
