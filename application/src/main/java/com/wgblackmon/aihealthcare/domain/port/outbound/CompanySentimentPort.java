package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link CompanySentiment} snapshots.
 *
 * <p>Implementations live in the infrastructure persistence layer. Each saved
 * record represents a point-in-time sentiment analysis for a single company.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface CompanySentimentPort {

    /**
     * Saves a company sentiment snapshot (insert or update by slug).
     */
    void save(CompanySentiment sentiment);

    /**
     * Returns the most recent sentiment for the given company slug.
     */
    Optional<CompanySentiment> findBySlug(String companySlug);

    /**
     * Returns all company sentiments, ordered by analyzedAt descending.
     */
    List<CompanySentiment> findAll();
}
