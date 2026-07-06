package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalyticsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA-backed adapter implementing {@link AnalyticsPort}.
 *
 * <p>Issues aggregate JPQL queries against the existing JPA repositories to
 * compute counts, groupings, and averages.  No new tables or entities are
 * introduced — all queries run against existing schema.
 *
 * <p>The {@code bestVariantId} field of the returned {@link EvaluationAnalytics}
 * is intentionally left {@code null}; the
 * {@link com.wgblackmon.aihealthcare.domain.service.AnalyticsService} applies
 * that business rule after receiving the raw aggregates from this adapter.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-07-05
 */
@Slf4j
@Component
public class AnalyticsAdapter implements AnalyticsPort {

    private static final DateTimeFormatter DATE_KEY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final NewsArticleRepository articleRepository;
    private final NewsletterRunRepository runRepository;
    private final EvaluationResultRepository evaluationRepository;
    private final ComparisonResultRepository comparisonRepository;

    public AnalyticsAdapter(NewsArticleRepository articleRepository,
                             NewsletterRunRepository runRepository,
                             EvaluationResultRepository evaluationRepository,
                             ComparisonResultRepository comparisonRepository) {
        this.articleRepository    = articleRepository;
        this.runRepository        = runRepository;
        this.evaluationRepository = evaluationRepository;
        this.comparisonRepository = comparisonRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IngestionAnalytics getIngestionAnalytics() {
        log.debug("getIngestionAnalytics()");

        long total = articleRepository.count();

        List<Object[]> tierRows = articleRepository.countBySourceTierGrouped();
        List<CountByLabel> byTier = new ArrayList<>();
        for (Object[] row : tierRows) {
            String label = row[0] != null ? String.valueOf(row[0]) : "UNKNOWN";
            long count = ((Number) row[1]).longValue();
            byTier.add(new CountByLabel(label.isBlank() ? "UNKNOWN" : label, count));
        }

        List<Object[]> topicRows = articleRepository.countByTopicGrouped();
        List<CountByLabel> byTopic = new ArrayList<>();
        for (Object[] row : topicRows) {
            String label = row[0] != null ? String.valueOf(row[0]) : "UNKNOWN";
            long count = ((Number) row[1]).longValue();
            byTopic.add(new CountByLabel(label.isBlank() ? "UNKNOWN" : label, count));
        }

        Instant sevenDaysAgo  = Instant.now().minus(7,  ChronoUnit.DAYS);
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long last7Days  = articleRepository.countByCreatedAtAfter(sevenDaysAgo);
        long last30Days = articleRepository.countByCreatedAtAfter(thirtyDaysAgo);

        IngestionAnalytics result = new IngestionAnalytics(total, byTier, byTopic, last7Days, last30Days);
        log.debug("getIngestionAnalytics() | return={}", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunAnalytics getRunAnalytics() {
        log.debug("getRunAnalytics()");

        long total    = runRepository.count();
        long draft    = 0;
        long sent     = 0;
        long archived = 0;

        List<Object[]> statusRows = runRepository.countByStatusGrouped();
        for (Object[] row : statusRows) {
            NewsletterRunStatus status = (NewsletterRunStatus) row[0];
            long count = ((Number) row[1]).longValue();
            if (status == NewsletterRunStatus.DRAFT)    draft    = count;
            else if (status == NewsletterRunStatus.SENT)     sent     = count;
            else if (status == NewsletterRunStatus.ARCHIVED) archived = count;
        }

        Instant mostRecent = runRepository.findMostRecentGeneratedAt().orElse(null);

        RunAnalytics result = new RunAnalytics(total, draft, sent, archived, mostRecent);
        log.debug("getRunAnalytics() | return={}", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EvaluationAnalytics getEvaluationAnalytics() {
        log.debug("getEvaluationAnalytics()");

        long total       = evaluationRepository.count();
        long comparisons = comparisonRepository.count();

        List<Object[]> rows = evaluationRepository.getVariantAggregates();
        List<VariantScore> variantScores = new ArrayList<>();
        for (Object[] row : rows) {
            variantScores.add(new VariantScore(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).longValue(),
                    toDouble(row[3]),
                    toDouble(row[4]),
                    toDouble(row[5]),
                    toDouble(row[6]),
                    toDouble(row[7]),
                    toDouble(row[8])
            ));
        }

        // bestVariantId is intentionally null here — AnalyticsService computes it
        EvaluationAnalytics result = new EvaluationAnalytics(total, comparisons, variantScores, null);
        log.debug("getEvaluationAnalytics() | return={}", result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CountByLabel> getDailyArticleCounts(int days) {
        log.debug("getDailyArticleCounts() | days={}", days);

        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(days));

        // Pre-fill all days with zero counts
        Map<String, Long> dailyCounts = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            String dateKey = DATE_KEY_FMT.format(now.minus(Duration.ofDays(i)));
            dailyCounts.put(dateKey, 0L);
        }

        // Overlay actual counts from the database
        List<Object[]> rows = articleRepository.countByDayGrouped(since);
        for (Object[] row : rows) {
            String dateKey = row[0].toString();
            // Normalize to yyyy-MM-dd in case DB returns datetime format
            if (dateKey.length() > 10) {
                dateKey = dateKey.substring(0, 10);
            }
            long count = ((Number) row[1]).longValue();
            dailyCounts.put(dateKey, count);
        }

        List<CountByLabel> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : dailyCounts.entrySet()) {
            result.add(new CountByLabel(entry.getKey(), entry.getValue()));
        }

        log.debug("getDailyArticleCounts() | return={} entries", result.size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CountByLabel> getTopicDistribution(int limit) {
        log.debug("getTopicDistribution() | limit={}", limit);

        List<Object[]> topicRows = articleRepository.countByTopicGrouped();
        List<CountByLabel> result = new ArrayList<>();
        int cap = Math.min(topicRows.size(), limit);
        for (int i = 0; i < cap; i++) {
            Object[] row = topicRows.get(i);
            String label = row[0] != null ? String.valueOf(row[0]) : "Unknown";
            long count = ((Number) row[1]).longValue();
            result.add(new CountByLabel(label.isBlank() ? "Unknown" : label, count));
        }

        log.debug("getTopicDistribution() | return={} entries", result.size());
        return result;
    }

    private double toDouble(Object val) {
        if (val == null) {
            return 0.0;
        }
        return ((Number) val).doubleValue();
    }
}
