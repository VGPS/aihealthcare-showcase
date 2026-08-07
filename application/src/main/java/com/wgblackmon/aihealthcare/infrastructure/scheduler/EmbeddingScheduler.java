package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that embeds persisted articles into the in-memory vector store.
 *
 * <p>Runs on the cron schedule defined by {@code aihealthcare.embedding.schedule}
 * in {@code application.yml} (default: daily at midnight).  On each run it
 * fetches all rows from {@code news_articles}, converts them to Spring AI
 * {@link Document} objects, and calls {@link VectorStore#add}.
 *
 * <p>The {@code PgVectorStore} persists embeddings in a PostgreSQL table with
 * the {@code pgvector} extension.  Because each {@link Document} is created
 * with {@code id = articleId}, re-running this scheduler is safe and
 * idempotent — an already-embedded article is simply overwritten in place;
 * no duplicates accumulate.
 *
 * <p>If the embedding API call fails (e.g., missing or invalid API key), the
 * exception is caught and logged as an error so that the scheduler thread is
 * not terminated and subsequent runs can still succeed once a valid key is
 * configured.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-08-07
 */
@Slf4j
@Component
public class EmbeddingScheduler {

    private static final int BATCH_SIZE = 10;
    private static final long BATCH_DELAY_MS = 5000;

    private final NewsArticleRepository repository;
    private final VectorStore           vectorStore;

    public EmbeddingScheduler(NewsArticleRepository repository,
                              ObjectProvider<VectorStore> vectorStoreProvider) {
        this.repository  = repository;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        log.debug("EmbeddingScheduler() | repository={}, vectorStore={}",
                  repository.getClass().getSimpleName(),
                  vectorStore != null ? vectorStore.getClass().getSimpleName() : "NULL (not configured)");
    }

    /**
     * Embeds all persisted articles into the vector store.
     *
     * <p>Invoked on the schedule configured by
     * {@code aihealthcare.embedding.schedule}.  Failures are logged and
     * swallowed so the scheduler thread remains alive for future runs.
     */
    @Scheduled(cron = "${aihealthcare.embedding.schedule}")
    public void embedArticles() {
        log.debug("embedArticles() | starting embedding run");
        try {
            embedArticlesInternal();
        } catch (Exception e) {
            log.error("embedArticles() | scheduler exception", e);
        }
        log.debug("embedArticles() | return=void");
    }

    private void embedArticlesInternal() {
        if (vectorStore == null) {
            log.warn("embedArticles() | VectorStore not available — skipping");
            return;
        }

        List<NewsArticleEntity> entities = repository.findByEmbeddedFalse();
        log.info("embedArticles() | Found {} new articles to embed (skipping already-embedded)", entities.size());

        if (entities.isEmpty()) {
            log.debug("embedArticles() | return=void (nothing to embed)");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (NewsArticleEntity entity : entities) {
            String content = buildContent(entity);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("articleId", entity.getArticleId());
            metadata.put("topic",     entity.getTopic());
            metadata.put("url",       entity.getUrl() != null ? entity.getUrl() : "");
            String docId = UUID.nameUUIDFromBytes(
                    entity.getArticleId().getBytes(StandardCharsets.UTF_8)).toString();
            Document doc = new Document(docId, content, metadata);
            documents.add(doc);
        }

        int totalEmbedded = 0;
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            try {
                vectorStore.add(batch);
                // Mark this batch of entities as embedded so they are skipped next run
                for (int j = i; j < end; j++) {
                    entities.get(j).setEmbedded(true);
                }
                repository.saveAll(entities.subList(i, end));
                totalEmbedded += batch.size();
                log.info("embedArticles() | Embedded batch {}-{} of {} articles",
                         i + 1, end, documents.size());
            } catch (Exception ex) {
                log.error("embedArticles() | Batch {}-{} failed: {}",
                          i + 1, end, ex.getMessage());
            }
            if (end < documents.size()) {
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("embedArticles() | interrupted during batch delay");
                    break;
                }
            }
        }
        log.info("embedArticles() | Completed — {} of {} new articles embedded",
                 totalEmbedded, documents.size());
    }

    /**
     * Builds the plain-text content string used as the document body for
     * embedding.  Combines title and body text so the embedding captures
     * the full article context.
     *
     * @param entity the article entity to build content from
     * @return non-blank content string
     */
    private String buildContent(NewsArticleEntity entity) {
        log.debug("buildContent() | articleId={}", entity.getArticleId());
        StringBuilder sb = new StringBuilder();
        if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
            sb.append(entity.getTitle());
        }
        if (entity.getBodyText() != null && !entity.getBodyText().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(entity.getBodyText());
        }
        String result = sb.length() > 0 ? sb.toString() : entity.getArticleId();
        log.debug("buildContent() | return={} chars", result.length());
        return result;
    }
}
