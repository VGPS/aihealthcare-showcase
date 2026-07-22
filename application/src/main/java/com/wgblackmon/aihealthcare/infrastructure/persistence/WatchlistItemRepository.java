package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WatchlistItemEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface WatchlistItemRepository extends JpaRepository<WatchlistItemEntity, String> {

    /**
     * Returns all watchlist items for a given user, ordered by createdAt descending.
     */
    List<WatchlistItemEntity> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    /**
     * Deletes a watchlist item by ID and user email (ownership check).
     */
    void deleteByItemIdAndUserEmail(String itemId, String userEmail);
}
