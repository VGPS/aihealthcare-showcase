package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleListView;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link ArticleIngestionPort}.
 *
 * <p>Queries the {@code news_articles} table for articles whose topic
 * <em>contains</em> the given keyword (case-insensitive), optionally
 * filtered to articles published within the last {@code daysBack} days.
 * The {@code daysBack} value is read from
 * {@code aihealthcare.articles.days-back} in {@code application.yml}
 * and defaults to {@code 7}.  Set to {@code 0} to disable date filtering.
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
 * @version 1.2
 * @since   2026-04-04
 * @updated 2026-05-30
 */
@Slf4j
@Component
public class ArticleIngestionAdapter implements ArticleIngestionPort {

    private final NewsArticleRepository repository;
    private final int daysBack;

    public ArticleIngestionAdapter(
            NewsArticleRepository repository,
            @Value("${aihealthcare.articles.days-back:7}") int daysBack) {
        log.debug("ArticleIngestionAdapter() | repository={}, daysBack={}",
                  repository.getClass().getSimpleName(), daysBack);
        this.repository = repository;
        this.daysBack = daysBack;
    }

    @Override
    public List<NewsArticle> fetchArticles(String topic, int maxArticles) {
        log.debug("fetchArticles() | topic={}, maxArticles={}, daysBack={}", topic, maxArticles, daysBack);

        List<NewsArticleEntity> entities;
        if (daysBack > 0) {
            Instant cutoff = Instant.now().minus(daysBack, ChronoUnit.DAYS);
            log.debug("fetchArticles() | using publishedAt filter: cutoff={}", cutoff);
            entities = repository.findByTopicContainingIgnoreCaseAndPublishedAtAfter(topic, cutoff);
        } else {
            log.debug("fetchArticles() | daysBack=0, no date filter applied");
            entities = repository.findByTopicContainingIgnoreCase(topic);
        }

        log.debug("fetchArticles() | query returned {} entities", entities.size());

        List<NewsArticle> result = new ArrayList<>();
        int limit = Math.min(entities.size(), maxArticles);

        for (int i = 0; i < limit; i++) {
            result.add(toDomain(entities.get(i)));
        }

        log.debug("fetchArticles() | return={} articles (limit applied: {})", result.size(), limit);
        return result;
    }

    @Override
    public List<NewsArticle> fetchAllByTopic(String topic) {
        log.debug("fetchAllByTopic() | topic={}", topic);

        List<NewsArticleEntity> entities = repository.findByTopicContainingIgnoreCase(topic);
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("fetchAllByTopic() | return={} articles", result.size());
        return result;
    }

    @Override
    public List<NewsArticle> fetchByTopicWithArchiveLimit(String topic, int archiveDays) {
        log.debug("fetchByTopicWithArchiveLimit() | topic={}, archiveDays={}", topic, archiveDays);

        List<NewsArticleEntity> entities;
        if (archiveDays > 0) {
            Instant cutoff = Instant.now().minus(archiveDays, ChronoUnit.DAYS);
            log.debug("fetchByTopicWithArchiveLimit() | using publishedAt filter: cutoff={}", cutoff);
            entities = repository.findByTopicContainingIgnoreCaseAndPublishedAtAfter(topic, cutoff);
        } else {
            log.debug("fetchByTopicWithArchiveLimit() | archiveDays=0, no date filter applied");
            entities = repository.findByTopicContainingIgnoreCase(topic);
        }

        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("fetchByTopicWithArchiveLimit() | return={} articles", result.size());
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

    @Override
    public List<NewsArticle> fetchRecentArticles(int days) {
        log.debug("fetchRecentArticles() | days={}", days);

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<NewsArticleEntity> entities = repository.findByCreatedAtAfterOrderByCreatedAtAsc(cutoff);
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("fetchRecentArticles() | return={} articles", result.size());
        return result;
    }

    @Override
    public List<NewsArticle> fetchArticlesByDateRange(Instant from, Instant to) {
        log.debug("fetchArticlesByDateRange() | from={}, to={}", from, to);

        List<NewsArticleEntity> entities = repository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("fetchArticlesByDateRange() | return={} articles", result.size());
        return result;
    }

    @Override
    public List<NewsArticle> fetchNewsHeadlines(String topic, int archiveDays) {
        log.debug("fetchNewsHeadlines() | topic={}, archiveDays={}", topic, archiveDays);

        List<NewsArticleListView> views;
        if (archiveDays > 0) {
            Instant cutoff = Instant.now().minus(archiveDays, ChronoUnit.DAYS);
            log.debug("fetchNewsHeadlines() | using publishedAt filter: cutoff={}", cutoff);
            views = repository.findTop25ByTopicContainingIgnoreCaseAndPublishedAtAfterOrderByPublishedAtDesc(
                    topic, cutoff);
        } else {
            log.debug("fetchNewsHeadlines() | archiveDays=0, no date filter applied");
            views = repository.findTop25ByTopicContainingIgnoreCaseOrderByPublishedAtDesc(topic);
        }

        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleListView view : views) {
            result.add(toHeadline(view));
        }

        log.debug("fetchNewsHeadlines() | return={} articles (capped at 25)", result.size());
        return result;
    }

    private NewsArticle toHeadline(NewsArticleListView view) {
        URI url = null;
        try {
            url = view.getUrl() != null && !view.getUrl().isBlank()
                    ? URI.create(view.getUrl())
                    : URI.create("");
        } catch (IllegalArgumentException ex) {
            log.warn("toHeadline() | invalid URI for articleId={}, using empty URI", view.getArticleId());
        }
        return new NewsArticle(
                view.getArticleId(),
                view.getTitle(),
                url,
                null,
                view.getTopic(),
                view.getAuthor(),
                view.getTopicId(),
                view.getSourceName(),
                view.getSourceTier(),
                view.getSourceWeight(),
                view.getPublishedAt()
        );
    }

    private NewsArticle toDomain(NewsArticleEntity entity) {
        log.debug("toDomain() | articleId={}, topic={}, createdAt={}",
                  entity.getArticleId(), entity.getTopic(), entity.getCreatedAt());

        URI url = null;
        try {
            url = entity.getUrl() != null && !entity.getUrl().isBlank()
                    ? URI.create(entity.getUrl())
                    : URI.create("");
        } catch (IllegalArgumentException ex) {
            log.warn("toDomain() | invalid URI stored for articleId={}, using empty URI",
                    entity.getArticleId());
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
