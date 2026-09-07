package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link NewBillCandidateEntity}.
 *
 * <p>Provides a derived query to find all unreviewed bill candidates,
 * ordered by discovery time descending (most recent first).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface NewBillCandidateRepository extends JpaRepository<NewBillCandidateEntity, Long> {

    List<NewBillCandidateEntity> findByReviewedFalseOrderByDiscoveredAtDesc();
}
