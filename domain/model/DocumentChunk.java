package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing a single text chunk extracted from a document
 * and prepared for embedding into the vector store.
 *
 * <p>Each chunk carries enough metadata to trace it back to its source file and
 * position within that file.  The {@code content} field is the text that will be
 * embedded; all other fields are stored as vector metadata for later retrieval.
 *
 * @param chunkId     Globally unique identifier for this chunk (UUID string).
 * @param sourceFile  Filename (not full path) of the document this chunk came from.
 * @param chunkIndex  Zero-based index of this chunk within its source file.
 * @param content     The text content to embed; never blank.
 * @param sourceLabel Human-readable label for the ingestion batch
 *                    (e.g., {@code "Clinical Guidelines 2026"}).
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public record DocumentChunk(
        String chunkId,
        String sourceFile,
        int    chunkIndex,
        String content,
        String sourceLabel
) {}
