package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentVectorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

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
 * <p>This adapter is only registered when a {@link VectorStore} bean is present.
 * In the {@code h2} test profile the pgvector auto-configuration is excluded, so
 * this bean is absent and the controller is unavailable — consistent with the
 * behaviour of {@link com.wgblackmon.aihealthcare.infrastructure.scheduler.EmbeddingScheduler} and {@link VectorStoreArticleSearchAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@Slf4j
@Component
@ConditionalOnBean(VectorStore.class)
public class DocumentIngestionAdapter implements DocumentVectorPort {

    private final VectorStore vectorStore;

    public DocumentIngestionAdapter(VectorStore vectorStore) {
        log.debug("DocumentIngestionAdapter() | vectorStore={}", vectorStore.getClass().getSimpleName());
        this.vectorStore = vectorStore;
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

        vectorStore.add(documents);
        log.info("store() | Embedded and stored {} document chunks", documents.size());
        log.debug("store() | return=void");
    }
}
