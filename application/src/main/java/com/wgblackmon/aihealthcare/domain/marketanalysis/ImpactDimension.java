package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * One of five dimensions along which an AI-healthcare news item's market impact is assessed.
 *
 * <p>The LLM classifier ({@link port.ImpactClassifierPort}) produces exactly one
 * {@link ImpactAssessment} per dimension for every {@link MarketDigestEntry}, with a
 * direction ({@link ImpactDirection}) and one-sentence rationale.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum ImpactDimension {

    /** Effect on near-term revenue recognition or top-line growth trajectory. */
    REVENUE,

    /** Effect on reported or adjusted earnings per share. */
    EARNINGS,

    /** Effect on company or sector valuation multiples. */
    VALUATION,

    /** Effect on institutional and retail investor sentiment or positioning. */
    INVESTOR_SENTIMENT,

    /** Effect on long-term growth prospects, pipeline, or competitive positioning. */
    FUTURE_GROWTH
}
