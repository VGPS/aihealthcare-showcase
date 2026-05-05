package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response DTO for {@code POST /api/v1/research}.
 *
 * <p>Carries the full structured answer produced by the research pipeline, including
 * thematic sections (each with inline citations) and a flat deduplicated citation list
 * suitable for rendering a reference section.
 *
 * @param answerId      UUID assigned to this answer.
 * @param query         The original research question.
 * @param sections      Ordered thematic sections; at least one.
 * @param allCitations  Deduplicated master citation list across all sections.
 * @param generatedAt   ISO-8601 timestamp when the answer was generated.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchAnswerDto(
        String answerId,
        String query,
        List<ResearchSectionDto> sections,
        List<SourceCitationDto> allCitations,
        String generatedAt
) {}
