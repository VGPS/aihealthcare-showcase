package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link LawChangeEventEntity}.
 *
 * <p>Provides a derived query to find all unreviewed change events,
 * ordered by detection time descending (most recent first).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface LawChangeEventRepository extends JpaRepository<LawChangeEventEntity, Long> {

    List<LawChangeEventEntity> findByReviewedFalseOrderByDetectedAtDesc();
}
