package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;

/**
 * Outbound port for querying aggregate analytics data from the persistence layer.
 *
 * <p>Implementations live in {@code infrastructure/persistence} and issue
 * aggregate SQL/JPQL queries against the existing JPA repositories.
 * The domain layer depends only on this interface — never on Spring Data or JPA.
 *
 * <p>Note: {@link EvaluationAnalytics#bestVariantId()} is always {@code null}
 * in the value returned from this port; the
 * {@link com.wgblackmon.aihealthcare.domain.service.AnalyticsService} applies
 * the business rule for determining the best variant.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
public interface AnalyticsPort {

    /**
     * Returns aggregate statistics about ingested articles.
     *
     * @return article counts by tier, topic, and recency window
     */
    IngestionAnalytics getIngestionAnalytics();

    /**
     * Returns aggregate statistics about newsletter runs.
     *
     * @return run counts by status and most-recent run timestamp
     */
    RunAnalytics getRunAnalytics();

    /**
     * Returns aggregate evaluation statistics with per-variant score summaries.
     * The {@code bestVariantId} field on the returned record is always {@code null};
     * the service layer computes it.
     *
     * @return evaluation counts and per-variant score averages
     */
    EvaluationAnalytics getEvaluationAnalytics();
}
