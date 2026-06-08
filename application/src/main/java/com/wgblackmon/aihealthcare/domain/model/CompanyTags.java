package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable value object representing AI-healthcare subcategory tags
 * for a discovered startup company.
 *
 * <p>Each boolean flag indicates whether the company's description matches
 * keyword heuristics for that subcategory. A company may match multiple
 * categories (e.g. both {@code scribe} and {@code agent}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public record CompanyTags(
        boolean scribe,
        boolean agent,
        boolean imaging,
        boolean rcm,
        boolean infra
) {

    /** All tags false — used as the default before classification. */
    public static CompanyTags none() {
        return new CompanyTags(false, false, false, false, false);
    }
}
