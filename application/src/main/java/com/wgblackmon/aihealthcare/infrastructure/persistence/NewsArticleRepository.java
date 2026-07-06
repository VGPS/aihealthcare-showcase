package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link NewsArticleEntity}.
 *
 * <p>The {@code existsByUrl} method is the deduplication gate used by
 * {@link ArticleStorageAdapter} before every insert — duplicate URLs are
 * silently skipped per the {@link com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort}
 * contract.
 *
 * <p>The {@code findByTopic} method backs the DB-based implementation of
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort#fetchArticles},
 * replacing the stub that previously returned an empty list.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-07-03
 */
public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, String>,
                JpaSpecificationExecutor<NewsArticleEntity> {

    /**
     * Returns {@code true} if an article with the given URL already exists.
     * Used for deduplication in {@link ArticleStorageAdapter}.
     *
     * @param url the article URL as a string
     * @return {@code true} if a matching row exists
     */
    boolean existsByUrl(String url);

    /**
     * Finds all articles belonging to the given topic name.
     *
     * @param topic the topic name to filter by (e.g. the feed source name)
     * @return list of matching entities; empty if none found
     */
    List<NewsArticleEntity> findByTopic(String topic);

    /**
     * Finds articles whose topic contains the given keyword (case-insensitive)
     * and that were created on or after the given cutoff instant.
     *
     * @param topic   substring to match against the topic column (case-insensitive)
     * @param cutoff  only articles created at or after this instant are returned
     * @return list of matching entities; empty if none found
     */
    List<NewsArticleEntity> findByTopicContainingIgnoreCaseAndCreatedAtAfter(
            String topic, Instant cutoff);

    /**
     * Finds articles whose topic contains the given keyword (case-insensitive),
     * with no date restriction.
     *
     * @param topic substring to match against the topic column (case-insensitive)
     * @return list of matching entities; empty if none found
     */
    List<NewsArticleEntity> findByTopicContainingIgnoreCase(String topic);

    /**
     * Finds all articles whose IDs are in the given list.
     * Used by the prompt evaluation framework to load specific articles.
     *
     * @param articleIds the article IDs to search for
     * @return list of matching entities; may be smaller than the input if some IDs are missing
     */
    List<NewsArticleEntity> findByArticleIdIn(List<String> articleIds);

    /**
     * Returns article counts grouped by {@code sourceTier}, ordered by count descending.
     * Each element is a two-element {@code Object[]} where index 0 is the tier string
     * and index 1 is the {@code Long} count.
     *
     * @return list of [sourceTier, count] pairs
     */
    @Query("SELECT n.sourceTier, COUNT(n) FROM NewsArticleEntity n GROUP BY n.sourceTier ORDER BY COUNT(n) DESC")
    List<Object[]> countBySourceTierGrouped();

    /**
     * Returns article counts grouped by {@code topic}, ordered by count descending.
     * Each element is a two-element {@code Object[]} where index 0 is the topic string
     * and index 1 is the {@code Long} count.
     *
     * @return list of [topic, count] pairs
     */
    @Query("SELECT n.topic, COUNT(n) FROM NewsArticleEntity n GROUP BY n.topic ORDER BY COUNT(n) DESC")
    List<Object[]> countByTopicGrouped();

    /**
     * Counts articles whose {@code createdAt} is strictly after the given instant.
     * Used to compute 7-day and 30-day ingestion windows.
     *
     * @param since the lower-bound instant (exclusive)
     * @return count of matching articles
     */
    long countByCreatedAtAfter(Instant since);

    /**
     * Returns articles created on or after the given instant, ordered by creation
     * time ascending. Used for building time-series chart data.
     *
     * @param since the lower-bound instant (inclusive via "after or equal")
     * @return list of matching entities, oldest first
     */
    List<NewsArticleEntity> findByCreatedAtAfterOrderByCreatedAtAsc(Instant since);

    /**
     * Returns article counts grouped by date (cast to DATE), for articles created
     * after the given instant. Each element is a two-element {@code Object[]} where
     * index 0 is the date ({@code java.sql.Date}) and index 1 is the {@code Long} count.
     *
     * @param since the lower-bound instant (exclusive)
     * @return list of [date, count] pairs ordered by date ascending
     */
    @Query("SELECT CAST(n.createdAt AS date), COUNT(n) FROM NewsArticleEntity n "
         + "WHERE n.createdAt > :since GROUP BY CAST(n.createdAt AS date) "
         + "ORDER BY CAST(n.createdAt AS date) ASC")
    List<Object[]> countByDayGrouped(Instant since);
}
