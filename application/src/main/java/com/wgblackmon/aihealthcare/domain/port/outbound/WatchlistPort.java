package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link WatchlistItem} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface WatchlistPort {

    /**
     * Saves (inserts or updates) a watchlist item.
     */
    void save(WatchlistItem item);

    /**
     * Deletes a watchlist item by ID, scoped to the owning user.
     */
    void delete(String itemId, String userEmail);

    /**
     * Returns all watchlist items for a given user, ordered by createdAt descending.
     */
    List<WatchlistItem> findByUser(String email);

    /**
     * Returns all watchlist items across all users.
     * Used by the batch matching scheduler.
     */
    List<WatchlistItem> findAll();

    /**
     * Finds a single watchlist item by its ID.
     */
    Optional<WatchlistItem> findById(String itemId);
}
