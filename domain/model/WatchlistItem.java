package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a single item on a subscriber's
 * personal watchlist.
 *
 * <p>Each item tracks a company, keyword, or topic that the subscriber
 * wants to monitor. Incoming articles are matched against watchlist items
 * during the harvest pipeline; matches are stored as {@link WatchlistMatch}
 * records.
 *
 * @param itemId    unique identifier (UUID)
 * @param userEmail owner's email address (FK to app_users)
 * @param itemType  classification determining match logic
 * @param value     the tracked value (company slug, keyword, or topic name)
 * @param label     human-readable display label (e.g. "Tempus AI", "FDA clearance")
 * @param createdAt when the item was added to the watchlist
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public record WatchlistItem(
        String itemId,
        String userEmail,
        WatchlistItemType itemType,
        String value,
        String label,
        Instant createdAt
) {

    /** Compact constructor — validates required fields. */
    public WatchlistItem {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        if (itemType == null) {
            throw new IllegalArgumentException("itemType must not be null");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
