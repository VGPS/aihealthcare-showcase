package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure domain service that matches regulatory events against subscriber
 * watchlist items.
 *
 * <p>Analogous to {@link WatchlistMatchingService} but operates on
 * {@link RegulatoryEvent} records instead of articles. Matching rules:
 * <ul>
 *   <li>{@code KEYWORD} — case-insensitive match in title + summary + deviceName + applicantName</li>
 *   <li>{@code COMPANY} — case-insensitive match of label in applicantName + title + summary</li>
 *   <li>{@code TOPIC} — matches if the event's keyword list contains the topic value</li>
 * </ul>
 *
 * <p>Produces standard {@link WatchlistMatch} records, reusing the existing
 * watchlist infrastructure. The {@link WatchlistMatch#articleId()} field is
 * populated with the event's {@code eventId}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public class RegulatoryWatchlistMatcher {

    private static final int SNIPPET_RADIUS = 100;
    private static final int MAX_SNIPPET_LENGTH = 250;

    /**
     * Matches a batch of regulatory events against a list of watchlist items.
     *
     * @param events regulatory events to scan
     * @param items  all active watchlist items
     * @return list of new matches (deduplicated within this batch)
     */
    public List<WatchlistMatch> matchEventsAgainstWatchlist(List<RegulatoryEvent> events,
                                                            List<WatchlistItem> items) {
        List<WatchlistMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();

        for (RegulatoryEvent event : events) {
            String titleLower = event.title() != null ? event.title().toLowerCase() : "";
            String summaryLower = event.summary() != null ? event.summary().toLowerCase() : "";
            String applicantLower = event.applicantName() != null ? event.applicantName().toLowerCase() : "";
            String deviceLower = event.deviceName() != null ? event.deviceName().toLowerCase() : "";
            String combined = titleLower + " " + summaryLower + " " + applicantLower + " " + deviceLower;

            StringBuilder keywordsLower = new StringBuilder();
            for (String kw : event.aiHealthcareKeywords()) {
                keywordsLower.append(kw.toLowerCase()).append(" ");
            }
            String keywordsText = keywordsLower.toString();

            for (WatchlistItem item : items) {
                String dedupKey = item.itemId() + "|" + event.eventId();
                if (seen.contains(dedupKey)) {
                    continue;
                }

                boolean matched = false;
                String searchTerm = "";

                if (item.itemType() == WatchlistItemType.KEYWORD) {
                    searchTerm = item.value().toLowerCase();
                    matched = combined.contains(searchTerm);
                } else if (item.itemType() == WatchlistItemType.COMPANY) {
                    searchTerm = item.label().toLowerCase();
                    matched = combined.contains(searchTerm);
                } else if (item.itemType() == WatchlistItemType.TOPIC) {
                    searchTerm = item.value().toLowerCase();
                    matched = keywordsText.contains(searchTerm);
                }

                if (matched) {
                    seen.add(dedupKey);
                    String snippet = extractSnippet(combined, searchTerm);
                    WatchlistMatch match = new WatchlistMatch(
                            UUID.randomUUID().toString(),
                            item.itemId(),
                            event.eventId(),
                            now,
                            snippet
                    );
                    matches.add(match);
                }
            }
        }

        return matches;
    }

    /**
     * Extracts a short snippet of text surrounding the first occurrence of
     * the search term.
     */
    private String extractSnippet(String text, String searchTerm) {
        int idx = text.indexOf(searchTerm);
        if (idx < 0) {
            return null;
        }

        int start = Math.max(0, idx - SNIPPET_RADIUS);
        int end = Math.min(text.length(), idx + searchTerm.length() + SNIPPET_RADIUS);
        String snippet = text.substring(start, end).trim();

        if (snippet.length() > MAX_SNIPPET_LENGTH) {
            snippet = snippet.substring(0, MAX_SNIPPET_LENGTH);
        }

        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }
}
