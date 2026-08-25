package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DocumentIngestionResult;

import java.nio.file.Path;

/**
 * Inbound port — drive document ingestion from a local directory or single file into the vector store.
 *
 * <p>The implementation scans the given directory for supported file types
 * (PDF, DOCX, TXT, MD), extracts text, splits it into chunks, and
 * embeds each chunk via the configured embedding model before persisting to
 * the pgvector database.
 *
 * <p>Ingestion is fault-tolerant: individual file failures are collected and
 * reported in the result rather than aborting the entire run.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-27
 * @updated 2026-08-25
 */
public interface IngestDocumentsUseCase {

    /**
     * Ingest all supported documents found in {@code directory} using fixed-size chunking.
     *
     * @param directory   Absolute path to the directory to scan; must exist and be readable.
     * @param sourceLabel Human-readable label applied to every chunk from this run.
     * @param chunkSize   Maximum character length of each text chunk before embedding.
     * @return A {@link DocumentIngestionResult} summarising files processed, chunks
     *         embedded, and any per-file failures.
     * @throws IllegalArgumentException if {@code directory} does not exist or is not
     *         a readable directory.
     */
    DocumentIngestionResult ingest(String directory, String sourceLabel, int chunkSize);

    /**
     * Ingest a single file using paragraph-aware chunking with overlap.
     *
     * <p>This overload is used by the Document Library upload endpoint.
     * Chunks are built from paragraph boundaries (~800 chars) with a 150-char
     * overlap window so context is not lost between adjacent chunks.
     *
     * @param file        Path to the file to parse; must be readable and of a supported type.
     * @param sourceLabel Human-readable attribution label (e.g., "Dr Smith — 2026 paper").
     * @return A {@link DocumentIngestionResult} summarising chunks embedded and any failure.
     */
    DocumentIngestionResult ingestFile(Path file, String sourceLabel);
}
