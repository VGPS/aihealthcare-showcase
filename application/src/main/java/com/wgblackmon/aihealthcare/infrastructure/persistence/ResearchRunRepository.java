package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ResearchRunEntity}.
 *
 * <p>Provides standard CRUD operations for the {@code research_runs} table
 * plus a lookup by the string run ID primary key.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
public interface ResearchRunRepository extends JpaRepository<ResearchRunEntity, String> {

    /**
     * Looks up a research run entity by its string run ID.
     *
     * @param runId the UUID assigned at conduct time
     * @return the matching entity, or empty if not found
     */
    Optional<ResearchRunEntity> findByRunId(String runId);
}
