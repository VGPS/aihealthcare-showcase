package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link TopicSummaryEntity}.
 *
 * <p>The primary key is the topic name ({@code String}), so
 * {@code findById(topic)} and {@code save(entity)} provide all required
 * operations without custom queries.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
public interface TopicSummaryRepository extends JpaRepository<TopicSummaryEntity, String> {
}
