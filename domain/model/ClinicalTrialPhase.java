package com.wgblackmon.aihealthcare.domain.model;

/**
 * Phase of a clinical trial as reported on ClinicalTrials.gov.
 *
 * <p>Values align with the {@code phases} field returned by the
 * ClinicalTrials.gov v2 API. {@link #NOT_APPLICABLE} covers
 * observational studies and trials without a defined phase.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public enum ClinicalTrialPhase {
    EARLY_PHASE_1,
    PHASE_1,
    PHASE_2,
    PHASE_3,
    PHASE_4,
    NOT_APPLICABLE
}
