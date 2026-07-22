package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WatchlistMatchEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface WatchlistMatchRepository extends JpaRepository<WatchlistMatchEntity, String> {

    /**
     * Returns matches for the given item IDs, ordered by matchedOn descending.
     */
    List<WatchlistMatchEntity> findByItemIdInOrderByMatchedOnDesc(List<String> itemIds);

    /**
     * Returns matches for a single item, ordered by matchedOn descending.
     */
    List<WatchlistMatchEntity> findByItemIdOrderByMatchedOnDesc(String itemId);

    /**
     * Checks if a match already exists for the given item and article.
     */
    boolean existsByItemIdAndArticleId(String itemId, String articleId);
}
