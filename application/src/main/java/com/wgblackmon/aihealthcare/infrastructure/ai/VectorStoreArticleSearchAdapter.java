package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spring AI adapter implementing {@link ArticleSearchPort} via a {@link VectorStore}.
 *
 * <p>Translates a natural-language or topic-name query into a vector similarity
 * search using the configured {@link VectorStore} (a
 * {@code PgVectorStore} backed by PostgreSQL + pgvector).
 * Each {@link Document} returned by the vector store carries the originating
 * {@code articleId} in its metadata; this adapter resolves that ID back to a
 * full {@link NewsArticle} domain record by querying {@link NewsArticleRepository}.
 *
 * <p>If the vector store is empty (e.g., the {@link EmbeddingScheduler} has not
 * yet run, or the OpenAI API key is invalid), {@link #findSimilar} returns an
 * empty list — callers must handle this gracefully.
 *
 * <p>Documents whose {@code articleId} metadata cannot be resolved in the
 * database are silently skipped and logged at {@code WARN} level.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-06-02
 */
@Slf4j
@Component
public class VectorStoreArticleSearchAdapter implements ArticleSearchPort {

    private final VectorStore            vectorStore;
    private final NewsArticleRepository  repository;

    public VectorStoreArticleSearchAdapter(ObjectProvider<VectorStore> vectorStoreProvider,
                                           NewsArticleRepository repository) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.repository  = repository;
        log.debug("VectorStoreArticleSearchAdapter() | vectorStore={}, repository={}",
                  vectorStore != null ? vectorStore.getClass().getSimpleName() : "NULL (not configured)",
                  repository.getClass().getSimpleName());
    }

    @Override
    public List<NewsArticle> findSimilar(String query, int topK) {
        log.debug("findSimilar() | query={}, topK={}", query, topK);

        if (vectorStore == null) {
            log.warn("findSimilar() | VectorStore not available — returning empty list");
            log.debug("findSimilar() | return=0 articles (no vector store)");
            return List.of();
        }

        List<Document> docs;
        try {
            SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
            docs = vectorStore.similaritySearch(request);
        } catch (Exception ex) {
            log.warn("findSimilar() | Vector search failed (vector store may be empty or API key invalid): {}",
                     ex.getMessage());
            log.debug("findSimilar() | return=0 articles (search exception)");
            return List.of();
        }

        List<NewsArticle> result = new ArrayList<>();
        for (Document doc : docs) {
            Object articleIdObj = doc.getMetadata().get("articleId");
            if (articleIdObj == null) {
                log.warn("findSimilar() | Document {} has no articleId metadata, skipping", doc.getId());
                continue;
            }
            String articleId = articleIdObj.toString();
            Optional<NewsArticleEntity> entity = repository.findById(articleId);
            if (entity.isEmpty()) {
                log.warn("findSimilar() | articleId={} from vector store not found in DB, skipping",
                         articleId);
                continue;
            }
            result.add(toDomain(entity.get()));
        }

        log.debug("findSimilar() | return={} articles", result.size());
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
            log.warn("toDomain() | invalid URI for articleId={}, using empty URI",
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
