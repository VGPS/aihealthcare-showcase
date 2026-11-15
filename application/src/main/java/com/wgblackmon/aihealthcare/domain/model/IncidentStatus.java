package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of an AI healthcare liability incident.
 *
 * <p>PENDING_REVIEW is an admin workflow state: the incident was proposed by the
 * LLM classifier and is awaiting human approval before appearing in the public registry.
 * FILED means the complaint or notice has been lodged but no hearing has begun.
 * ACTIVE means the case is in discovery, briefing, or trial. SETTLED means the
 * parties reached a private resolution. DISMISSED means the case was thrown out.
 * DECIDED means a final judicial or regulatory ruling has been issued.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-11-16
 * @updated 2026-11-15
 */
public enum IncidentStatus {
    PENDING_REVIEW("Pending Review"),
    FILED("Filed"),
    ACTIVE("Active"),
    SETTLED("Settled"),
    DISMISSED("Dismissed"),
    DECIDED("Decided");

    private final String displayName;

    IncidentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
