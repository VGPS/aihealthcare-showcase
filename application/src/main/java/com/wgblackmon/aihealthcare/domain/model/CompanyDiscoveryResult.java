package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable result record returned by the company discovery pipeline.
 *
 * <p>Contains the final deduplicated and classified list of companies,
 * the generated newsletter markdown, and summary counts for diagnostics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public record CompanyDiscoveryResult(
        List<Company> companies,
        String markdown,
        int totalScraped,
        int afterDedup,
        int aiHealthFiltered
) {
}
