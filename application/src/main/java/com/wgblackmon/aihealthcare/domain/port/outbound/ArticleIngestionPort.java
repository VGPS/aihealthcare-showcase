package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port — fetch articles from external sources for a given topic.
 *
 * <p>Implementations live in {@code infrastructure/ingestion}. The domain and application
 * layers depend only on this interface; no HTTP, RSS, or scraping details leak inward.
 *
 * <p>For Slice 1 the implementation performs a direct HTTP fetch of a supplied URL list.
 * Future slices may add RSS readers, news API clients, or a configurable source registry
 * without changing this contract.
 */
public interface ArticleIngestionPort {

    /**
     * Fetch up to {@code maxArticles} articles relevant to the given {@code topic}.
     *
     * @param topic       Search keyword or phrase (e.g. "AI in healthcare 2025").
     * @param maxArticles Maximum number of articles to return; must be &gt; 0.
     * @return An unordered list of ingested articles; may be empty if no results are found.
     */
    List<NewsArticle> fetchArticles(String topic, int maxArticles);

    /**
     * Fetch specific articles by their unique identifiers.
     *
     * <p>Used by the prompt evaluation framework to load known articles for
     * controlled experiments.  Articles not found in the database are silently
     * skipped; callers should verify the returned list size.
     *
     * @param articleIds List of article IDs to fetch.
     * @return The matching articles; may be smaller than the input list if some IDs are missing.
     */
    List<NewsArticle> fetchArticlesByIds(List<String> articleIds);
}
