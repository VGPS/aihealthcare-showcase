package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link ArticleIngestionPort}.
 *
 * <p>Queries the {@code news_articles} table for articles matching the given
 * topic name, applying the {@code maxArticles} limit in-memory after the
 * query.  Articles are mapped from {@link NewsArticleEntity} back to the
 * immutable {@link NewsArticle} domain record before returning.
 *
 * <p>The {@code url} column is stored as a {@code String}; this adapter
 * restores it to a {@code java.net.URI} via {@code URI.create()}.  If a
 * stored URL is not a valid URI (e.g., contains unencoded spaces), the adapter
 * falls back to {@code URI.create("")} and logs a warning rather than
 * throwing, preventing a single bad row from breaking the pipeline.
 *
 * <p>Replaces the Slice 1 stub that always returned an empty list.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-17
 */
@Slf4j
@Component
public class ArticleIngestionAdapter implements ArticleIngestionPort {

    private final NewsArticleRepository repository;

    public ArticleIngestionAdapter(NewsArticleRepository repository) {
        log.debug("ArticleIngestionAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public List<NewsArticle> fetchArticles(String topic, int maxArticles) {
        log.debug("fetchArticles() | topic={}, maxArticles={}", topic, maxArticles);

        List<NewsArticleEntity> entities = repository.findByTopic(topic);
        List<NewsArticle> result = new ArrayList<>();
        int limit = Math.min(entities.size(), maxArticles);

        for (int i = 0; i < limit; i++) {
            result.add(toDomain(entities.get(i)));
        }

        log.debug("fetchArticles() | return={} articles", result.size());
        return result;
    }

    @Override
    public List<NewsArticle> fetchArticlesByIds(List<String> articleIds) {
        log.debug("fetchArticlesByIds() | articleIds={}", articleIds);

        List<NewsArticleEntity> entities = repository.findByArticleIdIn(articleIds);
        List<NewsArticle> result = new ArrayList<>();

        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("fetchArticlesByIds() | return={} articles", result.size());
        return result;
    }

    private NewsArticle toDomain(NewsArticleEntity entity) {
        log.debug("toDomain() | articleId={}", entity.getArticleId());

        URI url;
        try {
            url = entity.getUrl() != null && !entity.getUrl().isBlank()
                    ? URI.create(entity.getUrl())
                    : URI.create("");
        } catch (IllegalArgumentException ex) {
            log.warn("toDomain() | invalid URI stored for articleId={}, using empty URI",
                    entity.getArticleId());
            url = URI.create("");
        }

        NewsArticle result = new NewsArticle(
                entity.getArticleId(),
                entity.getTitle(),
                url,
                entity.getBodyText(),
                entity.getTopic(),
                entity.getAuthor(),
                entity.getTopicId(),
                entity.getSourceName(),
                entity.getSourceTier(),
                entity.getSourceWeight(),
                entity.getPublishedAt()
        );

        log.debug("toDomain() | return={}", result.articleId());
        return result;
    }
}
