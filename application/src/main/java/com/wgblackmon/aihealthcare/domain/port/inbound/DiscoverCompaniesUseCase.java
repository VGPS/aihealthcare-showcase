package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;

/**
 * Inbound port for the company discovery pipeline.
 *
 * <p>Drives the end-to-end flow: scrape startup directories, classify
 * companies by AI-healthcare subcategory, deduplicate, persist as
 * articles, and generate newsletter markdown.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public interface DiscoverCompaniesUseCase {

    /**
     * Runs the full company discovery pipeline.
     *
     * @return the discovery result containing companies, markdown, and counts
     */
    CompanyDiscoveryResult discover();
}
