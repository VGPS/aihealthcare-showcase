package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a persistent company intelligence profile.
 *
 * <p>Profiles accumulate articles, events, and trend data over time, creating
 * a wiki-like page for each company discovered by the pipeline. The slug serves
 * as the natural key (kebab-case, e.g. "tempus-ai").
 *
 * @param slug              kebab-case identifier (e.g. "tempus-ai")
 * @param name              company display name
 * @param url               company website URL
 * @param description       current company description
 * @param categories        classification tags from {@link CompanyTags}
 * @param articleIds        all linked {@link NewsArticle} IDs (accumulated over time)
 * @param firstDiscoveredAt when the company was first seen by the discovery pipeline
 * @param lastUpdatedAt     most recent profile update timestamp
 * @param articleCount      total number of linked articles
 * @param trendDirection    current trend from trend analysis (reuses TD enum)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record CompanyProfile(
        String slug,
        String name,
        String url,
        String description,
        List<String> categories,
        List<String> articleIds,
        Instant firstDiscoveredAt,
        Instant lastUpdatedAt,
        int articleCount,
        TrendDirection trendDirection
) {

    /**
     * Compact constructor — validates required fields and defensively copies lists.
     */
    public CompanyProfile {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (firstDiscoveredAt == null) {
            throw new IllegalArgumentException("firstDiscoveredAt must not be null");
        }
        if (lastUpdatedAt == null) {
            throw new IllegalArgumentException("lastUpdatedAt must not be null");
        }
        categories = categories == null ? List.of() : List.copyOf(categories);
        articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
    }
}
