package com.wgblackmon.aihealthcare.domain.marketanalysis;

/**
 * Stage in a regulatory rulemaking or guidance lifecycle.
 *
 * <p>The order is roughly chronological: from early stakeholder discussion through
 * final enforcement. A {@link RegulatoryTracker} carries the current stage so the
 * digest pipeline can flag upcoming comment deadlines and stage transitions.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public enum RulemakingStage {

    /** Regulator has published a discussion paper or concept release — no formal proposal yet. */
    DISCUSSION_PAPER,

    /** Formal notice of proposed rulemaking — public comment window is open. */
    COMMENT_PERIOD,

    /** Regulator has published draft guidance for review; comments may still be accepted. */
    DRAFT_GUIDANCE,

    /** Rule or guidance is finalized and binding; compliance clock has started. */
    FINAL_GUIDANCE,

    /** Regulator is actively enforcing the rule — penalties and audits in effect. */
    ENFORCEMENT
}
