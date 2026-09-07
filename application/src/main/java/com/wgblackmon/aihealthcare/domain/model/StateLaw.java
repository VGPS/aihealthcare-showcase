package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate root representing a single enacted (or tracked) U.S. state law
 * regulating AI in healthcare.
 *
 * <p>Each record is identified by a slug-style {@code id} (e.g. "ca-ab-3030")
 * and carries the full legislative metadata: bill number, title, enactment
 * year, effective date, regulated parties, key requirements, enforcement
 * provisions, and provenance sources.
 *
 * <p>A law may belong to multiple {@link LawCategory} values (e.g. a
 * comprehensive AI act that also addresses payer utilization review).
 * Sources track both official (government) and secondary (analysis) URLs
 * with content-change monitoring.
 *
 * @param id                 slug identifier (e.g. "ca-ab-3030")
 * @param stateCode          two-letter state code enum
 * @param stateName          full state name (e.g. "California")
 * @param billNumber         legislative bill number (e.g. "AB 3030")
 * @param title              short title or description of the law
 * @param yearEnacted        year the law was enacted
 * @param dateSigned         ISO date string when the law was signed (nullable)
 * @param dateSignedNote     explanatory note about the signing date (nullable)
 * @param effectiveDate      ISO date string when the law takes effect (nullable)
 * @param effectiveDateNote  explanatory note about the effective date (nullable)
 * @param status             current legislative status
 * @param statusDetail       additional detail about the status (nullable)
 * @param categories         subject-area classifications
 * @param regulatedParties   description of who the law regulates (nullable)
 * @param keyRequirements    summary of key requirements (nullable)
 * @param enforcement        enforcement mechanism description (nullable)
 * @param sources            provenance source references
 * @param notes              additional notes or context (nullable)
 * @param datasetVersion     version tag from the seed dataset
 * @param createdAt          when this record was first created
 * @param updatedAt          when this record was last updated
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public record StateLaw(
        String id,
        StateCode stateCode,
        String stateName,
        String billNumber,
        String title,
        int yearEnacted,
        String dateSigned,
        String dateSignedNote,
        String effectiveDate,
        String effectiveDateNote,
        LawStatus status,
        String statusDetail,
        List<LawCategory> categories,
        String regulatedParties,
        String keyRequirements,
        String enforcement,
        List<LawSource> sources,
        String notes,
        String datasetVersion,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Compact constructor — validates required fields and defensively copies lists.
     */
    public StateLaw {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (stateCode == null) {
            throw new IllegalArgumentException("stateCode must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        categories = categories != null ? List.copyOf(categories) : List.of();
        sources = sources != null ? List.copyOf(sources) : List.of();
    }
}
