package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.service.ArticleQualityFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JPA-backed implementation of {@link ArticleStoragePort}.
 *
 * <p>Persists harvested {@link NewsArticle} domain records to the
 * {@code news_articles} table via {@link NewsArticleRepository}.  Deduplication
 * is performed per-article using {@link NewsArticleRepository#existsByUrl(String)};
 * any article whose URL already exists is silently skipped without error.
 *
 * <p>The domain {@link NewsArticle} record is converted to a
 * {@link NewsArticleEntity} inside this adapter — the entity never leaves the
 * infrastructure layer.  The {@code url} field is stored as a {@code String}
 * (the {@code java.net.URI} is converted via {@code toString()}).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-11
 * @updated 2026-09-05
 */
@Slf4j
@Component
public class ArticleStorageAdapter implements ArticleStoragePort {

    private static final int ARTICLE_ID_MAX_LENGTH = 1024;
    private static final int TITLE_MAX_LENGTH = 1024;
    private static final int URL_MAX_LENGTH = 2048;
    private static final int TOPIC_MAX_LENGTH = 512;
    private static final int AUTHOR_MAX_LENGTH = 512;

    private final NewsArticleRepository repository;
    private final ArticleQualityFilter   articleQualityFilter;

    public ArticleStorageAdapter(NewsArticleRepository repository,
                                  ArticleQualityFilter articleQualityFilter) {
        log.debug("ArticleStorageAdapter() | repository={}, articleQualityFilter={}",
                  repository.getClass().getSimpleName(),
                  articleQualityFilter.getClass().getSimpleName());
        this.repository           = repository;
        this.articleQualityFilter = articleQualityFilter;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the supplied list and checks each article's URL for an
     * existing row before inserting.  Duplicate URLs are skipped and logged
     * at DEBUG level.
     */
    @Override
    public void save(List<NewsArticle> articles) {
        log.debug("save() | articles={}", articles.size());

        int saved = 0;
        int skipped = 0;

        for (NewsArticle article : articles) {
            if (!articleQualityFilter.isUsable(article)) {
                log.debug("save() | skipping low-quality article title={}", article.title());
                skipped++;
                continue;
            }
            String urlString = article.url() != null ? article.url().toString() : "";
            if (repository.existsByUrl(urlString)) {
                log.debug("save() | skipping duplicate url={}", urlString);
                skipped++;
            } else {
                try {
                    repository.save(toEntity(article));
                    saved++;
                } catch (RuntimeException e) {
                    log.warn("save() | failed to persist article {} — skipping. cause={}",
                            article.articleId(), e.getMessage());
                    skipped++;
                }
            }
        }

        log.info("save() | Persisted {} new articles, skipped {} duplicates", saved, skipped);
        log.debug("save() | return=void");
    }

    private NewsArticleEntity toEntity(NewsArticle article) {
        log.debug("toEntity() | articleId={}", article.articleId());

        String url = article.url() != null ? article.url().toString() : "";
        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setArticleId(truncate(article.articleId(), article.articleId(), "articleId", ARTICLE_ID_MAX_LENGTH));
        entity.setTitle(truncate(article.articleId(), article.title(), "title", TITLE_MAX_LENGTH));
        entity.setUrl(truncate(article.articleId(), url, "url", URL_MAX_LENGTH));
        entity.setBodyText(article.bodyText());
        entity.setTopic(truncate(article.articleId(), article.topic(), "topic", TOPIC_MAX_LENGTH));
        entity.setAuthor(truncate(article.articleId(), article.author(), "author", AUTHOR_MAX_LENGTH));
        entity.setTopicId(article.topicId());
        entity.setSourceName(article.sourceName());
        entity.setSourceTier(article.sourceTier());
        entity.setSourceWeight(article.sourceWeight());
        entity.setPublishedAt(article.publishedAt());

        log.debug("toEntity() | return={}", entity.getArticleId());
        return entity;
    }

    /**
     * Clips a value to the database column's max length, logging a warning
     * when clipping actually occurs. RSS entry URIs (used as both the
     * article id and the url) have no length guarantee — some feeds
     * (Google News in particular) emit URLs long enough to overflow even
     * these generous bounds.
     *
     * @param articleId the owning article's id, for the warning log
     * @param value     the value to clip; null passes through unchanged
     * @param fieldName the column name, for the warning log
     * @param maxLength the column's max length
     * @return the value, clipped to {@code maxLength} characters if needed
     */
    private String truncate(String articleId, String value, String fieldName, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("truncate() | article {} field '{}' is {} chars, exceeding column limit of {} — clipping",
                articleId, fieldName, value.length(), maxLength);
        return value.substring(0, maxLength);
    }
}
