package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of a regulatory event outcome.
 *
 * <p>Tracks the progression of a regulatory submission from initial filing
 * through clearance/approval to market launch and CMS coverage decisions.
 * Not all events pass through every status — FDA 510(k) results arrive
 * pre-cleared, while CMS proposed rules start as {@code PENDING}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public enum RegulatoryOutcomeStatus {

    /** Submission is under review — no decision yet. */
    PENDING,

    /** FDA has cleared the device (510(k) substantially equivalent). */
    CLEARED,

    /** FDA has approved the device (PMA or De Novo). */
    APPROVED,

    /** FDA or CMS has denied the submission. */
    DENIED,

    /** Applicant withdrew the submission before a decision. */
    WITHDRAWN,

    /** Device is actively marketed post-clearance/approval. */
    MARKETED,

    /** CMS has issued a favorable coverage determination. */
    CMS_COVERED,

    /** CMS has issued a non-coverage determination. */
    CMS_NON_COVERED
}
