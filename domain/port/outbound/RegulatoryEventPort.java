package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link RegulatoryEvent} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 * Provides deduplication checks by reference number and source URL to prevent
 * storing duplicate regulatory events across harvest runs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-30
 */
public interface RegulatoryEventPort {

    /**
     * Saves (inserts or updates) a regulatory event.
     */
    void save(RegulatoryEvent event);

    /**
     * Saves multiple regulatory events in batch.
     */
    void saveAll(List<RegulatoryEvent> events);

    /**
     * Checks if a regulatory event with the given reference number already exists.
     * Used for deduplication of 510(k), De Novo, and PMA events.
     */
    boolean existsByReferenceNumber(String referenceNumber);

    /**
     * Checks if a regulatory event with the given source URL already exists.
     * Used for deduplication of CMS rules and announcements.
     */
    boolean existsBySourceUrl(String sourceUrl);

    /**
     * Returns the most recent regulatory events, ordered by discoveredAt descending.
     */
    List<RegulatoryEvent> findRecent(int limit);

    /**
     * Returns recent events filtered by event type.
     */
    List<RegulatoryEvent> findByType(RegulatoryEventType type, int limit);

    /**
     * Returns recent events filtered by regulatory body.
     */
    List<RegulatoryEvent> findByBody(RegulatoryBody body, int limit);

    /**
     * Finds a single regulatory event by its ID.
     */
    Optional<RegulatoryEvent> findById(String eventId);

    /**
     * Searches events whose title, summary, applicant, or device name
     * contain the given keyword (case-insensitive).
     */
    List<RegulatoryEvent> findByKeyword(String keyword, int limit);

    /**
     * Returns events where the applicant name contains the given company name
     * (case-insensitive). Used by company timeline views.
     */
    List<RegulatoryEvent> findByApplicant(String companyName, int limit);
}
