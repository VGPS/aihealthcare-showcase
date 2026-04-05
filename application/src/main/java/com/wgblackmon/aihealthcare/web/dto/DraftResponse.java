package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * API representation of a fully generated newsletter draft.
 *
 * <p>Returned by both POST /api/v1/drafts (on creation) and
 * GET /api/v1/drafts/{draftId} (on retrieval).  Maps from
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsletterDraft}, with the
 * full source-article list collapsed to a count — callers that need full
 * attribution data can be served by a future endpoint.
 *
 * @param draftId            Unique identifier for this draft.
 * @param runId              The ingestion run this draft was generated from.
 * @param title              Newsletter issue title.
 * @param weekOf             The Monday of the newsletter week.
 * @param introduction       AI-generated introductory paragraph.
 * @param sections           Ordered list of AI-generated sections.
 * @param sourceArticleCount Total number of source articles used across all sections.
 * @param generatedAt        Timestamp of when this draft was generated (UTC).
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
public record DraftResponse(
        String draftId,
        String runId,
        String title,
        LocalDate weekOf,
        String introduction,
        List<SectionResponse> sections,
        int sourceArticleCount,
        Instant generatedAt
) {}
