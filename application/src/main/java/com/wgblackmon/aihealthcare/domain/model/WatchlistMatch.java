package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a match between a {@link WatchlistItem}
 * and a {@link NewsArticle}.
 *
 * <p>Created during the harvest pipeline when an incoming article's title or
 * body text matches a subscriber's watchlist item. The {@code snippet} field
 * contains a short excerpt showing the matched context.
 *
 * @param matchId   unique identifier (UUID)
 * @param itemId    FK to the matched {@link WatchlistItem}
 * @param articleId FK to the matched {@link NewsArticle}
 * @param matchedOn when the match was detected
 * @param snippet   short excerpt showing match context (nullable)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record WatchlistMatch(
        String matchId,
        String itemId,
        String articleId,
        Instant matchedOn,
        String snippet
) {

    /** Compact constructor — validates required fields. */
    public WatchlistMatch {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (articleId == null || articleId.isBlank()) {
            throw new IllegalArgumentException("articleId must not be blank");
        }
        if (matchedOn == null) {
            throw new IllegalArgumentException("matchedOn must not be null");
        }
    }
}
