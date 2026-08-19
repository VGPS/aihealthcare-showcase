package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Indicates how much of a deal's financial terms have been publicly disclosed.
 *
 * <p>Used by {@link DealTerms} to qualify the completeness of deal economics
 * so analysts can assess whether the reported figures represent the full picture.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum DisclosedPortion {

    /** All material financial terms (upfront, milestones, equity, royalties) are public. */
    FULL,

    /** Some terms are public; others are withheld under confidentiality provisions. */
    PARTIAL,

    /** Deal value or structure is not publicly disclosed. */
    UNDISCLOSED
}
