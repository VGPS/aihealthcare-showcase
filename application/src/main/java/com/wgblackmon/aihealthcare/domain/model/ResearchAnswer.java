package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing the final output of the research pipeline.
 *
 * <p>A {@code ResearchAnswer} is the top-level artifact returned by
 * {@link com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase}.
 * It contains one or more {@link ResearchSection} records (each with inline citations)
 * and a flat, deduplicated {@code allCitations} list that callers can use to render a
 * reference section.
 *
 * <p>In the LEGACY_GOOGLE mode, the answer contains a single section whose body lists
 * article titles and URLs without AI synthesis.  In the STAGED_RESEARCH mode, the
 * answer contains 2-3 thematically organised sections produced by the AI synthesis step.
 *
 * @param answerId      UUID assigned at creation time.
 * @param query         The original research question.
 * @param sections      Ordered thematic sections; never empty.
 * @param allCitations  Deduplicated, sequentially numbered citations across all sections.
 * @param generatedAt   Timestamp when this answer was assembled.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchAnswer(
        String answerId,
        String query,
        List<ResearchSection> sections,
        List<SourceCitation> allCitations,
        Instant generatedAt
) {}
