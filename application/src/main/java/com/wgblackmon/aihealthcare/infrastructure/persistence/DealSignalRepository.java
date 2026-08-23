package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DealSignalEntity}.
 *
 * <p>Provides two flavours of list queries:
 * <ul>
 *   <li>Full-entity methods ({@code findAllByOrderByDetectedAtDesc}, {@code findBySignalTypeOrderByDetectedAtDesc})
 *       — kept for backward compatibility but no longer called by {@link DealSignalAdapter}
 *       for list pages.
 *   <li>Projection methods ({@code findRecentListView}, {@code findBySignalTypeListView})
 *       — return {@link DealSignalListView} which excludes the {@code llm_analysis} TEXT column,
 *       reducing per-request data transfer by skipping 1 000+ chars of LLM output per row.
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-04
 * @updated 2026-08-23
 */
public interface DealSignalRepository extends JpaRepository<DealSignalEntity, String> {

    List<DealSignalEntity> findAllByOrderByDetectedAtDesc(Pageable pageable);

    boolean existsByArticleId(String articleId);

    List<DealSignalEntity> findBySignalTypeOrderByDetectedAtDesc(String signalType, Pageable pageable);

    /**
     * Returns recent deal signals as a lightweight projection, ordered newest-first.
     * Excludes {@code llm_analysis} and {@code article_id} columns.
     *
     * @param pageable page size and offset (use {@code PageRequest.of(0, limit)} for top-N)
     * @return list of projections; empty if none found
     */
    @Query("SELECT d FROM DealSignalEntity d ORDER BY d.detectedAt DESC")
    List<DealSignalListView> findRecentListView(Pageable pageable);

    /**
     * Returns deal signals of the given type as a lightweight projection, ordered newest-first.
     *
     * @param signalType the signal type string (e.g. "FUNDING")
     * @param pageable   page size and offset
     * @return filtered list of projections; empty if none found
     */
    @Query("SELECT d FROM DealSignalEntity d WHERE d.signalType = :signalType ORDER BY d.detectedAt DESC")
    List<DealSignalListView> findBySignalTypeListView(@Param("signalType") String signalType, Pageable pageable);
}
