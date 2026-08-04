package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting and querying {@link WatchlistMatch} records.
 *
 * <p>Implementations live in the infrastructure layer (e.g. JPA adapter).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface WatchlistMatchPort {

    /**
     * Saves a single watchlist match.
     */
    void save(WatchlistMatch match);

    /**
     * Saves multiple watchlist matches in batch.
     */
    void saveAll(List<WatchlistMatch> matches);

    /**
     * Returns recent matches for watchlist items owned by the given user.
     *
     * @param email user email
     * @param limit maximum number of matches to return
     * @return matches ordered by matchedOn descending
     */
    List<WatchlistMatch> findByUser(String email, int limit);

    /**
     * Returns matches for a specific watchlist item.
     *
     * @param itemId watchlist item ID
     * @param limit  maximum number of matches to return
     * @return matches ordered by matchedOn descending
     */
    List<WatchlistMatch> findByItem(String itemId, int limit);

    /**
     * Returns matches for the user's watchlist items where matchedOn is
     * at or after the given timestamp, ordered by matchedOn descending.
     *
     * @param email user email
     * @param since cutoff timestamp (inclusive)
     * @return matches since the given time
     */
    List<WatchlistMatch> findByUserSince(String email, Instant since);

    /**
     * Checks if a match already exists for the given item and article.
     * Used for deduplication during batch matching.
     */
    boolean existsByItemAndArticle(String itemId, String articleId);
}
