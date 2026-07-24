package com.wgblackmon.aihealthcare.domain.model;

/**
 * Overall recruitment status of a clinical trial as reported on
 * ClinicalTrials.gov.
 *
 * <p>Values align with the {@code overallStatus} field returned by
 * the ClinicalTrials.gov v2 API. {@link #UNKNOWN} is the fallback
 * for unrecognised or missing status values.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public enum ClinicalTrialStatus {
    RECRUITING,
    ACTIVE_NOT_RECRUITING,
    COMPLETED,
    TERMINATED,
    WITHDRAWN,
    NOT_YET_RECRUITING,
    SUSPENDED,
    ENROLLING_BY_INVITATION,
    UNKNOWN
}
