package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TopicEntity}.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus a name-based lookup used when resolving topic context for AI prompts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    /**
     * Finds a topic by its human-readable name.
     *
     * @param name the topic name (e.g. "AI Healthcare")
     * @return the matching entity, or empty if not found
     */
    Optional<TopicEntity> findByName(String name);
}
