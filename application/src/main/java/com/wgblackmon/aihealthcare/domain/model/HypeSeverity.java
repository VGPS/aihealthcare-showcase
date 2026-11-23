package com.wgblackmon.aihealthcare.domain.model;

/**
 * Severity tier for MARKETING_HYPE claims in the Frontier Claim Tracker.
 *
 * <p>Assigned by the LLM classifier only when {@link ClaimVerdict#MARKETING_HYPE}
 * is selected.  Null for all other verdicts.  SEVERE is the most actionable tier —
 * it indicates clinically-framed language that could mislead a care decision.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-24
 * @updated 2026-11-24
 */
public enum HypeSeverity {

    /** Generic positioning language with no clinical framing ("industry-leading AI platform"). */
    MILD,

    /** Specific capability or outcome claim without evidence, but not directly patient-facing
     *  ("reduces administrative burden by 50%"). */
    MODERATE,

    /** Clinically-framed claim without evidence where a clinician or patient could be misled
     *  into a care decision ("proven to detect cancer earlier", "reduces ICU mortality"). */
    SEVERE
}
