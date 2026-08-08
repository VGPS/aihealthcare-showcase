package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record representing a pre-generated AI summary for a
 * topic section on the news listing page.
 *
 * <p>Each topic with more than one harvested article receives a concise
 * 3-sentence summary generated during the feed harvest cycle.  The summary
 * is stored in the database and served statically on page load to avoid
 * repeated AI API calls.
 *
 * <p>The {@code topic} field matches the topic name used in
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort}
 * and the news listing configuration, serving as the natural key.
 *
 * @param topic       Topic name (e.g. "Anthropic Healthcare"); must not be blank.
 * @param summaryText AI-generated 3-sentence summary of the topic's articles; must not be blank.
 * @param generatedAt Timestamp when the summary was generated; must not be null.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
public record TopicSummary(String topic, String summaryText, Instant generatedAt) {

    /**
     * Compact constructor — validates required fields.
     */
    public TopicSummary {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (summaryText == null || summaryText.isBlank()) {
            throw new IllegalArgumentException("summaryText must not be blank");
        }
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt must not be null");
        }
    }
}
