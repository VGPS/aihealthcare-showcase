package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Immutable domain record tracking a single document uploaded through the
 * Document Library and ingested into the vector store and wiki.
 *
 * <p>Created when a file is accepted by the upload endpoint and updated as it
 * progresses through the ingestion pipeline.  The {@code wikiPageSlug} field
 * is {@code null} until wiki compilation succeeds.
 *
 * @param docId          UUID identifying this document record.
 * @param filename       Original filename as uploaded.
 * @param sourceLabel    Human-readable attribution label (e.g. "Dr Smith — 2026 paper").
 * @param uploadedAt     Timestamp when the file was received.
 * @param chunkCount     Number of text chunks embedded into the vector store; 0 until indexed.
 * @param wikiPageSlug   Slug of the wiki page compiled from this document; null until compiled.
 * @param status         Current lifecycle state.
 * @param errorMessage   Human-readable failure description; null when status is not FAILED.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
public record DocumentRecord(
        String docId,
        String filename,
        String sourceLabel,
        Instant uploadedAt,
        int chunkCount,
        String wikiPageSlug,
        DocumentStatus status,
        String errorMessage
) {
    public DocumentRecord {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("docId must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (sourceLabel == null || sourceLabel.isBlank()) {
            throw new IllegalArgumentException("sourceLabel must not be blank");
        }
        if (uploadedAt == null) {
            throw new IllegalArgumentException("uploadedAt must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
