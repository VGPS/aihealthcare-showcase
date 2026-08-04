package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link FrameworkAnalysisEntity}.
 *
 * <p>Uses the company slug as the natural primary key. The
 * {@link #findAllByOrderByOverallScoreDesc()} method returns all framework
 * analyses with the highest-scoring companies first.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface FrameworkAnalysisRepository extends JpaRepository<FrameworkAnalysisEntity, String> {

    /**
     * Returns all framework analyses ordered by overall score descending.
     */
    List<FrameworkAnalysisEntity> findAllByOrderByOverallScoreDesc();
}
