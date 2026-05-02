package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DocumentChunk;

import java.util.List;

/**
 * Outbound port — embed and persist a batch of {@link DocumentChunk}s in the vector store.
 *
 * <p>Implementations live in {@code infrastructure/ai} and delegate to Spring AI's
 * {@link org.springframework.ai.vectorstore.VectorStore}.  Each chunk is converted to
 * a Spring AI {@code Document} (content + metadata) before being embedded and stored.
 *
 * <p>Storing is idempotent with respect to chunk ID: re-ingesting the same file with
 * the same chunk IDs will overwrite existing embeddings rather than create duplicates.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public interface DocumentVectorPort {

    /**
     * Embed and store all chunks in the vector database.
     *
     * @param chunks Non-null, non-empty list of chunks to embed; each chunk must have
     *               non-blank content.
     */
    void store(List<DocumentChunk> chunks);
}
