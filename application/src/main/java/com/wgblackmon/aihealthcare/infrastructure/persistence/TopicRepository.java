package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link TopicEntity}.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}.
 * The {@code topics} table is seeded via {@code data.sql} and the entity
 * defines the schema; no custom query methods are required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-05-25
 */
public interface TopicRepository extends JpaRepository<TopicEntity, Long> {
}
