package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link ResearchRun} records.
 *
 * <p>One {@code ResearchRun} is saved at the end of every successful
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase#conduct}
 * call, giving operators a queryable audit trail of all research activity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
public interface ResearchRunPort {

    /**
     * Persists a research run record.  No-op if a run with the same
     * {@code runId} already exists.
     *
     * @param run the run to persist; must not be {@code null}
     */
    void save(ResearchRun run);

    /**
     * Returns all persisted research runs, ordered by {@code researchedAt}
     * descending (most recent first).
     *
     * @return immutable list; never {@code null}; may be empty
     */
    List<ResearchRun> findAll();

    /**
     * Looks up a single run by its ID.
     *
     * @param runId the UUID assigned at conduct time
     * @return the matching run, or empty if not found
     */
    Optional<ResearchRun> findByRunId(String runId);
}
