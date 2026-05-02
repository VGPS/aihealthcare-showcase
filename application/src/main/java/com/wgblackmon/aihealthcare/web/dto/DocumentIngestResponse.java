package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response body for the POST /api/v1/documents/ingest endpoint.
 *
 * @param filesProcessed Number of files successfully parsed.
 * @param chunksEmbedded Total text chunks embedded into the vector store.
 * @param failures       Per-file error messages for files that could not be parsed.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public record DocumentIngestResponse(
        int          filesProcessed,
        int          chunksEmbedded,
        List<String> failures
) {}
