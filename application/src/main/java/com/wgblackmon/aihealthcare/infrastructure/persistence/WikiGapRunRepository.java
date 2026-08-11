package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiGapRunEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
public interface WikiGapRunRepository extends JpaRepository<WikiGapRunEntity, Long> {

    /**
     * Returns the 20 most recent gap analysis runs, newest first.
     *
     * @return list of recent runs
     */
    List<WikiGapRunEntity> findTop20ByOrderByStartedAtDesc();
}
