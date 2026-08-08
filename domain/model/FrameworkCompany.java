package com.wgblackmon.aihealthcare.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration record identifying a company whose healthcare AI framework
 * should be tracked for competitive analysis.
 *
 * <p>The {@code topics} list maps to feed topic names in the article corpus
 * (e.g. "Anthropic Healthcare"), allowing the analysis service to find all
 * relevant articles. Adding a new company to the analysis requires only
 * a new entry in {@code application.yml} under
 * {@code aihealthcare.frameworks.companies}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record FrameworkCompany(
        String slug,
        String name,
        String url,
        List<String> topics
) {

    /**
     * Compact constructor — validates required fields and defensively copies topics.
     */
    public FrameworkCompany {
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        topics = topics != null ? new ArrayList<>(topics) : new ArrayList<>();
    }
}
