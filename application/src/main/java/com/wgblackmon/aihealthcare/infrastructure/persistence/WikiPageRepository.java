package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiPageEntity}.
 *
 * <p>The slug is the natural primary key — no auto-generated IDs.
 * Query methods support the wiki query adapter's search and retrieval operations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface WikiPageRepository extends JpaRepository<WikiPageEntity, String> {

    /**
     * Finds wiki pages whose title, tags, or content contain the given keyword
     * (case-insensitive).  Phase 1 text search — will be replaced by pgvector
     * semantic similarity in a future slice.
     *
     * @param keyword search term
     * @return matching pages ordered by title
     */
    @Query("SELECT w FROM WikiPageEntity w WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.contentMarkdown) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY w.title")
    List<WikiPageEntity> searchByKeyword(String keyword);

    /**
     * Finds all wiki pages of the given type.
     *
     * @param pageType the page type string (e.g. "ENTITY", "CONCEPT")
     * @return matching pages
     */
    List<WikiPageEntity> findByPageType(String pageType);
}
