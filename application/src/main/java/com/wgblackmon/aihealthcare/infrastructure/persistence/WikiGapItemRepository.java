package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiGapItemEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
public interface WikiGapItemRepository extends JpaRepository<WikiGapItemEntity, Long> {

    /**
     * Returns all gap items for a given run, ordered by ID ascending.
     *
     * @param runId the gap run ID
     * @return list of gap items for the run
     */
    List<WikiGapItemEntity> findByRunIdOrderByIdAsc(Long runId);

    /**
     * Returns all gap items with the given status (e.g. PENDING, APPROVED).
     *
     * @param status the item status
     * @return list of matching gap items
     */
    List<WikiGapItemEntity> findByStatus(String status);
}
