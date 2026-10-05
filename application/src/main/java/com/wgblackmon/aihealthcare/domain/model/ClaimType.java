package com.wgblackmon.aihealthcare.domain.model;

/**
 * Categorizes the type of claim made by a frontier AI company.
 *
 * <p>Used by the Frontier Claim Tracker to group claims for filtering
 * and trend analysis. Each value represents a distinct domain in which
 * AI companies routinely make public assertions about their products.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public enum ClaimType {

    /** Claims about model capabilities, accuracy, or general performance. */
    CAPABILITY_CLAIM,

    /** Claims about safety, alignment, risk mitigation, or harm reduction. */
    SAFETY_CLAIM,

    /** Claims referencing benchmark results, leaderboard rankings, or evaluation scores. */
    BENCHMARK_CLAIM,

    /** Claims about product release timelines, roadmap milestones, or availability. */
    TIMELINE_CLAIM,

    /** Claims about regulatory approvals, compliance certifications, or government clearances. */
    REGULATORY_CLAIM,

    /** Claims about partnerships, integrations, or enterprise deployments. */
    PARTNERSHIP_CLAIM
}
