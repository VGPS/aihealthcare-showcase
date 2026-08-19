package com.wgblackmon.aihealthcare.domain.marketanalysis;

import java.time.Instant;
import java.util.List;

/**
 * Raw AI-healthcare market news item produced by the research adapter.
 *
 * <p>This is the initial, unenriched result of a {@link port.MarketNewsResearchPort} call.
 * It carries the headline, summary, and at least one source URL from the Perplexity
 * search response. The {@link MarketDigestEntry} aggregate wraps this record after LLM
 * classification and market-data enrichment.
 *
 * @param headline     short descriptive title of the news event (required, non-blank)
 * @param summary      paragraph summary of the event (required, non-blank)
 * @param sourceUrls   one or more URLs citing the original sources (required; null yields empty list)
 * @param publishedAt  time the news was published or discovered (required, non-null)
 * @param category     classification of the event type (required, non-null)
 * @param dealSizeUsd  disclosed deal amount in USD (nullable — only populated for FUNDING and
 *                     M_AND_A items where the amount was publicly stated)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public record MarketNewsItem(
        String headline,
        String summary,
        List<String> sourceUrls,
        Instant publishedAt,
        NewsCategory category,
        Long dealSizeUsd
) {

    public MarketNewsItem {
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("headline must not be blank");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
        if (publishedAt == null) {
            throw new IllegalArgumentException("publishedAt must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
    }
}
