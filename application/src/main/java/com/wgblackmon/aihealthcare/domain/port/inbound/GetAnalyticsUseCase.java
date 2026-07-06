package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;

import java.util.List;

/**
 * Inbound port for the analytics dashboard use case.
 *
 * <p>Provides three read-only query operations covering the three main
 * analytics views: article ingestion, newsletter run production, and
 * prompt evaluation performance.  All methods are side-effect-free.
 *
 * <p>The web layer ({@link com.wgblackmon.aihealthcare.web.controller.AnalyticsController})
 * drives this port; the implementation is
 * {@link com.wgblackmon.aihealthcare.domain.service.AnalyticsService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-07-05
 */
public interface GetAnalyticsUseCase {

    /**
     * Returns aggregate statistics about harvested articles.
     *
     * @return ingestion analytics (total, by tier, by topic, recency windows)
     */
    IngestionAnalytics getIngestionAnalytics();

    /**
     * Returns aggregate statistics about newsletter runs.
     *
     * @return run analytics (total, by status, most recent timestamp)
     */
    RunAnalytics getRunAnalytics();

    /**
     * Returns aggregate statistics about prompt evaluations, including
     * per-variant score summaries and the best-performing variant ID.
     *
     * @return evaluation analytics
     */
    EvaluationAnalytics getEvaluationAnalytics();

    /**
     * Returns daily article counts for the last {@code days} days,
     * with zero-count days included.
     *
     * @param days number of days to look back
     * @return list of [date, count] entries ordered chronologically
     */
    List<CountByLabel> getDailyArticleCounts(int days);

    /**
     * Returns article counts grouped by topic, limited to the top
     * {@code limit} topics.
     *
     * @param limit maximum number of topics to return
     * @return list of [topic, count] entries ordered by count descending
     */
    List<CountByLabel> getTopicDistribution(int limit);
}
