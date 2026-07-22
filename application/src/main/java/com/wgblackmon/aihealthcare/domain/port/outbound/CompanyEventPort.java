package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;

import java.util.List;

/**
 * Outbound port for persisting and querying {@link CompanyEvent} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface CompanyEventPort {

    /**
     * Saves a company event.
     */
    void save(CompanyEvent event);

    /**
     * Finds all events for a given company, ordered by occurredAt descending.
     */
    List<CompanyEvent> findByCompanySlug(String slug);

    /**
     * Returns the most recent events across all companies.
     */
    List<CompanyEvent> findRecent(int limit);
}
