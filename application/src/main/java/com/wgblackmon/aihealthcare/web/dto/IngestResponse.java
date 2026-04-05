package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response body for the POST /api/v1/ingest endpoint.
 *
 * <p>Confirms that an ingestion run completed and reports how many articles
 * were collected across all topics.  The caller uses {@code runId} in a
 * subsequent POST /api/v1/drafts request to generate a newsletter from these
 * articles.
 *
 * @param runId        The run identifier echoed back from the request.
 * @param articleCount Total number of articles successfully ingested across all topics.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
public record IngestResponse(
        String runId,
        int articleCount
) {}
