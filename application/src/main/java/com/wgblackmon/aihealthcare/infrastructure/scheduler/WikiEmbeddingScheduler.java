package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled job that embeds wiki pages into the shared vector store so that
 * {@code WikiQueryAdapter} can run a semantic-similarity leg alongside its
 * keyword search (Slice W7).
 *
 * <p>Runs on the cron schedule defined by {@code aihealthcare.wiki.embedding-cron}
 * in {@code application.yml}.  On each run it fetches only wiki pages whose
 * current revision has not yet been embedded ({@code revision > lastEmbeddedRevision}),
 * converts them to Spring AI {@link Document} objects tagged with
 * {@code type=wiki_page} metadata, and calls {@link VectorStore#add}.  Embeddings
 * share the same {@code PgVectorStore} table used for article embeddings — the
 * metadata tag is what lets {@code WikiQueryAdapter} filter wiki pages back out.
 *
 * <p>Because each {@link Document} is created with an ID derived from the page
 * slug, re-running this scheduler is safe and idempotent — a page already
 * embedded at its current revision is simply skipped by the repository query;
 * a re-compiled page (new revision) overwrites its prior embedding in place.
 *
 * <p>If the embedding API call fails (e.g., missing or invalid API key), the
 * exception is caught and logged so the scheduler thread survives and
 * subsequent runs can still succeed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-26
 * @updated 2026-10-26
 */
@Slf4j
@Component
public class WikiEmbeddingScheduler {

    private static final int BATCH_SIZE = 10;
    private static final long BATCH_DELAY_MS = 5000;

    private final WikiPageRepository repository;
    private final VectorStore vectorStore;
    private final PipelineHealthService healthService;

    public WikiEmbeddingScheduler(WikiPageRepository repository,
                                   ObjectProvider<VectorStore> vectorStoreProvider,
                                   PipelineHealthService healthService) {
        this.repository = repository;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.healthService = healthService;
        log.debug("WikiEmbeddingScheduler() | repository={}, vectorStore={}",
                  repository.getClass().getSimpleName(),
                  vectorStore != null ? vectorStore.getClass().getSimpleName() : "NULL (not configured)");
    }

    /**
     * Embeds all wiki pages needing (re-)embedding into the vector store.
     *
     * <p>Invoked on the schedule configured by
     * {@code aihealthcare.wiki.embedding-cron}.  Failures are logged and
     * swallowed so the scheduler thread remains alive for future runs.
     */
    @Scheduled(cron = "${aihealthcare.wiki.embedding-cron}", zone = "UTC")
    public void embedWikiPages() {
        log.debug("embedWikiPages() | starting wiki embedding run");
        Instant start = Instant.now();
        try {
            int embedded = embedWikiPagesInternal();
            healthService.recordRun("wiki-embedding", PipelineHealthService.PipelineRunRecord.success(
                    "wiki-embedding", embedded, start, Instant.now()));
        } catch (Exception e) {
            log.error("embedWikiPages() | scheduler exception", e);
            healthService.recordRun("wiki-embedding", PipelineHealthService.PipelineRunRecord.failure(
                    "wiki-embedding", e.getMessage(), start, Instant.now()));
        }
        log.debug("embedWikiPages() | return=void");
    }

    private int embedWikiPagesInternal() {
        if (vectorStore == null) {
            log.warn("embedWikiPages() | VectorStore not available — skipping");
            return 0;
        }

        List<WikiPageEntity> entities = repository.findNeedingEmbedding();
        log.info("embedWikiPages() | Found {} wiki pages needing (re-)embedding", entities.size());

        if (entities.isEmpty()) {
            log.debug("embedWikiPages() | return=0 (nothing to embed)");
            return 0;
        }

        List<Document> documents = new ArrayList<>();
        for (WikiPageEntity entity : entities) {
            String content = buildContent(entity);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "wiki_page");
            metadata.put("slug", entity.getSlug());
            metadata.put("title", entity.getTitle() != null ? entity.getTitle() : "");
            String docId = UUID.nameUUIDFromBytes(
                    entity.getSlug().getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(new Document(docId, content, metadata));
        }

        int totalEmbedded = 0;
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            try {
                vectorStore.add(batch);
                for (int j = i; j < end; j++) {
                    WikiPageEntity entity = entities.get(j);
                    entity.setLastEmbeddedRevision(entity.getRevision());
                }
                repository.saveAll(entities.subList(i, end));
                totalEmbedded += batch.size();
                log.info("embedWikiPages() | Embedded batch {}-{} of {} pages",
                         i + 1, end, documents.size());
            } catch (Exception ex) {
                log.error("embedWikiPages() | Batch {}-{} failed: {}",
                          i + 1, end, ex.getMessage());
            }
            if (end < documents.size()) {
                try {
                    Thread.sleep(BATCH_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("embedWikiPages() | interrupted during batch delay");
                    break;
                }
            }
        }
        log.info("embedWikiPages() | Completed — {} of {} pages embedded",
                 totalEmbedded, documents.size());
        return totalEmbedded;
    }

    /**
     * Builds the plain-text content string used as the document body for
     * embedding. Combines title and markdown content so the embedding
     * captures the full page context.
     *
     * @param entity the wiki page entity to build content from
     * @return non-blank content string
     */
    private String buildContent(WikiPageEntity entity) {
        log.debug("buildContent() | slug={}", entity.getSlug());
        StringBuilder sb = new StringBuilder();
        if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
            sb.append(entity.getTitle());
        }
        if (entity.getContentMarkdown() != null && !entity.getContentMarkdown().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(entity.getContentMarkdown());
        }
        String result = sb.length() > 0 ? sb.toString() : entity.getSlug();
        log.debug("buildContent() | return={} chars", result.length());
        return result;
    }
}
