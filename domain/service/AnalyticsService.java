package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalyticsPort;

import java.util.List;

/**
 * Application-layer service implementing the analytics dashboard use case.
 *
 * <p>Delegates ingestion and run analytics directly to {@link AnalyticsPort}.
 * For evaluation analytics, applies the business rule of identifying the
 * best-performing prompt variant: the variant with the highest average overall
 * score across all its evaluations.  This determination is intentionally kept
 * in the domain service rather than the adapter so it can be tested and
 * reasoned about independently of persistence.
 *
 * <p>This class carries no Spring annotations — it is wired as a {@code @Bean}
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-07-05
 */
public class AnalyticsService implements GetAnalyticsUseCase {

    private final AnalyticsPort analyticsPort;

    public AnalyticsService(AnalyticsPort analyticsPort) {
        this.analyticsPort = analyticsPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IngestionAnalytics getIngestionAnalytics() {
        IngestionAnalytics result = analyticsPort.getIngestionAnalytics();
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunAnalytics getRunAnalytics() {
        RunAnalytics result = analyticsPort.getRunAnalytics();
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>After fetching raw aggregates from the port, iterates the variant
     * score list to find the variant with the highest {@code avgOverall} and
     * sets {@code bestVariantId} on the returned record.
     */
    @Override
    public EvaluationAnalytics getEvaluationAnalytics() {
        EvaluationAnalytics raw = analyticsPort.getEvaluationAnalytics();

        String bestVariantId = null;
        double bestScore = -1.0;
        for (VariantScore vs : raw.variantScores()) {
            if (vs.avgOverall() > bestScore) {
                bestScore = vs.avgOverall();
                bestVariantId = vs.variantId();
            }
        }

        EvaluationAnalytics result = new EvaluationAnalytics(
                raw.totalEvaluations(),
                raw.totalComparisons(),
                raw.variantScores(),
                bestVariantId);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CountByLabel> getDailyArticleCounts(int days) {
        List<CountByLabel> result = analyticsPort.getDailyArticleCounts(days);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CountByLabel> getTopicDistribution(int limit) {
        List<CountByLabel> result = analyticsPort.getTopicDistribution(limit);
        return result;
    }
}
