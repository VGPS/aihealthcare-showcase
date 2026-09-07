package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link StateLaw} records
 * in the state health-AI legislation registry.
 *
 * <p>Implementations live in the infrastructure persistence layer.
 * The {@link #upsert(StateLaw)} method performs an insert-or-update
 * keyed on the law's slug id, supporting idempotent seed loading.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface StateLawPort {

    /**
     * Inserts or updates a state law record (keyed on {@code law.id()}).
     */
    void upsert(StateLaw law);

    /**
     * Returns the law with the given slug id.
     */
    Optional<StateLaw> findById(String id);

    /**
     * Returns all laws in the registry, ordered by state code then year enacted.
     */
    List<StateLaw> findAll();

    /**
     * Returns all laws for a given state.
     */
    List<StateLaw> findByState(StateCode stateCode);

    /**
     * Returns all laws matching a given category.
     */
    List<StateLaw> findByCategory(LawCategory category);

    /**
     * Returns all laws with a given status.
     */
    List<StateLaw> findByStatus(LawStatus status);

    /**
     * Returns laws with effective dates between the given ISO date strings (inclusive).
     *
     * @param from ISO date string (e.g. "2026-01-01")
     * @param to   ISO date string (e.g. "2026-12-31")
     */
    List<StateLaw> findEffectiveBetween(String from, String to);

    /**
     * Full-text search across law titles, bill numbers, key requirements,
     * and notes. Returns matching laws ordered by relevance.
     */
    List<StateLaw> search(String query);

    /**
     * Updates the monitoring fields on a single source record identified by
     * law ID and URL. Sets the last-fetched timestamp, content hash, HTTP
     * status, and changed-since-last-review flag.
     *
     * @param lawId         the slug identifier of the law
     * @param sourceUrl     the source URL to update
     * @param fetchedAt     when the URL was fetched
     * @param contentHash   SHA-256 hex digest of the response body (nullable)
     * @param httpStatus    HTTP status code of the fetch attempt (nullable)
     * @param changed       whether the content hash changed since the last check
     */
    void updateSourceMonitoringFields(String lawId, String sourceUrl,
                                       java.time.Instant fetchedAt, String contentHash,
                                       Integer httpStatus, boolean changed);
}
