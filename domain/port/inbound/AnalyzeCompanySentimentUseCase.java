package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port — drives company sentiment analysis and retrieval.
 *
 * <p>The {@link #analyzeAll()} method triggers a full re-analysis of all
 * tracked company profiles against their recent article coverage.
 * Query methods expose the persisted results.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface AnalyzeCompanySentimentUseCase {

    /**
     * Runs sentiment analysis for all tracked company profiles.
     *
     * @return the computed sentiments (one per company with recent articles)
     */
    List<CompanySentiment> analyzeAll();

    /**
     * Returns the latest sentiment analysis for a given company.
     */
    Optional<CompanySentiment> getBySlug(String companySlug);

    /**
     * Returns all persisted company sentiments.
     */
    List<CompanySentiment> getAll();
}
