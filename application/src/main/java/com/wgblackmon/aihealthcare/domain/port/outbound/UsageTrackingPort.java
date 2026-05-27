package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.UsageRecord;

import java.util.List;

/**
 * Outbound port for tracking per-subscriber monthly AI query usage.
 *
 * <p>Implementations live in {@code infrastructure.persistence}.  The domain
 * and application layers depend only on this interface — no JPA or storage
 * technology details leak inward.
 *
 * <p>Each subscriber has at most one usage record per calendar month, identified
 * by the {@code yearMonth} string (format {@code "YYYY-MM"}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
public interface UsageTrackingPort {

    /**
     * Retrieves the usage record for the given subscriber and month, creating a
     * new record with zero count if none exists.
     *
     * @param email     Subscriber email address.
     * @param yearMonth Month in {@code "YYYY-MM"} format.
     * @return The current (or newly created) usage record; never {@code null}.
     */
    UsageRecord getOrCreateUsage(String email, String yearMonth);

    /**
     * Atomically increments the query count for the given subscriber and month,
     * then returns the updated record.  If no record exists, one is created with
     * a count of 1.
     *
     * @param email     Subscriber email address.
     * @param yearMonth Month in {@code "YYYY-MM"} format.
     * @return The updated usage record with incremented count.
     */
    UsageRecord incrementAndGet(String email, String yearMonth);

    /**
     * Returns all usage records for a given month.  Useful for admin reporting.
     *
     * @param yearMonth Month in {@code "YYYY-MM"} format.
     * @return All records for the month; may be empty, never {@code null}.
     */
    List<UsageRecord> findAllByYearMonth(String yearMonth);
}
