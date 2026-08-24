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
 * @updated 2026-08-23
 */
@Slf4j
@Component
public class ArticleStorageAdapter implements ArticleStoragePort {

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
                repository.save(toEntity(article));
                saved++;
            }
        }

        log.info("save() | Persisted {} new articles, skipped {} duplicates", saved, skipped);
        log.debug("save() | return=void");
    }

    private NewsArticleEntity toEntity(NewsArticle article) {
        log.debug("toEntity() | articleId={}", article.articleId());

        NewsArticleEntity entity = new NewsArticleEntity();
        entity.setArticleId(article.articleId());
        entity.setTitle(article.title());
        entity.setUrl(article.url() != null ? article.url().toString() : "");
        entity.setBodyText(article.bodyText());
        entity.setTopic(article.topic());
        entity.setAuthor(article.author());
        entity.setTopicId(article.topicId());
        entity.setSourceName(article.sourceName());
        entity.setSourceTier(article.sourceTier());
        entity.setSourceWeight(article.sourceWeight());
        entity.setPublishedAt(article.publishedAt());

        log.debug("toEntity() | return={}", entity.getArticleId());
        return entity;
    }
}
