package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.SectionType;

/**
 * API representation of a single newsletter section within a {@link DraftResponse}.
 *
 * <p>Maps directly from {@link com.wgblackmon.aihealthcare.domain.model.NewsletterSection}.
 * Source article IDs are intentionally omitted here — they are captured in the full
 * domain object but are not needed by API consumers for rendering the newsletter.
 *
 * @param sectionId   Unique identifier for this section.
 * @param sectionType Editorial classification (e.g. {@code WHAT_SHIPPED}, {@code POLICY_WATCH}).
 * @param topic       The search topic this section covers.
 * @param headline    AI-generated headline.
 * @param summary     AI-generated summary paragraph.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
public record SectionResponse(
        String sectionId,
        SectionType sectionType,
        String topic,
        String headline,
        String summary
) {}
