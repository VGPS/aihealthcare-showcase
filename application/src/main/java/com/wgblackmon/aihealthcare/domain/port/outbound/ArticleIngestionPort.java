package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.time.Instant;
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
     * Fetch all articles for the given topic with no date restriction.
     *
     * <p>Used by the analytics dashboard detail view to show the complete
     * article list that matches the analytics count, bypassing any
     * {@code days-back} window applied by {@link #fetchArticles}.
     *
     * @param topic exact topic label to filter by (case-insensitive substring match)
     * @return all matching articles; may be empty if no results are found
     */
    List<NewsArticle> fetchAllByTopic(String topic);

    /**
     * Fetch all articles for the given topic, restricted to articles created
     * within the last {@code archiveDays} days.  If {@code archiveDays} is 0,
     * no date restriction is applied (unlimited archive access).
     *
     * <p>Used by tier-gated UI pages to enforce archive depth limits per
     * subscription tier (e.g. FREE sees last 7 days, SUBSCRIBER sees all).
     *
     * @param topic       exact topic label to filter by (case-insensitive substring match)
     * @param archiveDays maximum article age in days; 0 = unlimited
     * @return matching articles within the date window; may be empty
     */
    List<NewsArticle> fetchByTopicWithArchiveLimit(String topic, int archiveDays);

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

    /**
     * Fetch all articles created within the last {@code days} days,
     * regardless of topic.
     *
     * <p>Used by the trend detection engine to analyze keyword frequency
     * across the entire article corpus within a rolling time window.
     *
     * @param days number of days to look back; must be &gt; 0
     * @return all articles created within the window; may be empty
     */
    List<NewsArticle> fetchRecentArticles(int days);

    /**
     * Fetch all articles created between {@code from} and {@code to} (inclusive),
     * regardless of topic.
     *
     * <p>Used by the trend detection date-range filter to analyze keyword frequency
     * across a user-specified time window.
     *
     * @param from start of the date range (inclusive); must not be null
     * @param to   end of the date range (inclusive); must not be null
     * @return all articles created within the range; may be empty
     */
    List<NewsArticle> fetchArticlesByDateRange(Instant from, Instant to);
}
