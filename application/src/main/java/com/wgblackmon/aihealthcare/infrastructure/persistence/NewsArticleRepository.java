package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
 * @updated 2026-04-11
 */
public interface NewsArticleRepository extends JpaRepository<NewsArticleEntity, String> {

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
}
