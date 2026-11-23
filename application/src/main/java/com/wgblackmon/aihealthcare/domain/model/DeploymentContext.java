package com.wgblackmon.aihealthcare.domain.model;

/**
 * Deployment context for claims in the Frontier Claim Tracker.
 *
 * <p>Categorises the operational domain in which the claimed capability
 * is intended to be deployed.  Assigned by the LLM classifier for all
 * claim types.  CLINICAL is the highest-risk context — claims made in
 * direct patient-care settings warrant closer scrutiny.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-24
 * @updated 2026-11-24
 */
public enum DeploymentContext {

    /** Direct patient care, diagnosis, treatment decisions, or clinical documentation. */
    CLINICAL,

    /** Hospital administration, billing, scheduling, staffing, or supply chain. */
    OPERATIONAL,

    /** Drug discovery, clinical trial design, or genomics research platforms. */
    RESEARCH,

    /** No specific deployment context stated. */
    GENERAL
}
