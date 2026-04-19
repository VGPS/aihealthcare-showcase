package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Request DTO for comparing multiple prompt variants side by side.
 *
 * @param variantIds IDs of the variants to compare (minimum 2)
 * @param articleIds IDs of articles to use as shared input
 * @param topic      the topic for summarization
 * @param tone       the newsletter tone (PROFESSIONAL, ACCESSIBLE, TECHNICAL)
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
public record CompareRequest(
        List<String> variantIds,
        List<String> articleIds,
        String topic,
        String tone
) {}
