package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;

/**
 * Request body for the POST /api/v1/drafts endpoint.
 *
 * <p>Carries the parameters needed to generate a newsletter draft from a
 * previously completed ingestion run.  The {@code runId} must match a run
 * that was successfully completed via POST /api/v1/ingest.
 *
 * <p>When {@code ragEnabled} is {@code true}, the service queries the vector
 * store for semantically similar past articles (up to {@code ragContextCount}
 * per topic) and includes them as additional context in the summarization prompt,
 * enabling retrieval-augmented generation (RAG).
 *
 * @param runId               Identifier of the ingestion run to summarize.
 * @param draftId             Caller-supplied unique identifier for the resulting draft
 *                            (e.g. {@code "draft-2026-04-04-001"}).
 * @param title               Title of this newsletter issue
 *                            (e.g. {@code "AI in Healthcare — Week of April 4, 2026"}).
 * @param tone                Writing tone for all AI-generated content.
 * @param maxSectionsPerTopic Maximum number of source articles to feed the AI per topic;
 *                            controls summarization depth. Must be &gt;= 1.
 * @param ragEnabled          When {@code true}, augments each section with past articles
 *                            retrieved from the vector store. {@code null} defaults to
 *                            {@code false} in the controller.
 * @param ragContextCount     Number of past articles to retrieve per topic when RAG is
 *                            enabled. {@code null} defaults to 3 in the controller.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-27
 */
public record GenerateRequest(
        String runId,
        String draftId,
        String title,
        NewsletterTone tone,
        int maxSectionsPerTopic,
        Boolean ragEnabled,
        Integer ragContextCount
) {}
