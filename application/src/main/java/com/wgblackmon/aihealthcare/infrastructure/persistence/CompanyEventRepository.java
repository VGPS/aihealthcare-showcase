package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CompanyEventEntity}.
 *
 * <p>Provides CRUD operations and custom finders for company events
 * stored in the {@code company_events} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface CompanyEventRepository extends JpaRepository<CompanyEventEntity, String> {

    /**
     * Returns all events for a given company, ordered by occurredAt descending.
     */
    List<CompanyEventEntity> findByCompanySlugOrderByOccurredAtDesc(String companySlug);

    /**
     * Returns the most recent events across all companies.
     */
    List<CompanyEventEntity> findTop20ByOrderByDetectedAtDesc();
}
