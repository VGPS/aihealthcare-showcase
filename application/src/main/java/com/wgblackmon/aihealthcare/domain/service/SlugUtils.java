package com.wgblackmon.aihealthcare.domain.service;

/**
 * Canonical slug generation for company names and similar identifiers.
 *
 * Produces URL-safe kebab-case slugs: lowercase, non-alphanumeric runs
 * collapsed to a single hyphen, leading/trailing hyphens stripped.
 *
 * Every service that needs a slug must use this class to avoid cross-reference
 * mismatches between services that write slugs (persistence adapters) and
 * services that read them back (enrichment, signal scoring, controllers).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public final class SlugUtils {

    private SlugUtils() {}

    /**
     * Converts a display name to a URL-safe slug.
     * "Tempus AI, Inc." → "tempus-ai-inc"
     * "R1 / RCM"        → "r1-rcm"
     * "Health+AI"        → "health-ai"
     */
    public static String toSlug(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
