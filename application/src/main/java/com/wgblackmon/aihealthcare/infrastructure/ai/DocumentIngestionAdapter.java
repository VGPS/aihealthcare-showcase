package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI adapter that implements {@link DocumentVectorPort} by writing
 * {@link DocumentChunk}s into the configured {@link VectorStore} (pgvector).
 *
 * <p>Each {@link DocumentChunk} is converted to a Spring AI {@link Document}:
 * <ul>
 *   <li>{@code id} — the chunk's UUID, enabling idempotent re-ingestion</li>
 *   <li>{@code content} — the chunk text that gets embedded by OpenAI</li>
 *   <li>{@code metadata} — {@code sourceFile}, {@code chunkIndex}, and
 *       {@code sourceLabel} for later retrieval filtering</li>
 * </ul>
 *
 * <p>Not a {@code @Component} — instantiated directly by
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig#ingestDocumentsUseCase}
 * only when a {@link VectorStore} bean is actually resolvable. A prior version used
 * {@code @Component @ConditionalOnBean(VectorStore.class)}, which is a documented
 * Spring Boot anti-pattern: conditions on component-scanned classes are evaluated
 * before deferred auto-configuration imports (like {@code PgVectorStoreAutoConfiguration})
 * register their bean definitions, so the condition saw no {@code VectorStore} and
 * silently disabled document ingestion even when pgvector was fully configured.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-27
 * @updated 2026-11-23
 */
@Slf4j
public class DocumentIngestionAdapter implements DocumentVectorPort {

    private static final int  BATCH_SIZE    = 10;
    private static final long BATCH_DELAY_MS = 5_000;

    private final VectorStore vectorStore;
    private final long        batchDelayMs;

    public DocumentIngestionAdapter(VectorStore vectorStore) {
        this(vectorStore, BATCH_DELAY_MS);
    }

    /** Package-private constructor for tests — allows injecting a zero delay. */
    DocumentIngestionAdapter(VectorStore vectorStore, long batchDelayMs) {
        log.debug("DocumentIngestionAdapter() | vectorStore={}, batchDelayMs={}",
                  vectorStore.getClass().getSimpleName(), batchDelayMs);
        this.vectorStore  = vectorStore;
        this.batchDelayMs = batchDelayMs;
    }

    @Override
    public void store(List<DocumentChunk> chunks) {
        log.debug("store() | chunkCount={}", chunks.size());

        List<Document> documents = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("sourceFile",  chunk.sourceFile());
            metadata.put("chunkIndex",  chunk.chunkIndex());
            metadata.put("sourceLabel", chunk.sourceLabel());
            documents.add(new Document(chunk.chunkId(), chunk.content(), metadata));
        }

        int stored = 0;
        for (int i = 0; i < documents.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            try {
                vectorStore.add(batch);
                stored += batch.size();
                log.info("store() | Embedded batch {}-{} of {} chunks", i + 1, end, documents.size());
            } catch (Exception ex) {
                log.error("store() | Batch {}-{} failed: {}", i + 1, end, ex.getMessage());
            }
            if (end < documents.size()) {
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("store() | interrupted during batch delay");
                    break;
                }
            }
        }

        log.info("store() | Embedded and stored {} of {} document chunks", stored, documents.size());
        log.debug("store() | return=void");
    }
}
