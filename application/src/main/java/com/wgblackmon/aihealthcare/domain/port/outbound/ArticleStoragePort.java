package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for persisting harvested {@link NewsArticle} records.
 *
 * <p>Implementations must silently skip articles whose URL already exists in
 * the store — deduplication is the adapter's responsibility, not the caller's.
 * This ensures that repeated scheduler invocations (every 4 hours for INDUSTRY
 * feeds, daily for ACADEMIC/REGULATORY) do not produce duplicate rows.
 *
 * <p>Callers pass the full harvested list; the adapter filters duplicates
 * internally and persists only new articles.  An empty input list is a no-op.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public interface ArticleStoragePort {

    /**
     * Persists the supplied articles, skipping any whose URL is already stored.
     *
     * @param articles articles to persist; must not be {@code null}; may be empty
     */
    void save(List<NewsArticle> articles);
}
