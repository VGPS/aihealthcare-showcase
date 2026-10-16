package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for {@link PipelinePauseEntity}, keyed by pipeline identifier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-16
 * @updated 2026-10-16
 */
public interface PipelinePauseRepository extends JpaRepository<PipelinePauseEntity, String> {
}
