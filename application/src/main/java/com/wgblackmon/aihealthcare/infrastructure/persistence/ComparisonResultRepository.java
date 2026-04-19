package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ComparisonResultEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface ComparisonResultRepository extends JpaRepository<ComparisonResultEntity, String> {
}
