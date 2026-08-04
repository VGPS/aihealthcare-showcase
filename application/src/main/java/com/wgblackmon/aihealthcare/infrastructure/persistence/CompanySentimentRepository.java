package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CompanySentimentEntity}.
 *
 * <p>Uses the company slug as the natural primary key. The
 * {@link #findAllByOrderByAnalyzedAtDesc()} method returns all sentiments
 * with the most recently analyzed companies first.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public interface CompanySentimentRepository extends JpaRepository<CompanySentimentEntity, String> {

    /**
     * Returns all company sentiments ordered by analyzedAt descending.
     */
    List<CompanySentimentEntity> findAllByOrderByAnalyzedAtDesc();
}
