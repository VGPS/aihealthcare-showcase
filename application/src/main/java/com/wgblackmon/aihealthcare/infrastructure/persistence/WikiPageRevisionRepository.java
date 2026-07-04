package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiPageRevisionEntity}.
 *
 * <p>Supports retrieval of revision history for audit trail purposes.
 * Every wiki page update creates a new revision preserving the content
 * at that point in time.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface WikiPageRevisionRepository extends JpaRepository<WikiPageRevisionEntity, Long> {

    /**
     * Finds all revisions for a given page slug, ordered by revision
     * number descending (newest first).
     *
     * @param pageSlug the slug of the wiki page
     * @return revisions ordered by revision descending
     */
    List<WikiPageRevisionEntity> findByPageSlugOrderByRevisionDesc(String pageSlug);
}
