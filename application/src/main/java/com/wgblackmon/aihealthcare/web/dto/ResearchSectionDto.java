package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response DTO representing a single thematic section within a {@link ResearchAnswerDto}.
 *
 * @param heading    Section heading text.
 * @param body       Paragraph body with inline citation markers such as {@code [1]}.
 * @param citations  Citations referenced within this section.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchSectionDto(
        String heading,
        String body,
        List<SourceCitationDto> citations
) {}
