package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for LLM-powered extraction and classification of frontier AI
 * company claims from harvested articles.
 *
 * <p>The adapter implementation in {@code infrastructure.ai} sends article
 * content to the LLM in batches and parses the structured response into
 * {@link FrontierClaim} records with assigned {@link com.wgblackmon.aihealthcare.domain.model.ClaimType}
 * and {@link com.wgblackmon.aihealthcare.domain.model.ClaimVerdict} values.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public interface ClaimClassifierPort {

    /**
     * Extracts and classifies all frontier AI company claims found in the
     * supplied articles.
     *
     * @param articles articles to analyse — typically up to 10 per LLM call
     * @return zero or more classified claims; never null
     */
    List<FrontierClaim> classifyClaims(List<NewsArticle> articles);

    /**
     * Cross-checks {@code newClaims} against {@code priorClaims} for the same
     * company and returns the new claims with verdicts upgraded to
     * {@link com.wgblackmon.aihealthcare.domain.model.ClaimVerdict#CONTRADICTED}
     * where a conflict is detected.
     *
     * @param newClaims   freshly classified claims (all from the same company)
     * @param priorClaims existing claims from the past 90 days for that company
     * @return new claims list, verdicts updated in place for contradictions
     */
    List<FrontierClaim> detectContradictions(List<FrontierClaim> newClaims,
                                              List<FrontierClaim> priorClaims);
}
