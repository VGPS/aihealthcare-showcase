package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable result record returned after a document ingestion run.
 *
 * <p>Summarises how many files were successfully parsed and how many text chunks
 * were embedded into the vector store.  Files that could not be parsed are
 * recorded in {@code failures} with a descriptive message — the run continues
 * past individual file errors rather than aborting.
 *
 * @param filesProcessed Number of files successfully parsed (excluding failures).
 * @param chunksEmbedded Total number of text chunks embedded into the vector store.
 * @param failures       List of error messages for files that could not be processed;
 *                       empty when all files succeeded.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public record DocumentIngestionResult(
        int          filesProcessed,
        int          chunksEmbedded,
        List<String> failures
) {}
