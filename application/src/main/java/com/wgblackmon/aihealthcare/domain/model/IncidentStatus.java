package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of an AI healthcare liability incident.
 *
 * <p>FILED means the complaint or notice has been lodged but no hearing has begun.
 * ACTIVE means the case is in discovery, briefing, or trial. SETTLED means the
 * parties reached a private resolution. DISMISSED means the case was thrown out.
 * DECIDED means a final judicial or regulatory ruling has been issued.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public enum IncidentStatus {
    FILED,
    ACTIVE,
    SETTLED,
    DISMISSED,
    DECIDED
}
