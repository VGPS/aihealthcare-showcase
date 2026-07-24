package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
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
 * Pure domain service that matches clinical trials against subscriber
 * watchlist items.
 *
 * <p>Analogous to {@link RegulatoryWatchlistMatcher} but operates on
 * {@link ClinicalTrial} records. Matching rules:
 * <ul>
 *   <li>{@code KEYWORD} — case-insensitive match in title + summary + conditions</li>
 *   <li>{@code COMPANY} — case-insensitive match of label in sponsor + title</li>
 *   <li>{@code TOPIC} — matches if the trial's keyword list contains the topic value</li>
 * </ul>
 *
 * <p>Produces standard {@link WatchlistMatch} records, reusing the existing
 * watchlist infrastructure. The {@link WatchlistMatch#articleId()} field is
 * populated with the trial's {@code trialId}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public class ClinicalTrialWatchlistMatcher {

    private static final int SNIPPET_RADIUS = 100;
    private static final int MAX_SNIPPET_LENGTH = 250;

    /**
     * Matches a batch of clinical trials against a list of watchlist items.
     *
     * @param trials clinical trials to scan
     * @param items  all active watchlist items
     * @return list of new matches (deduplicated within this batch)
     */
    public List<WatchlistMatch> matchTrialsAgainstWatchlist(List<ClinicalTrial> trials,
                                                            List<WatchlistItem> items) {
        List<WatchlistMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();

        for (ClinicalTrial trial : trials) {
            String titleLower = trial.title() != null ? trial.title().toLowerCase() : "";
            String summaryLower = trial.briefSummary() != null ? trial.briefSummary().toLowerCase() : "";
            String sponsorLower = trial.sponsor() != null ? trial.sponsor().toLowerCase() : "";

            StringBuilder conditionsBuilder = new StringBuilder();
            for (String condition : trial.conditions()) {
                conditionsBuilder.append(condition.toLowerCase()).append(" ");
            }
            String conditionsText = conditionsBuilder.toString();

            String combined = titleLower + " " + summaryLower + " " + sponsorLower + " " + conditionsText;

            StringBuilder keywordsBuilder = new StringBuilder();
            for (String kw : trial.aiHealthcareKeywords()) {
                keywordsBuilder.append(kw.toLowerCase()).append(" ");
            }
            String keywordsText = keywordsBuilder.toString();

            for (WatchlistItem item : items) {
                String dedupKey = item.itemId() + "|" + trial.trialId();
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
                            trial.trialId(),
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
