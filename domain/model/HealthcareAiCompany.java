package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing an AI-in-healthcare company discovered
 * and enriched via the Perplexity API pipeline.
 *
 * <p>Contains rich company intelligence fields including funding stage,
 * estimated funding, founders, headquarters, and sector classification.
 * Each company is deduplicated by normalized name and domain before insertion.
 *
 * <p>Source provenance is tracked via {@code sourceUrls} (discovery citations)
 * and cross-validation status via {@code validated} and {@code validationSources}.
 *
 * @param companyId          UUID primary key
 * @param name               display name (e.g. "Tempus AI")
 * @param nameNormalized     lowercase, suffix-stripped name for dedup
 * @param domain             website domain (e.g. "tempus.com")
 * @param description        company description from Perplexity extraction
 * @param hqLocation         headquarters location (e.g. "Chicago, IL")
 * @param foundedYear        year founded (nullable if unknown)
 * @param sector             broad sector (e.g. "Healthcare AI")
 * @param subSector          specific focus (e.g. "clinical documentation")
 * @param fundingStage       latest funding stage (e.g. "Series C", "Public")
 * @param estimatedFunding   total funding as free-text (e.g. "$200M")
 * @param foundersJson       JSON array of founders: [{"name":"...","title":"..."}]
 * @param sourceUrls         Perplexity citation URLs from discovery
 * @param validated          whether cross-validation against curated lists passed
 * @param validationSources  URLs from validation calls (CB Insights, Rock Health)
 * @param discoveredAt       when first discovered by the pipeline
 * @param lastValidatedAt    most recent validation timestamp
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public record HealthcareAiCompany(
        String companyId,
        String name,
        String nameNormalized,
        String domain,
        String description,
        String hqLocation,
        Integer foundedYear,
        String sector,
        String subSector,
        String fundingStage,
        String estimatedFunding,
        String foundersJson,
        List<String> sourceUrls,
        boolean validated,
        List<String> validationSources,
        Instant discoveredAt,
        Instant lastValidatedAt
) {

    /**
     * Compact constructor — validates required fields and defensively copies lists.
     */
    public HealthcareAiCompany {
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalArgumentException("companyId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (nameNormalized == null || nameNormalized.isBlank()) {
            throw new IllegalArgumentException("nameNormalized must not be blank");
        }
        if (discoveredAt == null) {
            throw new IllegalArgumentException("discoveredAt must not be null");
        }
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
        validationSources = validationSources == null ? List.of() : List.copyOf(validationSources);
    }
}
