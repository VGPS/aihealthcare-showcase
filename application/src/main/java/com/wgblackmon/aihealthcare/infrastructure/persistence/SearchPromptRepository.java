package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link SearchPromptEntity}.
 *
 * <p>The engine identifier is the natural primary key, so all standard
 * {@code findById} / {@code save} / {@code findAll} operations are sufficient
 * — no custom query methods are required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
public interface SearchPromptRepository extends JpaRepository<SearchPromptEntity, String> {
}
