package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Regulatory jurisdiction for a rulemaking or guidance action.
 *
 * <p>Used by {@link RegulatoryTracker} to identify which authority issued the rule,
 * enabling the market digest to surface jurisdiction-relevant signals to subscribers
 * (e.g. EU AI Act deadlines for European-market participants).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum Jurisdiction {

    /** U.S. Food and Drug Administration — device approval and AI/ML guidance. */
    US_FDA,

    /** European Union AI Act — risk-based classification for AI systems including medical devices. */
    EU_AI_ACT,

    /** U.K. Medicines and Healthcare products Regulatory Agency. */
    UK_MHRA,

    /** U.S. state-level AI or digital health regulation (specify state in docketId). */
    US_STATE,

    /** International or other jurisdiction not covered by the named values. */
    OTHER
}
