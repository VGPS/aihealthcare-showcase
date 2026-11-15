package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable domain record representing a single AI healthcare liability incident.
 *
 * <p>An incident is any lawsuit, FDA safety flag, wrongful-death complaint, or
 * regulatory sanction in which an AI product is alleged to have caused patient harm.
 * Records are manually seeded by an admin or (in Phase 4) auto-proposed by an LLM
 * classifier. The {@code defendant} field is the primary key for article linking —
 * the article-link query uses it as a keyword against harvested article titles.
 *
 * @param incidentId   unique identifier (UUID, caller-assigned)
 * @param incidentType legal/regulatory form of the incident
 * @param harmType     category of patient harm alleged
 * @param status       current lifecycle status
 * @param title        short display label (≤500 chars)
 * @param plaintiff    patient, family, or regulator bringing the action; nullable
 * @param defendant    AI vendor or health system named; used for article keyword linking
 * @param legalTheory  free-text legal theory (e.g. "Product liability"); nullable
 * @param court        court or regulatory body; nullable
 * @param caseNumber   docket or reference number; nullable
 * @param harmDate     date harm occurred; nullable
 * @param filingDate   date lawsuit filed or FDA notice issued; nullable
 * @param outcome      brief outcome description when resolved; nullable
 * @param sourceUrl    primary source URL; nullable
 * @param notes        free-text context for archival purposes; nullable
 * @param createdAt    record creation timestamp
 * @param updatedAt    record last-modified timestamp
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
public record LiabilityIncident(
        String incidentId,
        IncidentType incidentType,
        HarmType harmType,
        IncidentStatus status,
        String title,
        String plaintiff,
        String defendant,
        String legalTheory,
        String court,
        String caseNumber,
        LocalDate harmDate,
        LocalDate filingDate,
        String outcome,
        String sourceUrl,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    public LiabilityIncident {
        if (incidentId == null || incidentId.isBlank()) {
            throw new IllegalArgumentException("incidentId must not be blank");
        }
        if (incidentType == null) {
            throw new IllegalArgumentException("incidentType must not be null");
        }
        if (harmType == null) {
            throw new IllegalArgumentException("harmType must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (defendant == null || defendant.isBlank()) {
            throw new IllegalArgumentException("defendant must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
    }
}
