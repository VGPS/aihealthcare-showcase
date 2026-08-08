package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing one thematic section within a {@link ResearchAnswer}.
 *
 * <p>In the STAGED_RESEARCH path, sections are parsed from the AI synthesis response by
 * {@link com.wgblackmon.aihealthcare.domain.service.ResearchSynthesisService}, which
 * splits the response on {@code ##} headings.  In the LEGACY_GOOGLE path, a single
 * section is created directly from the retrieved source titles.
 *
 * <p>The {@code citations} list contains only the sources referenced in this specific
 * section.  The deduplicated master list is on {@link ResearchAnswer#allCitations()}.
 *
 * @param heading    Section heading text; never blank.
 * @param body       Paragraph body of the section, with inline citation markers such as
 *                   {@code [1]}, {@code [2]}.
 * @param citations  Citations referenced within this section; may be empty.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public record ResearchSection(
        String heading,
        String body,
        List<SourceCitation> citations
) {}
