package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
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
 * Pure domain service that matches incoming articles against subscriber
 * watchlist items.
 *
 * <p>For each article × item combination, applies type-specific matching:
 * <ul>
 *   <li>{@code KEYWORD} — case-insensitive substring match in title + body</li>
 *   <li>{@code COMPANY} — case-insensitive match of label in title + body</li>
 *   <li>{@code TOPIC} — case-insensitive match of value against article topic</li>
 * </ul>
 *
 * <p>Duplicate matches (same item + article) are suppressed within a single run.
 * Cross-run deduplication is handled by the caller via
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort#existsByItemAndArticle}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-08-01
 *
 * <p>Fix: stripHtmlTags now performs two-pass tag removal to catch tags
 * re-created by HTML entity decoding (e.g. &amp;lt;a href=...&amp;gt;).
 */
public class WatchlistMatchingService {

    private static final int SNIPPET_RADIUS = 100;
    private static final int MAX_SNIPPET_LENGTH = 250;

    /**
     * Matches a batch of articles against a list of watchlist items.
     *
     * @param articles incoming articles to scan
     * @param items    all active watchlist items
     * @return list of new matches (deduplicated within this batch)
     */
    public List<WatchlistMatch> matchArticlesAgainstWatchlist(List<NewsArticle> articles,
                                                              List<WatchlistItem> items) {
        List<WatchlistMatch> matches = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Instant now = Instant.now();

        for (NewsArticle article : articles) {
            String titleLower = article.title() != null ? stripHtmlTags(article.title()).toLowerCase() : "";
            String bodyLower = article.bodyText() != null ? stripHtmlTags(article.bodyText()).toLowerCase() : "";
            String combined = titleLower + " " + bodyLower;
            String topicLower = article.topic() != null ? article.topic().toLowerCase() : "";

            for (WatchlistItem item : items) {
                String dedupKey = item.itemId() + "|" + article.articleId();
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
                    matched = topicLower.contains(searchTerm);
                }

                if (matched) {
                    seen.add(dedupKey);
                    String snippet = extractSnippet(combined, searchTerm);
                    WatchlistMatch match = new WatchlistMatch(
                            UUID.randomUUID().toString(),
                            item.itemId(),
                            article.articleId(),
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
     * Strips HTML tags from text using a simple regex.
     * Decodes common HTML entities and collapses whitespace.
     *
     * @param text raw text potentially containing HTML tags
     * @return plain text with tags removed
     */
    private String stripHtmlTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String stripped = text.replaceAll("<[^>]+>", " ");
        stripped = stripped.replaceAll("<[^>]*$", "");
        stripped = stripped.replace("&amp;", "&")
                          .replace("&lt;", "<")
                          .replace("&gt;", ">")
                          .replace("&quot;", "\"")
                          .replace("&#39;", "'")
                          .replace("&nbsp;", " ");
        stripped = stripped.replaceAll("<[^>]+>", " ");
        stripped = stripped.replaceAll("<[^>]*$", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    /**
     * Extracts a short snippet of text surrounding the first occurrence of
     * the search term.
     *
     * @param text       the full text to search (already lowercased)
     * @param searchTerm the term to find (already lowercased)
     * @return a snippet with context, or null if not found
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
