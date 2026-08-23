package com.wgblackmon.aihealthcare.infrastructure.persistence;

import java.time.Instant;

/**
 * JPA interface projection for the news listing page.
 *
 * <p>Includes only the fields rendered by {@code news-listing.html}.
 * Excludes {@code bodyText} (a TEXT column that averages ~500-2000 bytes per row)
 * to avoid transferring article body content for a page that never displays it.
 * Spring Data generates a column-specific SELECT that skips {@code body_text}.
 *
 * <p>Used by {@code findTop25...} methods in {@link NewsArticleRepository}.
 * Full-entity methods remain available for pages that need {@code bodyText}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
public interface NewsArticleListView {

    String getArticleId();

    String getTitle();

    String getUrl();

    String getTopic();

    String getAuthor();

    Long getTopicId();

    String getSourceName();

    String getSourceTier();

    double getSourceWeight();

    Instant getPublishedAt();
}
