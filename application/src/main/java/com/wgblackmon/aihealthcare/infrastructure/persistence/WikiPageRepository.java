package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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
 * @updated 2026-08-23
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

    /**
     * Finds wiki pages matching both a keyword search and a page type filter.
     *
     * @param keyword  search term (case-insensitive, matched against title/tags/content)
     * @param pageType the page type string (e.g. "ENTITY", "CONCEPT")
     * @return matching pages ordered by title
     */
    @Query("SELECT w FROM WikiPageEntity w WHERE w.pageType = :pageType AND " +
           "(LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.contentMarkdown) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY w.title")
    List<WikiPageEntity> searchByKeywordAndPageType(String keyword, String pageType);

    // ------------------------------------------------------------------
    // Index projections — exclude content_markdown to avoid transferring
    // 833 KB/request of markdown that the index listing never displays.
    // ------------------------------------------------------------------

    /** All pages, index columns only. */
    List<WikiPageIndexView> findAllBy();

    /** Pages of a given type, index columns only. */
    List<WikiPageIndexView> findAllByPageType(String pageType);

    /** Keyword search (title/tags/content), index columns only. */
    @Query("SELECT w.slug AS slug, w.title AS title, w.pageType AS pageType, w.tags AS tags, " +
           "w.relatedSlugs AS relatedSlugs, w.createdAt AS createdAt, w.updatedAt AS updatedAt, " +
           "w.revision AS revision " +
           "FROM WikiPageEntity w WHERE LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.contentMarkdown) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY w.title")
    List<WikiPageIndexView> searchByKeywordForIndex(String keyword);

    /** Keyword + type filter search, index columns only. */
    @Query("SELECT w.slug AS slug, w.title AS title, w.pageType AS pageType, w.tags AS tags, " +
           "w.relatedSlugs AS relatedSlugs, w.createdAt AS createdAt, w.updatedAt AS updatedAt, " +
           "w.revision AS revision " +
           "FROM WikiPageEntity w WHERE w.pageType = :pageType AND " +
           "(LOWER(w.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(w.contentMarkdown) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY w.title")
    List<WikiPageIndexView> searchByKeywordAndPageTypeForIndex(String keyword, String pageType);

    /**
     * Finds wiki pages created after the given instant (newest first).
     *
     * @param since lower bound (exclusive) for creation time
     * @return recently created pages
     */
    List<WikiPageEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since);

    /**
     * Finds wiki pages updated after the given instant (newest first).
     * Excludes pages that were only created (not subsequently updated).
     *
     * @param since lower bound (exclusive) for update time
     * @return recently updated pages
     */
    List<WikiPageEntity> findByUpdatedAtAfterAndRevisionGreaterThanOrderByUpdatedAtDesc(Instant since, int minRevision);
}
