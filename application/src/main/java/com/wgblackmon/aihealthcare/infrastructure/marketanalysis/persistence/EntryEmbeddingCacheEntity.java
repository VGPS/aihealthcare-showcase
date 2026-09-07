package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity caching computed embeddings for market digest entry dedup.
 *
 * <p>Keyed by a SHA-256 hash of the headline+summary text so identical content
 * always resolves to the same cached embedding. The embedding is stored as a
 * JSON-serialized float array in a TEXT column (H2-compatible, no pgvector
 * extension required).
 *
 * <p>This cache avoids re-calling the embedding API for entries from prior
 * digests during the 7-day rolling dedup window.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-07
 */
@Entity
@Table(name = "entry_embedding_cache")
public class EntryEmbeddingCacheEntity {

    @Id
    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(name = "embedding_json", nullable = false, columnDefinition = "TEXT")
    private String embeddingJson;

    protected EntryEmbeddingCacheEntity() {}

    public EntryEmbeddingCacheEntity(String textHash, String embeddingJson) {
        this.textHash      = textHash;
        this.embeddingJson = embeddingJson;
    }

    public String getTextHash()      { return textHash; }
    public String getEmbeddingJson() { return embeddingJson; }
}
