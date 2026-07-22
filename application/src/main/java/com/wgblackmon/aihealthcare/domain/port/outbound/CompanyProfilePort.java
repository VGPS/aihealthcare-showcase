package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link CompanyProfile} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface CompanyProfilePort {

    /**
     * Saves (inserts or updates) a company profile.
     */
    void save(CompanyProfile profile);

    /**
     * Finds a company profile by its slug.
     */
    Optional<CompanyProfile> findBySlug(String slug);

    /**
     * Returns all company profiles ordered by article count descending.
     */
    List<CompanyProfile> findAll();

    /**
     * Returns all company profiles matching the given category.
     */
    List<CompanyProfile> findByCategory(String category);
}
