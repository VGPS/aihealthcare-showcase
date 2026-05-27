package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UsageRecordEntity}.
 *
 * <p>Uses the composite key {@link UsageRecordId} (email + yearMonth).
 * Provides finder methods for per-subscriber monthly usage lookups.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, UsageRecordId> {

    /**
     * Finds the usage record for a specific subscriber and month.
     *
     * @param email     Subscriber email address.
     * @param yearMonth Month in {@code "YYYY-MM"} format.
     * @return The matching entity, or empty if no record exists.
     */
    Optional<UsageRecordEntity> findByEmailAndYearMonth(String email, String yearMonth);

    /**
     * Returns all usage records for a given month.
     *
     * @param yearMonth Month in {@code "YYYY-MM"} format.
     * @return All matching entities; never {@code null}.
     */
    List<UsageRecordEntity> findAllByYearMonth(String yearMonth);
}
