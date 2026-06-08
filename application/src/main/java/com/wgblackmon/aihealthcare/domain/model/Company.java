package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing an AI-healthcare startup company
 * discovered from YC, TopStartups.io, or a hand-curated anchor list.
 *
 * <p>Holds the company's identity, source provenance, description text,
 * classification tags, and coarse AI/health flags. Used as the primary
 * data structure throughout the company discovery pipeline: scraping,
 * classification, deduplication, and newsletter markdown rendering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public record Company(
        String name,
        String source,
        String url,
        String companySite,
        String description,
        CompanyTags tags,
        boolean isAI,
        boolean isHealth
) {

    /**
     * Compact constructor — validates required fields.
     */
    public Company {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Company source must not be blank");
        }
        if (description == null) {
            description = "";
        }
        if (tags == null) {
            tags = CompanyTags.none();
        }
    }
}
