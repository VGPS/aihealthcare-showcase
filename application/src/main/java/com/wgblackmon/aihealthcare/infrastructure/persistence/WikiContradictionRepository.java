package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiContradictionEntity}.
 *
 * <p>Supports retrieval of recent contradictions for the "Reversal Watch"
 * newsletter section and wiki query operations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface WikiContradictionRepository extends JpaRepository<WikiContradictionEntity, Long> {

    /**
     * Finds contradictions detected after the given instant, ordered by
     * detection time (most recent first).
     *
     * @param since lower bound (exclusive) for detection time
     * @return contradictions ordered by detectedAt descending
     */
    List<WikiContradictionEntity> findByDetectedAtAfterOrderByDetectedAtDesc(Instant since);
}
