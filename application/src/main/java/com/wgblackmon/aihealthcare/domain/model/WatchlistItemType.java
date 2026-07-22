package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classification of a watchlist item — determines how incoming articles
 * are matched against the item.
 *
 * <ul>
 *   <li>{@code COMPANY} — matches articles mentioning a tracked company name</li>
 *   <li>{@code KEYWORD} — matches articles containing a specific keyword or phrase</li>
 *   <li>{@code TOPIC} — matches articles filed under a specific topic/feed</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public enum WatchlistItemType {
    COMPANY,
    KEYWORD,
    TOPIC
}
