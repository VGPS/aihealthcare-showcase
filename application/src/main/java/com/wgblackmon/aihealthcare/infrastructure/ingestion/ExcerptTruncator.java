package com.wgblackmon.aihealthcare.infrastructure.ingestion;

/**
 * Truncates article body text to a configurable maximum character limit,
 * cutting at a sentence boundary when possible.
 *
 * <p>Called at ingestion time so that the database never stores more text
 * than the configured cap for a given source tier. This is a copyright-safety
 * boundary: COMPETITOR-tier scraped pages are capped at 500 chars, while
 * RSS/API content is capped at 2,000 chars.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public final class ExcerptTruncator {

    private ExcerptTruncator() {
    }

    /**
     * Truncates text to at most {@code maxChars} characters, preferring to
     * cut at the last sentence-ending punctuation ({@code .}, {@code !},
     * {@code ?}) within the limit. If no sentence boundary is found, cuts
     * at the last whitespace and appends {@code "..."}.
     *
     * @param text     the text to truncate (null or blank returns as-is)
     * @param maxChars the hard cap in characters (must be positive)
     * @return the truncated text, or the original if already within the cap
     */
    public static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }

        String window = text.substring(0, maxChars);

        int lastSentenceEnd = -1;
        for (int i = window.length() - 1; i >= 0; i--) {
            char c = window.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                lastSentenceEnd = i;
                break;
            }
        }

        if (lastSentenceEnd > 0) {
            return window.substring(0, lastSentenceEnd + 1);
        }

        int lastSpace = window.lastIndexOf(' ');
        if (lastSpace > 0) {
            return window.substring(0, lastSpace) + "...";
        }

        return window + "...";
    }
}
