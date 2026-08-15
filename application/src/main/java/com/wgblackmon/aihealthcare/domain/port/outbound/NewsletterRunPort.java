package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving {@link NewsletterRun} records.
 *
 * <p>Implementations map between the immutable {@link NewsletterRun} domain
 * record and the underlying store (JPA entity in Slice 2; any other store in
 * future slices).  No JPA or Spring types appear in this interface — callers
 * remain fully decoupled from the persistence technology.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public interface NewsletterRunPort {

    /**
     * Persists a {@link NewsletterRun}.  If a run with the same {@code runId}
     * already exists it is overwritten (upsert semantics).
     *
     * @param run the run to persist; must not be {@code null}
     */
    void save(NewsletterRun run);

    /**
     * Retrieves a {@link NewsletterRun} by its primary key.
     *
     * @param runId the run identifier to look up; must not be blank
     * @return the matching run
     * @throws RunNotFoundException if no run exists for the given {@code runId}
     */
    NewsletterRun findByRunId(String runId);

    /**
     * Returns all persisted runs in insertion order.
     *
     * @return unmodifiable list of all runs; empty if none have been saved
     */
    List<NewsletterRun> findAll();

    /**
     * Returns the most recently generated newsletter run, or empty if none exist.
     *
     * @return the latest run by {@code generatedAt}, or empty
     */
    Optional<NewsletterRun> findLatest();
}
