package com.wgblackmon.aihealthcare.domain.model;

/**
 * Verdict assigned by the LLM classifier to a frontier AI company claim,
 * representing the quality and verifiability of the evidence supporting it.
 *
 * <p>Verdicts range from peer-reviewed evidence (EVIDENCE_BACKED) through
 * unverified assertions (ALLEGED_UNVERIFIED), marketing superlatives without
 * data (MARKETING_HYPE), claims that conflict with other evidence (CONTRADICTED),
 * and claims the company itself has since walked back (RETRACTED).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
public enum ClaimVerdict {

    /** Claim is supported by peer-reviewed research, independent benchmarks, or verified audits. */
    EVIDENCE_BACKED,

    /** Claim is stated without citation, independent verification, or supporting data. */
    ALLEGED_UNVERIFIED,

    /** Claim uses superlatives or sweeping performance assertions without any substantiating data. */
    MARKETING_HYPE,

    /** Claim directly conflicts with other sourced, credible evidence. */
    CONTRADICTED,

    /** Company has explicitly walked back, corrected, or retracted the claim. */
    RETRACTED
}
