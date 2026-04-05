package com.wgblackmon.aihealthcare.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for the POST /api/v1/ingest endpoint.
 *
 * <p>Carries the parameters needed to kick off one article ingestion run:
 * a unique run identifier chosen by the caller, the newsletter week being
 * prepared, the list of search topics, and the per-topic article cap.
 *
 * @param runId               Caller-supplied identifier for this ingestion run
 *                            (e.g. {@code "run-2026-04-04"}).  Must be unique
 *                            across runs; used as the key when retrieving articles
 *                            for newsletter generation.
 * @param weekOf              The Monday of the newsletter week (ISO-8601 date).
 * @param topics              One or more search topics/keywords to drive article
 *                            discovery (e.g. {@code ["AI diagnostics", "ML drug discovery"]}).
 * @param maxArticlesPerTopic Maximum number of articles to fetch per topic; must be &gt;= 1.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
public record IngestRequest(
        String runId,
        LocalDate weekOf,
        List<String> topics,
        int maxArticlesPerTopic
) {}
