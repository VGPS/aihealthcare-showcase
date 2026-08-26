package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for browsing the public AI healthcare company directory.
 *
 * <p>Provides read-only access to discovered and validated AI healthcare
 * companies. Intended for the public-facing company directory at {@code /companies}.
 * Does not require authentication — all results are visible to anonymous visitors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-26
 * @updated 2026-08-26
 */
public interface BrowseCompaniesUseCase {

    /**
     * Returns all known AI healthcare companies sorted alphabetically by name.
     */
    List<HealthcareAiCompany> listCompanies();

    /**
     * Returns the company whose URL slug matches the given value, or empty if not found.
     *
     * @param slug  URL-safe slug derived from the company name (e.g. "grelin-health")
     */
    Optional<HealthcareAiCompany> getCompany(String slug);
}
