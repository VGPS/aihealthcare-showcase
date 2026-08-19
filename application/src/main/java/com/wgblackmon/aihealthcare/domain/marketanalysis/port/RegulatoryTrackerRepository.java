package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link RegulatoryTracker} records.
 *
 * <p>The {@link #upsert} operation uses the {@code docketId + jurisdiction} composite
 * key — if a tracker for the same docket already exists it is replaced, otherwise a
 * new row is inserted. This keeps the table as a current-state view of each active
 * rulemaking, not an append-only audit log.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface RegulatoryTrackerRepository {

    /**
     * Inserts or updates the tracker keyed on {@code docketId + jurisdiction}.
     *
     * @param tracker the current tracker snapshot (non-null)
     */
    void upsert(RegulatoryTracker tracker);

    /**
     * Returns the current tracker for the given docket and jurisdiction, or empty.
     *
     * @param docketId    official docket reference number
     * @param jurisdiction issuing regulatory body
     */
    Optional<RegulatoryTracker> findByDocketId(String docketId,
            com.wgblackmon.aihealthcare.domain.marketanalysis.Jurisdiction jurisdiction);

    /**
     * Returns all trackers whose {@code commentDeadline} falls on or before
     * {@code deadlineOnOrBefore} (i.e. approaching within the caller-specified window).
     * Never returns trackers with a null deadline.
     *
     * @param deadlineOnOrBefore upper bound for the comment deadline (inclusive)
     */
    List<RegulatoryTracker> findApproachingDeadlines(LocalDate deadlineOnOrBefore);

    /**
     * Returns all currently tracked regulatory actions, ordered by {@code lastUpdatedAt}
     * descending.
     */
    List<RegulatoryTracker> findAll();
}
