package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link HealthcareAiCompany} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 * Deduplication by normalized name and domain is the caller's responsibility;
 * this port provides the lookup methods to support it.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public interface HealthcareAiCompanyPort {

    /**
     * Saves (inserts or updates) a healthcare AI company.
     */
    void save(HealthcareAiCompany company);

    /**
     * Finds a company by its normalized name.
     */
    Optional<HealthcareAiCompany> findByNameNormalized(String nameNormalized);

    /**
     * Finds a company by its website domain.
     */
    Optional<HealthcareAiCompany> findByDomain(String domain);

    /**
     * Returns true if a company with the given normalized name or domain exists.
     */
    boolean existsByNameOrDomain(String nameNormalized, String domain);

    /**
     * Returns all companies ordered by discoveredAt descending.
     */
    List<HealthcareAiCompany> findAll();
}
