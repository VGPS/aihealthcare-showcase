package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link LlmCallUsageEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface LlmCallUsageRepository extends JpaRepository<LlmCallUsageEntity, Long> {

    List<LlmCallUsageEntity> findByOccurredAtAfterOrderByOccurredAtDesc(Instant since);
}
