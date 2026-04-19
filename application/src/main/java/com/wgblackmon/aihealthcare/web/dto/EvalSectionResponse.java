package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Response DTO for a newsletter section produced during prompt evaluation.
 *
 * <p>Unlike {@link SectionResponse} (used for newsletter drafts), this DTO includes
 * the source {@code articleIds} and uses a String for {@code sectionType} — both
 * are valuable for evaluation review.
 *
 * @param sectionId   unique identifier for this section
 * @param sectionType editorial classification as a string
 * @param topic       the search topic
 * @param headline    AI-generated headline
 * @param summary     AI-generated summary
 * @param articleIds  IDs of source articles
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record EvalSectionResponse(
        String sectionId,
        String sectionType,
        String topic,
        String headline,
        String summary,
        List<String> articleIds
) {}
