package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.EntryEmbeddingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain service that builds a de-duplicated weekly market digest rollup.
 *
 * <p>The pipeline:
 * <ol>
 *   <li>Load all {@link MarketDigest} records for the 7-day window
 *       [{@code weekStart}, {@code weekStart + 6 days}].</li>
 *   <li>Flatten all qualifying entries across all days.</li>
 *   <li>Collapse near-duplicates:
 *     <ul>
 *       <li>If {@link EntryEmbeddingPort} is present: group by cosine similarity
 *           against a configurable threshold.</li>
 *       <li>If null (no embedding model): group by normalized headline
 *           (lowercase + strip non-alphanumeric) as a lightweight fallback.</li>
 *     </ul>
 *   </li>
 *   <li>For each group, elect the representative entry (lowest rank value = highest priority)
 *       and record the occurrence count.</li>
 *   <li>Sort the result ascending by best rank.</li>
 * </ol>
 *
 * <p>This is a plain domain class — not a {@code @Component}. It is wired by
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.MarketAnalysisConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
public class WeeklyRollupService {

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.92;

    private final MarketDigestRepository repository;
    private final EntryEmbeddingPort     embeddingPort;
    private final double                 similarityThreshold;

    public WeeklyRollupService(MarketDigestRepository repository,
                                EntryEmbeddingPort embeddingPort,
                                double similarityThreshold) {
        this.repository          = repository;
        this.embeddingPort       = embeddingPort;
        this.similarityThreshold = similarityThreshold;
        log.debug("WeeklyRollupService() | embeddingPortPresent={}, threshold={}",
                embeddingPort != null, similarityThreshold);
    }

    /**
     * Builds a {@link WeeklyRollup} for the 7-day window starting on {@code weekStart}.
     *
     * @param weekStart the first day of the week (non-null)
     * @return rollup with de-duplicated, ranked entries; never null
     */
    public WeeklyRollup buildRollup(LocalDate weekStart) {
        log.debug("buildRollup() | weekStart={}", weekStart);

        LocalDate weekEnd = weekStart.plusDays(6);
        List<MarketDigest> digests = repository.findByDateRange(weekStart, weekEnd);
        log.info("buildRollup() | loaded {} digests for week of {}", digests.size(), weekStart);

        List<MarketDigestEntry> allEntries = new ArrayList<>();
        for (MarketDigest digest : digests) {
            for (MarketDigestEntry entry : digest.entries()) {
                allEntries.add(entry);
            }
        }

        log.debug("buildRollup() | flattenedEntries={}", allEntries.size());

        List<RollupEntry> rollupEntries;
        if (embeddingPort != null && !allEntries.isEmpty()) {
            rollupEntries = collapseByEmbedding(allEntries);
        } else {
            rollupEntries = collapseByHeadline(allEntries);
        }

        rollupEntries.sort((a, b) -> Integer.compare(a.bestRank().value(), b.bestRank().value()));

        WeeklyRollup result = new WeeklyRollup(weekStart, rollupEntries, Instant.now());
        log.info("buildRollup() | rollup has {} entries after collapse (from {} raw)", rollupEntries.size(), allEntries.size());
        log.debug("buildRollup() | return={}", result);
        return result;
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    /**
     * Collapses entries by normalized headline (lowercase + strip non-alphanumeric).
     * Used when no embedding port is available.
     */
    private List<RollupEntry> collapseByHeadline(List<MarketDigestEntry> entries) {
        Map<String, List<MarketDigestEntry>> groups = new HashMap<>();
        List<String> keyOrder = new ArrayList<>();

        for (MarketDigestEntry entry : entries) {
            String key = normalizeHeadline(entry.newsItem().headline());
            if (!groups.containsKey(key)) {
                keyOrder.add(key);
                groups.put(key, new ArrayList<>());
            }
            groups.get(key).add(entry);
        }

        List<RollupEntry> result = new ArrayList<>();
        for (String key : keyOrder) {
            result.add(toRollupEntry(groups.get(key)));
        }
        return result;
    }

    /**
     * Collapses entries by embedding cosine similarity. Falls back to headline
     * grouping for any entry whose embedding fails.
     */
    private List<RollupEntry> collapseByEmbedding(List<MarketDigestEntry> entries) {
        float[][] embeddings = new float[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            MarketDigestEntry entry = entries.get(i);
            try {
                embeddings[i] = embeddingPort.embed(
                        entry.newsItem().headline(), entry.newsItem().summary());
            } catch (Exception e) {
                log.warn("collapseByEmbedding() | embedding failed for entry {}: {}", i, e.getMessage());
                embeddings[i] = null;
            }
        }

        boolean[] assigned = new boolean[entries.size()];
        List<List<MarketDigestEntry>> groups = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            if (assigned[i]) continue;

            List<MarketDigestEntry> group = new ArrayList<>();
            group.add(entries.get(i));
            assigned[i] = true;

            if (embeddings[i] == null) {
                groups.add(group);
                continue;
            }

            for (int j = i + 1; j < entries.size(); j++) {
                if (assigned[j] || embeddings[j] == null) continue;
                double sim = MarketDigestService.cosineSimilarity(embeddings[i], embeddings[j]);
                if (sim >= similarityThreshold) {
                    group.add(entries.get(j));
                    assigned[j] = true;
                }
            }
            groups.add(group);
        }

        List<RollupEntry> result = new ArrayList<>();
        for (List<MarketDigestEntry> group : groups) {
            result.add(toRollupEntry(group));
        }
        return result;
    }

    /**
     * Elects the best representative from a group (lowest rank value)
     * and counts occurrences.
     */
    private RollupEntry toRollupEntry(List<MarketDigestEntry> group) {
        MarketDigestEntry best = group.get(0);
        FactClassification bestFact = best.factClassification();

        for (int i = 1; i < group.size(); i++) {
            MarketDigestEntry candidate = group.get(i);
            if (candidate.rank().value() < best.rank().value()) {
                best = candidate;
            }
            // CONFIRMED overrides SPECULATIVE
            if (candidate.factClassification() == FactClassification.CONFIRMED) {
                bestFact = FactClassification.CONFIRMED;
            }
        }

        return new RollupEntry(best, best.rank(), bestFact, group.size());
    }

    private static String normalizeHeadline(String headline) {
        return headline.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
