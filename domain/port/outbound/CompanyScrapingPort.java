package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.Company;

import java.util.List;

/**
 * Outbound port for scraping startup companies from external directories.
 *
 * <p>Implementations fetch company data from sources such as Y Combinator,
 * TopStartups.io, and hand-curated anchor lists, returning raw
 * {@link Company} records with source provenance but without classification
 * tags applied (tags and flags are set downstream by the domain service).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public interface CompanyScrapingPort {

    /**
     * Scrapes all configured startup directory sources and returns
     * the raw company list. Tags and flags are not yet applied.
     *
     * @return unclassified companies from all sources
     */
    List<Company> scrapeAll();
}
