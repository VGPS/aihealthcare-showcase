package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link EntryEmbeddingCacheEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
public interface EntryEmbeddingCacheJpaRepository
        extends JpaRepository<EntryEmbeddingCacheEntity, String> {
}
