package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link ArticleSearchQueryPort}.
 *
 * <p>Uses {@link ArticleSpecificationBuilder} to build a dynamic
 * {@link Specification} from the given {@link ArticleSearchCriteria}, then
 * delegates to {@link NewsArticleRepository#findAll(Specification)}.
 *
 * <p>Entity-to-domain conversion follows the same pattern as
 * {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.ArticleIngestionAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
@Slf4j
@Component
public class ArticleSearchQueryAdapter implements ArticleSearchQueryPort {

    private final NewsArticleRepository repository;

    public ArticleSearchQueryAdapter(NewsArticleRepository repository) {
        log.debug("ArticleSearchQueryAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public List<NewsArticle> findByCriteria(ArticleSearchCriteria criteria) {
        log.debug("findByCriteria() | criteria={}", criteria);

        Specification<NewsArticleEntity> spec = ArticleSpecificationBuilder.fromCriteria(criteria);
        List<NewsArticleEntity> entities = repository.findAll(spec);

        log.debug("findByCriteria() | query returned {} entities", entities.size());

        List<NewsArticle> result = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findByCriteria() | return={} articles", result.size());
        return result;
    }

    /**
     * Converts a JPA entity to its domain record equivalent.
     *
     * <p>The {@code url} column is stored as a plain string; this method
     * restores it to a {@link URI}. If the stored URL is invalid, the method
     * falls back to {@code URI.create("")} rather than throwing.
     *
     * @param entity the JPA entity to convert
     * @return the equivalent domain record
     */
    private NewsArticle toDomain(NewsArticleEntity entity) {
        log.debug("toDomain() | articleId={}, topic={}", entity.getArticleId(), entity.getTopic());

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
